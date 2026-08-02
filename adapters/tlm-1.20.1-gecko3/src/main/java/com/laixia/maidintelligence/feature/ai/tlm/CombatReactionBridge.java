package com.laixia.maidintelligence.feature.ai.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.api.MaidAiOptimizationApi;
import com.laixia.maidintelligence.feature.ai.api.MaidAiTuning;
import com.laixia.maidintelligence.feature.ai.domain.CombatReactionPolicy;
import com.laixia.maidintelligence.feature.ai.domain.CombatThreatKind;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/**
 * Seeds TLM's existing combat memories without replacing its attack tasks.
 */
public final class CombatReactionBridge {
    private static final int VERTICAL_SCAN_RANGE = 6;

    private static MaidAiOptimizationApi optimization;

    private CombatReactionBridge() {
    }

    public static boolean beginAiStep(EntityMaid maid) {
        MaidAiOptimizationApi ai = optimization();
        MaidAiTuning.CombatReaction tuning =
                ai.tuning().combatReaction();
        if (!eligible(maid, ai, tuning)) {
            return false;
        }

        refreshAttackTarget(maid, tuning);
        return true;
    }

    public static WalkTarget captureMovement(EntityMaid maid) {
        return MovementCoordinationBridge.capture(maid);
    }

    public static void finishAiStep(
            EntityMaid maid,
            WalkTarget previous
    ) {
        LivingEntity attackTarget = maid.getBrain()
                .getMemory(MemoryModuleType.ATTACK_TARGET)
                .orElse(null);
        WalkTarget current = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (attackTarget != null
                && current != null
                && current.getTarget() instanceof EntityTracker tracker
                && tracker.getEntity() == attackTarget) {
            MovementCoordinationBridge.finishKnownWrite(
                    maid,
                    previous,
                    MovementIntentSource.COMBAT,
                    true
            );
            return;
        }
        if (current != previous) {
            // A third-party or non-combat writer remains authoritative.
            MovementCoordinationBridge.finishKnownWrite(
                    maid,
                    previous,
                    MovementIntentSource.COMBAT,
                    false
            );
        }
    }

    private static void refreshAttackTarget(
            EntityMaid maid,
            MaidAiTuning.CombatReaction tuning
    ) {
        IAttackTask attackTask = (IAttackTask) maid.getTask();
        LivingEntity current = maid.getBrain()
                .getMemory(MemoryModuleType.ATTACK_TARGET)
                .orElse(null);
        if (current != null && validExistingTarget(maid, attackTask, current)) {
            return;
        }
        if (current != null) {
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }

        LivingEntity owner = maid.getOwner();
        float radius = maid.getRestrictRadius();
        Candidate candidate = recentCandidate(
                maid,
                attackTask,
                maid.getLastHurtByMob(),
                maid.getLastHurtByMobTimestamp(),
                maid.tickCount,
                CombatThreatKind.MAID_ATTACKER,
                tuning,
                radius
        );
        candidate = betterOf(
                candidate,
                recentCandidate(
                        maid,
                        attackTask,
                        owner == null ? null : owner.getLastHurtByMob(),
                        owner == null ? 0 : owner.getLastHurtByMobTimestamp(),
                        owner == null ? 0 : owner.tickCount,
                        CombatThreatKind.OWNER_ATTACKER,
                        tuning,
                        radius
                )
        );
        candidate = betterOf(
                candidate,
                recentCandidate(
                        maid,
                        attackTask,
                        owner == null ? null : owner.getLastHurtMob(),
                        owner == null ? 0 : owner.getLastHurtMobTimestamp(),
                        owner == null ? 0 : owner.tickCount,
                        CombatThreatKind.OWNER_TARGET,
                        tuning,
                        radius
                )
        );
        if (candidate == null && shouldProactivelyScan(maid, tuning)) {
            candidate = proactiveCandidate(
                    maid,
                    attackTask,
                    tuning,
                    radius
            );
        }
        if (candidate == null) {
            return;
        }

        maid.getBrain().setMemory(
                MemoryModuleType.ATTACK_TARGET,
                candidate.entity()
        );
        maid.getBrain().eraseMemory(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        );
        optimization().recordCombatTarget(candidate.kind());
    }

    private static Candidate recentCandidate(
            EntityMaid maid,
            IAttackTask attackTask,
            LivingEntity target,
            int memoryTimestamp,
            int sourceTickCount,
            CombatThreatKind kind,
            MaidAiTuning.CombatReaction tuning,
            float radius
    ) {
        if (target == null) {
            return null;
        }
        int age = sourceTickCount - memoryTimestamp;
        boolean recent = age >= 0 && age <= tuning.recentThreatTicks();
        return candidate(
                maid,
                attackTask,
                target,
                kind,
                recent,
                true,
                radius
        );
    }

