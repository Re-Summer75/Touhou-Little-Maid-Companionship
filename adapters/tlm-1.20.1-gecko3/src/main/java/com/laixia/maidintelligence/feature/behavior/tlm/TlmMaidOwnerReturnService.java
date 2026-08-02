package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentLease;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationAccess;
import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.api.MaidOwnerReturnApi;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerReturnPolicy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Detects completed work sequences and newly selected random strolls.
 */
@SuppressWarnings("null")
public final class TlmMaidOwnerReturnService
        implements MaidOwnerReturnApi<EntityMaid> {
    private static final String TLM_NAMESPACE = "touhou_little_maid";
    private static final float APPROACH_SPEED = 0.5F;

    private final Supplier<BehaviorTuning> tuning;
    private final ToDoubleFunction<EntityMaid> randomSample;
    private final Map<EntityMaid, RuntimeState> states = new WeakHashMap<>();

    public TlmMaidOwnerReturnService(
            Supplier<BehaviorTuning> tuning,
            ToDoubleFunction<EntityMaid> randomSample
    ) {
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.randomSample = Objects.requireNonNull(
                randomSample,
                "randomSample"
        );
    }

    @Override
    public boolean tick(EntityMaid maid, long gameTime) {
        RuntimeState state = states.computeIfAbsent(
                maid,
                ignored -> new RuntimeState()
        );
        if (state.lastTick != Long.MIN_VALUE && gameTime < state.lastTick) {
            state.reset();
        }
        state.lastTick = gameTime;

        WalkTarget walkTarget = currentWalkTarget(maid);
        boolean newWalkTarget = walkTarget != null
                && walkTarget != state.lastWalkTarget;
        state.lastWalkTarget = walkTarget;

        BehaviorTuning current = currentTuning();
        BehaviorTuning.OwnerReturn settings = current.ownerReturn();
        if (!current.enabled() || !isBuiltInTask(maid)) {
            state.resetWork();
            return false;
        }

        boolean hasWorkTarget = maid.getBrain().hasMemoryValue(
                InitEntities.TARGET_POS.get()
        );
        observeWorkTarget(state, hasWorkTarget, gameTime);

        if (state.postTaskPending) {
            boolean returned = tryPostTaskReturn(
                    maid,
                    state,
                    settings,
                    gameTime
            );
            if (returned || state.postTaskPending) {
                return returned;
            }
        }
        return tryWanderReturn(
                maid,
                state,
                settings,
                walkTarget,
                newWalkTarget,
                hasWorkTarget,
                gameTime
        );
    }

    private boolean tryPostTaskReturn(
            EntityMaid maid,
            RuntimeState state,
            BehaviorTuning.OwnerReturn settings,
            long gameTime
    ) {
        OwnerReturnPolicy policy = OwnerReturnPolicy.INSTANCE;
        if (!settings.postTaskEnabled()
                || policy.postTaskExpired(
                        state.workReleasedAtTick,
                        gameTime,
                        settings.postTaskTimeoutTicks()
                )) {
            state.clearPendingPostTask();
            return false;
        }
        if (!policy.postTaskReady(
                state.workReleasedAtTick,
                gameTime,
                settings.postTaskSettleTicks(),
                state.postTaskCooldownUntilTick
        )) {
            return false;
        }
        if (!tryReturnToOwner(
                maid,
                settings.closeEnoughDistance(),
                gameTime
        )) {
            return false;
        }

        state.clearPendingPostTask();
        state.postTaskCooldownUntilTick = deadline(
                gameTime,
                settings.postTaskCooldownTicks()
        );
        return true;
    }

    private boolean tryWanderReturn(
            EntityMaid maid,
            RuntimeState state,
            BehaviorTuning.OwnerReturn settings,
            WalkTarget walkTarget,
            boolean newWalkTarget,
            boolean hasWorkTarget,
            long gameTime
    ) {
        OwnerReturnPolicy policy = OwnerReturnPolicy.INSTANCE;
        if (!settings.wanderEnabled()
                || !newWalkTarget
                || hasWorkTarget
                || !(walkTarget.getTarget() instanceof BlockPosTracker)
                || !maid.getBrain().isActive(Activity.WORK)
                || !maid.getTask().enableLookAndRandomWalk(maid)
                || !policy.cooldownElapsed(
                        gameTime,
                        state.wanderCooldownUntilTick
                )
                || !policy.chancePassed(
                        randomSample.applyAsDouble(maid),
                        settings.wanderChance()
                )
                || !tryReturnToOwner(
                        maid,
                        settings.closeEnoughDistance(),
                        gameTime
                )) {
            return false;
        }

        state.wanderCooldownUntilTick = deadline(
                gameTime,
                settings.wanderCooldownTicks()
        );
        return true;
    }

    private static void observeWorkTarget(
            RuntimeState state,
            boolean hasWorkTarget,
            long gameTime
    ) {
        if (hasWorkTarget) {
            state.workTargetActive = true;
            state.clearPendingPostTask();
            return;
        }
        if (state.workTargetActive) {
            state.workTargetActive = false;
            state.postTaskPending = true;
            state.workReleasedAtTick = gameTime;
        }
    }

    private static boolean tryReturnToOwner(
            EntityMaid maid,
            int closeEnoughDistance,
            long gameTime
    ) {
        LivingEntity owner = validOwner(maid);
        if (owner == null) {
            return false;
        }
        if (targetsOwner(maid, owner)) {
            return true;
        }

        boolean canMove = maid.canBrainMoving()
                && !maid.isMaidInSittingPose()
                && !maid.isOrderedToSit()
                && !maid.isSleeping()
                && !maid.isLeashed()
                && !maid.isPassenger();
        boolean combatActive = maid.getBrain().hasMemoryValue(
                MemoryModuleType.ATTACK_TARGET
        ) || maid.getBrain().isActive(Activity.PANIC);
        boolean protectedMovement = protectedMovement(maid, gameTime)
                || maid.getSwimManager().isGoingToBreath();
        if (!OwnerReturnPolicy.INSTANCE.eligible(
                !maid.isHomeModeEnable(),
                canMove,
                combatActive,
                protectedMovement
        )) {
            return false;
        }

        if (OwnerReturnPolicy.INSTANCE.closeEnough(
                maid.distanceToSqr(owner),
                closeEnoughDistance
        )) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().setMemory(
                    MemoryModuleType.LOOK_TARGET,
                    new EntityTracker(owner, true)
            );
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
                closeEnoughDistance
        );
        return true;
    }

    private static boolean targetsOwner(
            EntityMaid maid,
            LivingEntity owner
    ) {
        WalkTarget current = currentWalkTarget(maid);
        return current != null
                && current.getTarget() instanceof EntityTracker tracker
                && tracker.getEntity() == owner;
    }

    private static boolean protectedMovement(
            EntityMaid maid,
            long gameTime
    ) {
        if (!(maid instanceof MovementCoordinationAccess access)) {
            return false;
        }
        MovementIntentLease lease =
                access.maidIntelligence$movementIntentLease();
        return lease.isFailOpen(gameTime)
                || lease.hasActiveLease(gameTime);
    }

    private static LivingEntity validOwner(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner == null
                || !maid.isTame()
                || !owner.isAlive()
                || owner.isSpectator()
                || owner.level() != maid.level()) {
            return null;
        }
        return owner;
    }

    private static boolean isBuiltInTask(EntityMaid maid) {
        return TLM_NAMESPACE.equals(
                maid.getTask().getUid().getNamespace()
        );
    }

    private static WalkTarget currentWalkTarget(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
    }

    private BehaviorTuning currentTuning() {
        BehaviorTuning current = tuning.get();
        return current == null ? BehaviorTuning.defaults() : current;
    }

    private static long deadline(long gameTime, int durationTicks) {
        long duration = Math.max(0, durationTicks);
        return gameTime > Long.MAX_VALUE - duration
                ? Long.MAX_VALUE
                : gameTime + duration;
    }

    private static final class RuntimeState {
        private long lastTick = Long.MIN_VALUE;
        private boolean workTargetActive;
        private boolean postTaskPending;
        private long workReleasedAtTick = -1L;
        private long postTaskCooldownUntilTick;
        private long wanderCooldownUntilTick;
        private WalkTarget lastWalkTarget;

        private void clearPendingPostTask() {
            postTaskPending = false;
            workReleasedAtTick = -1L;
        }

        private void resetWork() {
            workTargetActive = false;
            clearPendingPostTask();
        }

        private void reset() {
            lastTick = Long.MIN_VALUE;
            resetWork();
            postTaskCooldownUntilTick = 0L;
            wanderCooldownUntilTick = 0L;
            lastWalkTarget = null;
        }
    }
}
