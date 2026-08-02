package com.laixia.maidintelligence.feature.ai.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.api.MaidAiOptimizationApi;
import com.laixia.maidintelligence.feature.ai.api.MaidAiTuning;
import com.laixia.maidintelligence.feature.ai.domain.DynamicActivityRadiusPolicy;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Converts live TLM state into a non-persistent effective activity radius.
 */
public final class ActivityRadiusBridge {
    private static final double MOVEMENT_EPSILON_SQUARED = 0.0025D;
    private static final String TLM_NAMESPACE = "touhou_little_maid";

    private static MaidAiOptimizationApi optimization;

    private ActivityRadiusBridge() {
    }

    public static float effectiveRadius(
            EntityMaid maid,
            float baseRadius
    ) {
        MaidAiOptimizationApi ai = optimization();
        MaidAiTuning.ActivityRadius tuning = ai.tuning().activityRadius();
        if (!eligibleForExpansion(maid, tuning)) {
            return baseRadius;
        }

        boolean stationary = ownerStationary(maid);
        return DynamicActivityRadiusPolicy.INSTANCE.effectiveRadius(
                baseRadius,
                stationary,
                activityKind(maid),
                tuning
        );
    }

    public static boolean ownerStationary(EntityMaid maid) {
        LivingEntity owner = validOwner(maid);
        ActivityRadiusState state = state(maid);
        if (owner == null) {
            state.ownerMotion().reset();
            return false;
        }

        MaidAiTuning.ActivityRadius tuning =
                optimization().tuning().activityRadius();
        double deltaX = owner.getX() - owner.xo;
        double deltaY = owner.getY() - owner.yo;
        double deltaZ = owner.getZ() - owner.zo;
        boolean moving = deltaX * deltaX
                + deltaY * deltaY
                + deltaZ * deltaZ
                > MOVEMENT_EPSILON_SQUARED;
        return state.ownerMotion().observe(
                maid.level().getGameTime(),
                ownerIdentity(owner),
                moving,
                tuning.stationaryConfirmTicks(),
                tuning.movingConfirmTicks()
        );
    }

    public static boolean applyRadius(
            EntityMaid maid,
            float baseRadius,
            float effectiveRadius
    ) {
        boolean changed = state(maid).updateEffectiveRadius(effectiveRadius);
        if (changed && effectiveRadius > baseRadius) {
            optimization().recordActivityRadiusExpansion();
        }
        return changed;
    }

    public static boolean isBuiltInCombatTask(EntityMaid maid) {
        return isBuiltInTask(maid)
                && maid.getTask() instanceof IAttackTask;
    }

    public static boolean adaptiveStateAllowed(EntityMaid maid) {
        if (maid.level().isClientSide()
                || maid.isHomeModeEnable()
                || !maid.canBrainMoving()
                || maid.isMaidInSittingPose()
                || maid.isOrderedToSit()
                || maid.isSleeping()
                || maid.isLeashed()
                || maid.isPassenger()
                || maid.getBrain().isActive(Activity.PANIC)
                || !isBuiltInTask(maid)) {
            return false;
        }
        long gameTime = maid.level().getGameTime();
        return !((MovementCoordinationAccess) maid)
                .maidIntelligence$movementIntentLease()
                .isFailOpen(gameTime);
    }

    public static void reset(EntityMaid maid) {
        state(maid).reset();
    }

    private static boolean eligibleForExpansion(
            EntityMaid maid,
            MaidAiTuning.ActivityRadius tuning
    ) {
        return optimization().enabled()
                && tuning.enabled()
                && adaptiveStateAllowed(maid)
                && validOwner(maid) != null;
    }

    private static boolean isBuiltInTask(EntityMaid maid) {
        return TLM_NAMESPACE.equals(
                maid.getTask().getUid().getNamespace()
        );
    }

    private static DynamicActivityRadiusPolicy.ActivityKind activityKind(
            EntityMaid maid
    ) {
        if (maid.getBrain().isActive(Activity.WORK)) {
            if (isBuiltInCombatTask(maid)) {
                return DynamicActivityRadiusPolicy.ActivityKind.COMBAT;
            }
            return DynamicActivityRadiusPolicy.ActivityKind.WORK;
        }
        return DynamicActivityRadiusPolicy.ActivityKind.IDLE;
    }

    private static LivingEntity validOwner(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner == null
                || !owner.isAlive()
                || owner.isSpectator()
                || owner.level() != maid.level()) {
            return null;
        }
        return owner;
    }

    private static long ownerIdentity(LivingEntity owner) {
        return owner.getUUID().getMostSignificantBits()
                ^ owner.getUUID().getLeastSignificantBits();
    }

    private static ActivityRadiusState state(EntityMaid maid) {
        return ((ActivityRadiusAccess) maid)
                .maidIntelligence$activityRadiusState();
    }

    private static MaidAiOptimizationApi optimization() {
        MaidAiOptimizationApi current = optimization;
        if (current == null) {
            current = AdapterRuntime.require(MaidAiOptimizationApi.class);
            optimization = current;
        }
        return current;
    }
}
