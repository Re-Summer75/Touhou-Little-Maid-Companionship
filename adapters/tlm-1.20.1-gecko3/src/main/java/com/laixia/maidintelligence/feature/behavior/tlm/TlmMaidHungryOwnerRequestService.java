package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.api.MaidHungryOwnerRequestApi;
import com.laixia.maidintelligence.feature.behavior.domain.HungryOwnerRequestPolicy;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * TLM movement adapter for probabilistic owner food requests.
 */
@SuppressWarnings("null")
public final class TlmMaidHungryOwnerRequestService
        implements MaidHungryOwnerRequestApi<EntityMaid> {
    private static final float APPROACH_SPEED = 0.55F;
    private static final int REQUEST_APPROACH_TIMEOUT_TICKS = 200;

    private final MaidStatusApi<EntityMaid> status;
    private final Supplier<BehaviorTuning> tuning;
    private final ToDoubleFunction<EntityMaid> randomSample;
    private final Consumer<EntityMaid> requestAction;
    private final Map<EntityMaid, PendingRequest> pendingRequests =
            new WeakHashMap<>();

    public TlmMaidHungryOwnerRequestService(
            MaidStatusApi<EntityMaid> status,
            Supplier<BehaviorTuning> tuning,
            ToDoubleFunction<EntityMaid> randomSample,
            Consumer<EntityMaid> requestAction
    ) {
        this.status = Objects.requireNonNull(status, "status");
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.randomSample = Objects.requireNonNull(
                randomSample,
                "randomSample"
        );
        this.requestAction = Objects.requireNonNull(
                requestAction,
                "requestAction"
        );
    }

    @Override
    public boolean shouldEvaluate(EntityMaid maid, long gameTime) {
        BehaviorTuning current = currentTuning();
        BehaviorTuning.HungryRequest request = current.hungryRequest();
        if (!current.enabled() || !request.enabled()) {
            pendingRequests.remove(maid);
            return false;
        }
        if (eligibleTier(maid, request)
                == HungryOwnerRequestPolicy.RequestTier.NONE) {
            pendingRequests.remove(maid);
            return false;
        }

        PendingRequest pending = pendingRequests.get(maid);
        if (pending != null) {
            if (pending.activeAt(gameTime)) {
                return true;
            }
            pendingRequests.remove(maid);
        }
        return HungryOwnerRequestPolicy.INSTANCE.shouldEvaluate(
                gameTime,
                maid.getId(),
                request.checkIntervalTicks()
        );
    }

    @Override
    public boolean tryRequest(EntityMaid maid) {
        BehaviorTuning current = currentTuning();
        BehaviorTuning.HungryRequest request = current.hungryRequest();
        HungryOwnerRequestPolicy.RequestTier tier =
                eligibleTier(maid, request);
        if (!current.enabled()
                || !request.enabled()
                || tier == HungryOwnerRequestPolicy.RequestTier.NONE) {
            pendingRequests.remove(maid);
            return false;
        }

        LivingEntity owner = validOwner(maid);
        if (owner == null) {
            pendingRequests.remove(maid);
            return false;
        }
        long gameTime = maid.level().getGameTime();
        PendingRequest pending = pendingRequests.get(maid);
        if (pending != null) {
            if (!pending.activeAt(gameTime)) {
                pendingRequests.remove(maid);
                return false;
            }
            return finishRequestOnArrival(maid, owner);
        }
        if (!HungryOwnerRequestPolicy.INSTANCE.chancePassed(
                randomSample.applyAsDouble(maid),
                request.chance(tier)
        )) {
            return false;
        }
        if (isPassiveTlmSeat(maid.getVehicle())) {
            maid.stopRiding();
        }
        if (!maid.canBrainMoving()) {
            return false;
        }

        int closeEnough =
                HungryOwnerRequestPolicy.DEFAULT_CLOSE_ENOUGH_DISTANCE;
        if (maid.distanceToSqr(owner) <= closeEnough * closeEnough) {
            showRequestAction(maid, owner);
            return true;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        );
        BehaviorUtils.setWalkAndLookTargetMemories(
                maid,
                owner,
                APPROACH_SPEED,
                closeEnough
        );
        pendingRequests.put(
                maid,
                new PendingRequest(
                        gameTime,
                        deadline(gameTime, REQUEST_APPROACH_TIMEOUT_TICKS)
                )
        );
        return true;
    }

    private boolean finishRequestOnArrival(
            EntityMaid maid,
            LivingEntity owner
    ) {
        int closeEnough =
                HungryOwnerRequestPolicy.DEFAULT_CLOSE_ENOUGH_DISTANCE;
        if (maid.distanceToSqr(owner) > closeEnough * closeEnough) {
            return false;
        }
        pendingRequests.remove(maid);
        showRequestAction(maid, owner);
        return true;
    }

    private void showRequestAction(
            EntityMaid maid,
            LivingEntity owner
    ) {
        maid.getBrain().setMemory(
                MemoryModuleType.LOOK_TARGET,
                new EntityTracker(owner, true)
        );
        requestAction.accept(maid);
    }

    private HungryOwnerRequestPolicy.RequestTier eligibleTier(
            EntityMaid maid,
            BehaviorTuning.HungryRequest request
    ) {
        LivingEntity owner = validOwner(maid);
        boolean passiveSeat = isPassiveTlmSeat(maid.getVehicle());
        boolean canMove = !maid.isMaidInSittingPose()
                && !maid.isOrderedToSit()
                && !maid.isSleeping()
                && !maid.isLeashed()
                && (!maid.isPassenger() || passiveSeat);
        boolean combatActive = maid.getBrain().hasMemoryValue(
                MemoryModuleType.ATTACK_TARGET
        ) || maid.getBrain().isActive(Activity.PANIC);
        HungryOwnerRequestPolicy.RequestTier tier =
                HungryOwnerRequestPolicy.INSTANCE.selectTier(
                        status.getState(maid).hunger(),
                        maid.getFavorabilityManager().getLevel(),
                        request.standard().hungerThreshold(),
                        request.standard().minimumFavorabilityLevel(),
                        request.highTrust().hungerThreshold(),
                        request.highTrust().minimumFavorabilityLevel()
                );
        if (owner != null
                && HungryOwnerRequestPolicy.INSTANCE.eligible(
                        tier,
                        !maid.isHomeModeEnable(),
                        canMove,
                        combatActive
                )) {
            return tier;
        }
        return HungryOwnerRequestPolicy.RequestTier.NONE;
    }

    private static LivingEntity validOwner(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner == null
                || !owner.isAlive()
                || owner.isSpectator()
                || owner.level() != maid.level()
                || !maid.isTame()) {
            return null;
        }
        return owner;
    }

    private static boolean isPassiveTlmSeat(Entity vehicle) {
        return vehicle != null
                && (vehicle.getType() == EntityChair.TYPE
                || vehicle.getType() == EntitySit.TYPE);
    }

    private BehaviorTuning currentTuning() {
        BehaviorTuning current = tuning.get();
        return current == null ? BehaviorTuning.defaults() : current;
    }

    private static long deadline(long gameTime, int durationTicks) {
        return gameTime > Long.MAX_VALUE - durationTicks
                ? Long.MAX_VALUE
                : gameTime + durationTicks;
    }

    private record PendingRequest(long startedAtTick, long expiresAtTick) {
        private boolean activeAt(long gameTime) {
            return gameTime >= startedAtTick && gameTime < expiresAtTick;
        }
    }
}
