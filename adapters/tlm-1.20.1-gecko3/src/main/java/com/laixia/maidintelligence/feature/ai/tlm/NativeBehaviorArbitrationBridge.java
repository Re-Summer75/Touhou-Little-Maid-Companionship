package com.laixia.maidintelligence.feature.ai.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyLevel;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyReason;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancySnapshot;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.MovementIntentAuthority;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentLease;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.OwnerCommandOverrideLease;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * Accesses the bounded owner-command override without persistent state.
 */
public final class NativeBehaviorArbitrationBridge {
    private NativeBehaviorArbitrationBridge() {
    }

    public static boolean acquireOwnerCommand(
            EntityMaid maid,
            long gameTime,
            int ttlTicks
    ) {
        return lease(maid).acquire(gameTime, ttlTicks);
    }

    public static boolean ownerCommandActive(
            EntityMaid maid,
            long gameTime
    ) {
        return lease(maid).isActive(gameTime);
    }

    public static void releaseOwnerCommand(EntityMaid maid) {
        lease(maid).release();
    }

    public static void hardReset(EntityMaid maid) {
        lease(maid).release();
    }

    public static void quiesceSoftBehavior(
            EntityMaid maid,
            BehaviorOccupancySnapshot occupancy
    ) {
        if (occupancy.level() != BehaviorOccupancyLevel.SOFT) {
            return;
        }

        Entity vehicle = maid.getVehicle();
        if (TlmBehaviorOccupancyClassifier.isPassiveSeat(vehicle)
                && !TlmBehaviorOccupancyClassifier.isSeatedWork(maid)) {
            maid.stopRiding();
        }
        if (occupancy.reason() == BehaviorOccupancyReason.LEISURE
                && !maid.getBrain().isActive(
                net.minecraft.world.entity.schedule.Activity.WORK
        )) {
            maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        }

        MovementIntentSource source = occupancy.movementSource();
        if (source == null
                || source.authority()
                != MovementIntentAuthority.NATIVE_SOFT) {
            maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            return;
        }
        MovementCoordinationBridge.capture(maid);
        MovementIntentLease movement = movementLease(maid);
        if (!movement.hasActiveLease(maid.level().getGameTime())
                || movement.holder() != source) {
            return;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        );
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        MovementCoordinationBridge.hardReset(maid);
    }

    private static OwnerCommandOverrideLease lease(EntityMaid maid) {
        return ((BehaviorArbitrationAccess) maid)
                .maidIntelligence$ownerCommandOverrideLease();
    }

    private static MovementIntentLease movementLease(EntityMaid maid) {
        return ((MovementCoordinationAccess) maid)
                .maidIntelligence$movementIntentLease();
    }
}
