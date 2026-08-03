package com.laixia.maidintelligence.feature.ai.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.api.MaidMovementCoordinationApi;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyLevel;
import com.laixia.maidintelligence.feature.ai.domain.PassiveFollowPolicy;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * Adapts live TLM state to the platform-neutral passive-follow policy.
 */
public final class PassiveFollowBridge {
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
        return TlmBehaviorOccupancyClassifier.snapshot(maid, gameTime)
                .level() != BehaviorOccupancyLevel.IDLE;
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
