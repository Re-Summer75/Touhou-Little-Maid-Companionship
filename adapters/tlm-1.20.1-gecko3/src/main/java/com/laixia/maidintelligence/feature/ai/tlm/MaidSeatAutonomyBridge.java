package com.laixia.maidintelligence.feature.ai.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Lets built-in movement needs release only TLM-owned passive seats.
 * Commanded sitting and unrelated vehicle integrations remain untouched.
 */
@SuppressWarnings("null")
public final class MaidSeatAutonomyBridge {
    private static final int COMBAT_SCAN_INTERVAL_TICKS = 5;
    private static final int COMBAT_CANDIDATE_LIMIT = 64;
    private static final int VERTICAL_COMBAT_SCAN_RANGE = 6;

    private MaidSeatAutonomyBridge() {
    }

    public static void leaveSeatForFollow(EntityMaid maid) {
        if (!canLeavePassiveSeat(maid) || maid.isHomeModeEnable()) {
            return;
        }

        LivingEntity owner = maid.getOwner();
        if (owner == null
                || owner.isSpectator()
                || owner.isDeadOrDying()
                || maid.level() != owner.level()) {
            return;
        }

        int followDistance = (int) maid.getRestrictRadius() - 2;
        if (!maid.closerThan(owner, followDistance)) {
            maid.stopRiding();
        }
    }

    public static void leaveSeatForPickup(EntityMaid maid) {
        if (!canLeavePassiveSeat(maid)
                || !maid.isPickup()
                || isSeatedWork(maid)) {
            return;
        }

        List<Entity> candidates = maid.getBrain()
                .getMemory(InitEntities.VISIBLE_PICKUP_ENTITIES.get())
                .orElse(List.of());
        for (Entity candidate : candidates) {
            if (candidate.isAlive()
                    && !candidate.isInWater()
                    && maid.isWithinRestriction(candidate.blockPosition())) {
                maid.stopRiding();
                return;
            }
        }
    }

    public static void leaveSeatForCombat(EntityMaid maid) {
        if (!canLeavePassiveSeat(maid)
                || isSeatedWork(maid)
                || !ActivityRadiusBridge.isBuiltInCombatTask(maid)) {
            return;
        }

        IAttackTask attackTask = (IAttackTask) maid.getTask();
        LivingEntity current = maid.getBrain()
                .getMemory(MemoryModuleType.ATTACK_TARGET)
                .orElse(null);
        if (validCombatTarget(maid, attackTask, current)) {
            dismountForCombat(maid, current);
            return;
        }
        if (Math.floorMod(
                maid.level().getGameTime() + maid.getId(),
                COMBAT_SCAN_INTERVAL_TICKS
        ) != 0L) {
            return;
        }

        float radius = Math.max(1.0F, maid.getRestrictRadius());
        AABB bounds = maid.getBoundingBox().inflate(
                radius,
                VERTICAL_COMBAT_SCAN_RANGE,
                radius
        );
        LivingEntity nearest = null;
        double nearestDistanceSquared = Double.POSITIVE_INFINITY;
        int evaluated = 0;
        List<LivingEntity> nearby = maid.level().getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                entity -> entity != maid && entity.isAlive()
        );
        for (LivingEntity candidate : nearby) {
            if (evaluated++ >= COMBAT_CANDIDATE_LIMIT) {
                break;
            }
            if (!validCombatTarget(maid, attackTask, candidate)) {
                continue;
            }
            if (!maid.hasLineOfSight(candidate)) {
                continue;
            }
            if (!maid.isWithinRestriction(candidate.blockPosition())) {
                continue;
            }
            double distanceSquared = maid.distanceToSqr(candidate);
            if (distanceSquared < nearestDistanceSquared) {
                nearest = candidate;
                nearestDistanceSquared = distanceSquared;
            }
        }
        if (nearest != null) {
            dismountForCombat(maid, nearest);
        }
    }

    private static boolean canLeavePassiveSeat(EntityMaid maid) {
        if (maid.level().isClientSide()
                || maid.isMaidInSittingPose()
                || maid.isOrderedToSit()
                || maid.isSleeping()
                || maid.isLeashed()) {
            return false;
        }

        Entity vehicle = maid.getVehicle();
        return vehicle != null
                && (vehicle.getType() == EntityChair.TYPE
                || vehicle.getType() == EntitySit.TYPE);
    }

    private static boolean isSeatedWork(EntityMaid maid) {
        if (maid.getScheduleDetail() != Activity.WORK) {
            return false;
        }

        Entity vehicle = maid.getVehicle();
        if (vehicle instanceof EntitySit seat) {
            return maid.getTask().canSitInJoy(maid, seat.getJoyType());
        }
        return vehicle != null
                && vehicle.getType() == EntityChair.TYPE
                && maid.getTask().workPointTask(maid);
    }

    private static boolean validCombatTarget(
            EntityMaid maid,
            IAttackTask attackTask,
            LivingEntity target
    ) {
        return target != null
                && target.isAlive()
                && target.level() == maid.level()
                && !maid.isAlliedTo(target)
                && attackTask.canAttack(maid, target);
    }

    private static void dismountForCombat(
            EntityMaid maid,
            LivingEntity target
    ) {
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.stopRiding();
    }
}
