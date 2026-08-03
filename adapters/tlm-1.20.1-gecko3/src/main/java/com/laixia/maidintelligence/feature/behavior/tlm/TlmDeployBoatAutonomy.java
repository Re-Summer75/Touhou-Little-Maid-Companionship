package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.MaidAbilityApi;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationSource;
import com.laixia.maidintelligence.feature.behavior.domain.ability.CompanionAbilityIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class TlmDeployBoatAutonomy {
    private static final int SAMPLE_INTERVAL_TICKS = 5;
    private static final int ENTER_SAMPLES = 3;
    private static final int EXIT_SAMPLES = 4;
    private static final int REQUEST_INTERVAL_TICKS = 40;
    private static final int COORDINATION_EPISODE_TICKS = 100;
    private static final int COORDINATION_PRIORITY = 500;
    private static final double AUTONOMOUS_UTILITY = 120.0D;
    private static final double MIN_OWNER_DISTANCE_SQR = 36.0D;
    private static final double MAX_OWNER_DISTANCE_SQR = 576.0D;

    private final MaidAbilityApi<EntityMaid> abilities;
    private final Map<EntityMaid, State> states = new WeakHashMap<>();

    public TlmDeployBoatAutonomy(
            MaidAbilityApi<EntityMaid> abilities
    ) {
        this.abilities = Objects.requireNonNull(abilities, "abilities");
    }

    public void tick(EntityMaid maid, long gameTime) {
        State state = states.computeIfAbsent(
                maid,
                ignored -> new State()
        );
        if (gameTime < state.nextSampleTick) {
            return;
        }
        state.nextSampleTick = gameTime + SAMPLE_INTERVAL_TICKS;
        boolean demand = crossingDemand(maid);
        if (demand) {
            state.enterSamples = Math.min(
                    ENTER_SAMPLES,
                    state.enterSamples + 1
            );
            state.exitSamples = 0;
            if (state.enterSamples >= ENTER_SAMPLES) {
                state.active = true;
            }
        } else {
            state.exitSamples = Math.min(
                    EXIT_SAMPLES,
                    state.exitSamples + 1
            );
            state.enterSamples = 0;
            if (state.exitSamples >= EXIT_SAMPLES) {
                state.active = false;
            }
        }
        if (state.active && gameTime >= state.nextRequestTick) {
            TlmOwnerCoordinationGroups.Decision decision =
                    TlmOwnerCoordinationGroups.decide(
                            maid,
                            CompanionAbilityIds.DEPLOY_BOAT,
                            COORDINATION_PRIORITY,
                            1,
                            COORDINATION_EPISODE_TICKS,
                            gameTime,
                            candidate -> canRespond(
                                    candidate,
                                    gameTime
                            ),
                            candidate -> AUTONOMOUS_UTILITY
                    );
            if (decision.assigned()) {
                abilities.request(
                        maid,
                        CompanionAbilityIds.DEPLOY_BOAT,
                        AbilityActivationSource.AUTONOMOUS,
                        gameTime,
                        decision.requestId()
                );
            }
            state.nextRequestTick =
                    gameTime + REQUEST_INTERVAL_TICKS;
        }
    }

    private boolean canRespond(EntityMaid maid, long gameTime) {
        var runtime = abilities.inspect(maid, gameTime);
        return crossingDemand(maid)
                && hasBoatResource(maid)
                && !runtime.executing().contains(
                CompanionAbilityIds.DEPLOY_BOAT
        )
                && runtime.cooldownUntil().getOrDefault(
                CompanionAbilityIds.DEPLOY_BOAT,
                Long.MIN_VALUE
        ) <= gameTime;
    }

    private static boolean hasBoatResource(EntityMaid maid) {
        AABB bounds = maid.getBoundingBox().inflate(8.0D);
        if (!maid.level().getEntitiesOfClass(
                Boat.class,
                bounds,
                boat -> boat.isAlive() && boat.getPassengers().isEmpty()
        ).isEmpty()) {
            return true;
        }
        var inventory = maid.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (isBoat(inventory.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBoat(ItemStack stack) {
        String path = BuiltInRegistries.ITEM
                .getKey(stack.getItem())
                .getPath()
                .toLowerCase(Locale.ROOT);
        return path.endsWith("_boat") || path.endsWith("_raft");
    }

    public void forget(EntityMaid maid) {
        states.remove(maid);
    }

    private boolean crossingDemand(EntityMaid maid) {
        if (!abilities.granted(maid, CompanionAbilityIds.DEPLOY_BOAT)
                || maid.isPassenger()
                || maid.isHomeModeEnable()
                || maid.isOrderedToSit()
                || maid.isMaidInSittingPose()
                || maid.isSleeping()
                || maid.isLeashed()
                || maid.getBrain().hasMemoryValue(
                MemoryModuleType.ATTACK_TARGET
        )
                || maid.getBrain().isActive(Activity.PANIC)) {
            return false;
        }
        LivingEntity owner = maid.getOwner();
        if (owner == null
                || !maid.isTame()
                || !owner.isAlive()
                || owner.isSpectator()
                || owner.level() != maid.level()) {
            return false;
        }
        double distance = maid.distanceToSqr(owner);
        if (distance < MIN_OWNER_DISTANCE_SQR
                || distance > MAX_OWNER_DISTANCE_SQR) {
            return false;
        }
        Vec3 start = maid.position();
        Vec3 delta = owner.position().subtract(start);
        for (int step = 0; step <= 8; step++) {
            double fraction = step / 8.0D;
            Vec3 sample = start.add(delta.scale(fraction));
            if (sample.distanceToSqr(start) > 25.0D) {
                break;
            }
            BlockPos position = BlockPos.containing(sample);
            if (maid.level().hasChunkAt(position)
                    && (maid.level().getFluidState(position)
                    .is(FluidTags.WATER)
                    || maid.level().getFluidState(position.below())
                    .is(FluidTags.WATER))) {
                return true;
            }
        }
        return false;
    }

    private static final class State {
        private int enterSamples;
        private int exitSamples;
        private boolean active;
        private long nextSampleTick;
        private long nextRequestTick;
    }
}
