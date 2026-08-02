package com.laixia.maidintelligence.feature.ai.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.api.MaidMovementCoordinationApi;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentDecision;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentLease;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.domain.MovementTargetKind;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Converts TLM Brain memory into allocation-free stable movement identities.
 */
public final class MovementCoordinationBridge {
    private static final String TLM_PACKAGE =
            "com.github.tartaricacid.touhoulittlemaid.";

    private static MaidMovementCoordinationApi coordination;

    private MovementCoordinationBridge() {
    }

    public static WalkTarget capture(EntityMaid maid) {
        WalkTarget current = currentWalkTarget(maid);
        reconcile(maid, current);
        return current;
    }

    public static void finishKnownWrite(
            EntityMaid maid,
            WalkTarget previous,
            MovementIntentSource source,
            boolean managedImplementation
    ) {
        WalkTarget current = currentWalkTarget(maid);
        if (current == previous) {
            return;
        }
        if (current == null) {
            coordination().hardReset(lease(maid));
            return;
        }
        if (!managedImplementation) {
            reconcile(maid, current);
            return;
        }
        if (shouldPreserveHardState(maid, source)) {
            restore(maid, previous);
            return;
        }

        MovementIntentDecision decision = claimCurrent(
                maid,
                current,
                source
        );
        if (!decision.writeAllowed()
                || (decision == MovementIntentDecision.RENEWED
                && coordination().mode().enforcementEnabled())) {
            // The original helper already updated LOOK_TARGET. Restoring only
            // WALK_TARGET keeps visual tracking while avoiding path churn.
            restore(maid, previous);
        }
    }

    public static void reconcile(EntityMaid maid) {
        if (!coordination().mode().trackingEnabled()) {
            coordination().hardReset(lease(maid));
            return;
        }
        reconcile(maid, currentWalkTarget(maid));
    }

    public static void hardReset(EntityMaid maid) {
        coordination().hardReset(lease(maid));
    }

    public static boolean isExactImplementation(
            Object instance,
            Class<?> expectedType
    ) {
        return instance.getClass() == expectedType;
    }

    public static boolean isManagedBuiltInWork(
            EntityMaid maid,
            Object instance
    ) {
        if (!instance.getClass().getName().startsWith(TLM_PACKAGE)) {
            return false;
        }
        if (instance.getClass().getSimpleName()
                .equals("MaidStealEdibleMoveBlockTask")) {
            return true;
        }
        return maid.getTask().getUid().getNamespace()
                .equals("touhou_little_maid");
    }

    public static MovementIntentSource builtInWorkSource(Object instance) {
        if (instance.getClass().getSimpleName()
                .equals("MaidStealEdibleMoveBlockTask")) {
            return MovementIntentSource.STEAL_EDIBLE;
        }
        return MovementIntentSource.BUILT_IN_WORK;
    }

    private static void reconcile(EntityMaid maid, WalkTarget current) {
        if (current == null) {
            reconcile(maid, MovementTargetKind.NONE, 0L);
            return;
        }
        PositionTracker tracker = current.getTarget();
        if (tracker instanceof EntityTracker entityTracker) {
            Entity target = entityTracker.getEntity();
            if (invalidEntityTarget(maid, target)) {
                reconcile(maid, MovementTargetKind.NONE, 0L);
                return;
            }
            reconcile(
                    maid,
                    MovementTargetKind.ENTITY,
                    movementIdentity(
                            current,
                            entityIdentity(target)
                    )
            );
            return;
        }
        if (tracker instanceof BlockPosTracker) {
            reconcile(
                    maid,
                    MovementTargetKind.BLOCK,
                    movementIdentity(
                            current,
                            tracker.currentBlockPosition().asLong()
                    )
            );
            return;
        }
        reconcile(
                maid,
                MovementTargetKind.UNMANAGED,
                System.identityHashCode(tracker)
        );
    }

    private static boolean shouldPreserveHardState(
            EntityMaid maid,
            MovementIntentSource source
    ) {
        if (!coordination().mode().enforcementEnabled()) {
            return false;
        }
        if (!maid.canBrainMoving()) {
            return true;
        }
        return source != MovementIntentSource.BREATH_AIR
                && maid.getBrain().isActive(Activity.PANIC);
    }

    private static void restore(EntityMaid maid, WalkTarget previous) {
        if (previous == null) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        } else {
            maid.getBrain().setMemory(
                    MemoryModuleType.WALK_TARGET,
                    previous
            );
        }
    }

    private static WalkTarget currentWalkTarget(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
    }

    private static MovementIntentDecision claimCurrent(
            EntityMaid maid,
            WalkTarget current,
            MovementIntentSource source
    ) {
        PositionTracker tracker = current.getTarget();
        if (tracker instanceof EntityTracker entityTracker) {
            Entity target = entityTracker.getEntity();
            if (invalidEntityTarget(maid, target)) {
                reconcile(maid, MovementTargetKind.NONE, 0L);
                return MovementIntentDecision.PASS_THROUGH;
            }
            return coordination().claim(
                    lease(maid),
                    maid.level().getGameTime(),
                    source,
                    MovementTargetKind.ENTITY,
                    movementIdentity(
                            current,
                            entityIdentity(target)
                    )
            );
        }
        if (tracker instanceof BlockPosTracker) {
            return coordination().claim(
                    lease(maid),
                    maid.level().getGameTime(),
                    source,
                    MovementTargetKind.BLOCK,
                    movementIdentity(
                            current,
                            tracker.currentBlockPosition().asLong()
                    )
            );
        }
        reconcile(maid, current);
        return MovementIntentDecision.PASS_THROUGH;
    }

    private static void reconcile(
            EntityMaid maid,
            MovementTargetKind targetKind,
            long targetIdentity
    ) {
        coordination().reconcile(
                lease(maid),
                maid.level().getGameTime(),
                targetKind,
                targetIdentity
        );
    }

    private static MovementIntentLease lease(EntityMaid maid) {
        return ((MovementCoordinationAccess) maid)
                .maidIntelligence$movementIntentLease();
    }

    private static MaidMovementCoordinationApi coordination() {
        MaidMovementCoordinationApi current = coordination;
        if (current == null) {
            current = AdapterRuntime.require(
                    MaidMovementCoordinationApi.class
            );
            coordination = current;
        }
        return current;
    }

    private static long entityIdentity(Entity entity) {
        return ((long) entity.getId() << 32)
                ^ (System.identityHashCode(entity) & 0xffffffffL);
    }

    private static boolean invalidEntityTarget(
            EntityMaid maid,
            Entity target
    ) {
        return !target.isAlive()
                || (target instanceof ItemEntity item
                && !maid.canPickup(item, true));
    }

    private static long movementIdentity(
            WalkTarget walkTarget,
            long targetIdentity
    ) {
        long speed = Float.floatToRawIntBits(
                walkTarget.getSpeedModifier()
        ) & 0xffffffffL;
        long distance = walkTarget.getCloseEnoughDist() & 0xffffffffL;
        return targetIdentity
                ^ Long.rotateLeft(speed, 17)
                ^ Long.rotateLeft(distance, 43);
    }
}
