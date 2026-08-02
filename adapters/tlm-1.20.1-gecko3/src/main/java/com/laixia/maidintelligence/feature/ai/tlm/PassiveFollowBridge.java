package com.laixia.maidintelligence.feature.ai.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.ai.api.MaidMovementCoordinationApi;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentLease;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.domain.PassiveFollowPolicy;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Adapts live TLM state to the platform-neutral passive-follow policy.
 */
public final class PassiveFollowBridge {
    private static final String TLM_NAMESPACE = "touhou_little_maid";

    private PassiveFollowBridge() {
    }

    public static boolean shouldDefer(EntityMaid maid) {
        MaidMovementCoordinationApi coordination =
                AdapterRuntime.require(MaidMovementCoordinationApi.class);
        if (!coordination.mode().enforcementEnabled()
                || maid.getSwimManager().isGoingToBreath()) {
            return false;
        }

        LivingEntity owner = maid.getOwner();
        if (owner == null
                || owner.isSpectator()
                || owner.isDeadOrDying()
                || owner.level() != maid.level()
                || currentTargetIsOwner(maid, owner)) {
            return false;
        }

        int followDistance = Math.max(
                0,
                (int) maid.getRestrictRadius() - 2
        );
        int teleportDistance = followDistance + 4;
        return PassiveFollowPolicy.INSTANCE.shouldDefer(
                ActivityRadiusBridge.ownerStationary(maid),
                hasProtectedTask(maid),
                maid.distanceToSqr(owner),
                followDistance,
                teleportDistance
        );
    }

    private static boolean hasProtectedTask(EntityMaid maid) {
        long gameTime = maid.level().getGameTime();
        MovementIntentLease lease =
                ((MovementCoordinationAccess) maid)
                        .maidIntelligence$movementIntentLease();
        if (lease.isFailOpen(gameTime)) {
            return false;
        }
        if (lease.hasActiveLease(gameTime)
                && protectsFromPassiveFollow(lease.holder())) {
            return true;
        }
        if (!TLM_NAMESPACE.equals(maid.getTask().getUid().getNamespace())
                || !maid.getBrain().isActive(Activity.WORK)) {
            return false;
        }
        return maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || maid.getBrain().hasMemoryValue(
                InitEntities.TARGET_POS.get()
        )
                || maid.getBrain().hasMemoryValue(
                MemoryModuleType.WALK_TARGET
        );
    }

    private static boolean protectsFromPassiveFollow(
            MovementIntentSource source
    ) {
        return source == MovementIntentSource.BUILT_IN_WORK
                || source == MovementIntentSource.COMBAT
                || source == MovementIntentSource.STEAL_EDIBLE
                || source == MovementIntentSource.PICKUP;
    }

    private static boolean currentTargetIsOwner(
            EntityMaid maid,
            LivingEntity owner
    ) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target.getTarget())
                .filter(EntityTracker.class::isInstance)
                .map(EntityTracker.class::cast)
                .map(EntityTracker::getEntity)
                .filter(owner::equals)
                .isPresent();
    }
}