    private static Candidate proactiveCandidate(
            EntityMaid maid,
            IAttackTask attackTask,
            MaidAiTuning.CombatReaction tuning,
            float radius
    ) {
        AABB bounds = maid.getBoundingBox().inflate(
                radius,
                VERTICAL_SCAN_RANGE,
                radius
        );
        List<LivingEntity> entities = ((ServerLevel) maid.level())
                .getEntitiesOfClass(
                        LivingEntity.class,
                        bounds,
                        entity -> entity != maid && entity.isAlive()
                );
        entities.sort(Comparator.comparingDouble(maid::distanceToSqr));

        int evaluated = Math.min(entities.size(), tuning.candidateLimit());
        optimization().recordCombatScan(
                evaluated,
                entities.size() > evaluated
        );
        for (int index = 0; index < evaluated; index++) {
            Candidate candidate = candidate(
                    maid,
                    attackTask,
                    entities.get(index),
                    CombatThreatKind.PROACTIVE_HOSTILE,
                    true,
                    maid.hasLineOfSight(entities.get(index)),
                    radius
            );
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static Candidate candidate(
            EntityMaid maid,
            IAttackTask attackTask,
            LivingEntity target,
            CombatThreatKind kind,
            boolean recent,
            boolean visible,
            float radius
    ) {
        boolean alive = target.isAlive()
                && target.level() == maid.level();
        boolean attackable = alive
                && !maid.isAlliedTo(target)
                && attackTask.canAttack(maid, target);
        double distanceSquared = referenceDistanceSquared(maid, target);
        boolean withinRange = distanceSquared <= radius * radius;
        if (!CombatReactionPolicy.INSTANCE.eligible(
                kind,
                alive,
                attackable,
                visible,
                withinRange,
                recent
        )) {
            return null;
        }
        return new Candidate(target, kind, distanceSquared);
    }

    private static Candidate betterOf(
            Candidate current,
            Candidate candidate
    ) {
        if (candidate == null) {
            return current;
        }
        if (current == null || CombatReactionPolicy.INSTANCE.outranks(
                candidate.kind(),
                candidate.distanceSquared(),
                current.kind(),
                current.distanceSquared()
        )) {
            return candidate;
        }
        return current;
    }

    private static boolean shouldProactivelyScan(
            EntityMaid maid,
            MaidAiTuning.CombatReaction tuning
    ) {
        boolean stationary = !maid.isHomeModeEnable()
                && ActivityRadiusBridge.ownerStationary(maid);
        return CombatReactionPolicy.INSTANCE.shouldScan(
                maid.level().getGameTime(),
                maid.getId(),
                stationary,
                tuning
        );
    }

    private static boolean validExistingTarget(
            EntityMaid maid,
            IAttackTask attackTask,
            LivingEntity target
    ) {
        return target.isAlive()
                && target.level() == maid.level()
                && !maid.isAlliedTo(target)
                && attackTask.canAttack(maid, target);
    }

    private static double referenceDistanceSquared(
            EntityMaid maid,
            LivingEntity target
    ) {
        LivingEntity owner = maid.getOwner();
        if (!maid.isHomeModeEnable()
                && owner != null
                && owner.level() == maid.level()) {
            return owner.distanceToSqr(target);
        }
        return maid.distanceToSqr(target);
    }

    private static boolean eligible(
            EntityMaid maid,
            MaidAiOptimizationApi ai,
            MaidAiTuning.CombatReaction tuning
    ) {
        return ai.enabled()
                && tuning.enabled()
                && !maid.level().isClientSide()
                && ActivityRadiusBridge.isBuiltInCombatTask(maid)
                && maid.getBrain().isActive(Activity.WORK)
                && maid.canBrainMoving()
                && !maid.getBrain().isActive(Activity.PANIC)
                && !maid.isSleeping()
                && !maid.isMaidInSittingPose()
                && !maid.isOrderedToSit()
                && !maid.isLeashed()
                && !maid.isPassenger();
    }

    private static MaidAiOptimizationApi optimization() {
        MaidAiOptimizationApi current = optimization;
        if (current == null) {
            current = AdapterRuntime.require(MaidAiOptimizationApi.class);
            optimization = current;
        }
        return current;
    }

    private record Candidate(
            LivingEntity entity,
            CombatThreatKind kind,
            double distanceSquared
    ) {
    }
}
