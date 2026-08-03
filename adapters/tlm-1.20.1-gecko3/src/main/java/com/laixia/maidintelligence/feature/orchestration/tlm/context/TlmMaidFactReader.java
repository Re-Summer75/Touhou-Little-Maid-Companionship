package com.laixia.maidintelligence.feature.orchestration.tlm.context;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyLevel;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancySnapshot;
import com.laixia.maidintelligence.feature.ai.tlm.TlmBehaviorOccupancyClassifier;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentObserver;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;

import java.util.List;
import java.util.Objects;

/**
 * Builds one immutable TLM fact snapshot per orchestration read.
 */
public final class TlmMaidFactReader {
    private static final String TLM_NAMESPACE = "touhou_little_maid";

    private final MaidStatusApi<EntityMaid> status;
    private final TlmMaidIntentObserver observer;
    private final MaidSnackCabinetMealSource snackCabinetMeals;
    private final TlmAffordancePerceptionService perception;

    public TlmMaidFactReader(
            MaidStatusApi<EntityMaid> status,
            TlmMaidIntentObserver observer,
            MaidSnackCabinetMealSource snackCabinetMeals,
            TlmAffordancePerceptionService perception
    ) {
        this.status = Objects.requireNonNull(status, "status");
        this.observer = Objects.requireNonNull(observer, "observer");
        this.snackCabinetMeals = Objects.requireNonNull(
                snackCabinetMeals,
                "snackCabinetMeals"
        );
        this.perception = Objects.requireNonNull(
                perception,
                "perception"
        );
    }

    public void readFacts(
            EntityMaid maid,
            long gameTime,
            List<OrchestrationId> facts,
            double[] output
    ) {
        Snapshot snapshot = snapshot(maid, gameTime);
        for (int index = 0; index < facts.size(); index++) {
            output[index] = value(facts.get(index), snapshot);
        }
    }

    @SuppressWarnings("null")
    private Snapshot snapshot(EntityMaid maid, long gameTime) {
        perception.observeMaid(maid, gameTime);
        LivingEntity owner = validOwner(maid);
        boolean ownerValid = owner != null;
        double ownerDistance = ownerValid
                ? Math.sqrt(maid.distanceToSqr(owner))
                : Double.NaN;
        boolean passiveSeat =
                TlmBehaviorOccupancyClassifier.isPassiveSeat(
                        maid.getVehicle()
                );
        boolean sittingPose = maid.isMaidInSittingPose();
        boolean orderedSit = maid.isOrderedToSit();
        boolean sleeping = maid.isSleeping();
        boolean leashed = maid.isLeashed();
        boolean passenger = maid.isPassenger();
        boolean commandVehicle =
                MaidCommandSeatBridge.isSeatProtected(maid);
        boolean canMove = !sittingPose
                && !orderedSit
                && !sleeping
                && !leashed
                && (!passenger || passiveSeat || commandVehicle);
        boolean attackTargetPresent = maid.getBrain().hasMemoryValue(
                MemoryModuleType.ATTACK_TARGET
        );
        boolean panicActive = maid.getBrain().isActive(Activity.PANIC);
        boolean workTargetPresent = maid.getBrain().hasMemoryValue(
                InitEntities.TARGET_POS.get()
        );
        boolean usingItem = maid.isUsingItem();
        BehaviorOccupancySnapshot occupancy =
                TlmBehaviorOccupancyClassifier.snapshot(maid, gameTime);
        boolean homeMode = maid.isHomeModeEnable();
        int hunger = status.getState(maid).hunger();
        boolean snackCabinetMealAvailable =
                hunger <= DefaultHungerPolicy.AUTO_EAT_THRESHOLD
                        && canMove
                        && !attackTargetPresent
                        && !panicActive
                        && !usingItem
                        && occupancy.allowsPassiveCompanion()
                        && snackCabinetMeals.findAvailableMeal(
                                maid,
                                gameTime
                        ).isPresent();
        return new Snapshot(
                ownerValid,
                ownerDistance,
                maid.getFavorabilityManager().getLevel(),
                hunger,
                snackCabinetMealAvailable,
                !homeMode,
                homeMode,
                orderedSit,
                sittingPose,
                sleeping,
                leashed,
                passenger,
                passiveSeat,
                canMove,
                attackTargetPresent || panicActive,
                attackTargetPresent,
                panicActive,
                workTargetPresent,
                usingItem,
                isBuiltInTask(maid),
                occupancy.level() == BehaviorOccupancyLevel.HARD,
                observer.workReleaseAge(maid, gameTime),
                occupancy.movementSource() != null,
                occupancy.movementSource() == null
                        ? -1
                        : occupancy.movementSource().priority(),
                occupancy.movementFailOpen(),
                occupancy.level().code(),
                occupancy.reason().code()
        );
    }

    private static double value(
            OrchestrationId fact,
            Snapshot snapshot
    ) {
        if (fact.equals(CompanionIntentIds.OWNER_VALID)) {
            return bool(snapshot.ownerValid());
        }
        if (fact.equals(CompanionIntentIds.OWNER_DISTANCE)) {
            return snapshot.ownerDistance();
        }
        if (fact.equals(CompanionIntentIds.FAVORABILITY)) {
            return snapshot.favorability();
        }
        if (fact.equals(CompanionIntentIds.HUNGER)) {
            return snapshot.hunger();
        }
        if (fact.equals(
                CompanionIntentIds.SNACK_CABINET_MEAL_AVAILABLE
        )) {
            return bool(snapshot.snackCabinetMealAvailable());
        }
        if (fact.equals(CompanionIntentIds.FOLLOW_MODE)) {
            return bool(snapshot.followMode());
        }
        if (fact.equals(CompanionIntentIds.HOME_MODE)) {
            return bool(snapshot.homeMode());
        }
        if (fact.equals(CompanionIntentIds.ORDERED_SIT)) {
            return bool(snapshot.orderedSit());
        }
        if (fact.equals(CompanionIntentIds.SITTING_POSE)) {
            return bool(snapshot.sittingPose());
        }
        if (fact.equals(CompanionIntentIds.SLEEPING)) {
            return bool(snapshot.sleeping());
        }
        if (fact.equals(CompanionIntentIds.LEASHED)) {
            return bool(snapshot.leashed());
        }
        if (fact.equals(CompanionIntentIds.PASSENGER)) {
            return bool(snapshot.passenger());
        }
        if (fact.equals(CompanionIntentIds.PASSIVE_SEAT)) {
            return bool(snapshot.passiveSeat());
        }
        if (fact.equals(CompanionIntentIds.CAN_MOVE)) {
            return bool(snapshot.canMove());
        }
        if (fact.equals(CompanionIntentIds.COMBAT_ACTIVE)) {
            return bool(snapshot.combatActive());
        }
        if (fact.equals(CompanionIntentIds.ATTACK_TARGET_PRESENT)) {
            return bool(snapshot.attackTargetPresent());
        }
        if (fact.equals(CompanionIntentIds.PANIC_ACTIVE)) {
            return bool(snapshot.panicActive());
        }
        if (fact.equals(CompanionIntentIds.WORK_TARGET_PRESENT)) {
            return bool(snapshot.workTargetPresent());
        }
        if (fact.equals(CompanionIntentIds.USING_ITEM)) {
            return bool(snapshot.usingItem());
        }
        if (fact.equals(CompanionIntentIds.BUILT_IN_TASK)) {
            return bool(snapshot.builtInTask());
        }
        if (fact.equals(CompanionIntentIds.MOVEMENT_HARD_BLOCKED)) {
            return bool(snapshot.movementHardBlocked());
        }
        if (fact.equals(CompanionIntentIds.WORK_RELEASE_AGE)) {
            return snapshot.workReleaseAge();
        }
        if (fact.equals(CompanionIntentIds.MOVEMENT_LEASE_ACTIVE)) {
            return bool(snapshot.movementLeaseActive());
        }
        if (fact.equals(CompanionIntentIds.MOVEMENT_LEASE_PRIORITY)) {
            return snapshot.movementLeasePriority();
        }
        if (fact.equals(CompanionIntentIds.MOVEMENT_FAIL_OPEN)) {
            return bool(snapshot.movementFailOpen());
        }
        if (fact.equals(CompanionIntentIds.BEHAVIOR_OCCUPANCY_LEVEL)) {
            return snapshot.behaviorOccupancyLevel();
        }
        if (fact.equals(CompanionIntentIds.BEHAVIOR_OCCUPANCY_REASON)) {
            return snapshot.behaviorOccupancyReason();
        }
        return Double.NaN;
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

    private static double bool(boolean value) {
        return value ? 1.0D : 0.0D;
    }

    private record Snapshot(
            boolean ownerValid,
            double ownerDistance,
            int favorability,
            int hunger,
            boolean snackCabinetMealAvailable,
            boolean followMode,
            boolean homeMode,
            boolean orderedSit,
            boolean sittingPose,
            boolean sleeping,
            boolean leashed,
            boolean passenger,
            boolean passiveSeat,
            boolean canMove,
            boolean combatActive,
            boolean attackTargetPresent,
            boolean panicActive,
            boolean workTargetPresent,
            boolean usingItem,
            boolean builtInTask,
            boolean movementHardBlocked,
            double workReleaseAge,
            boolean movementLeaseActive,
            int movementLeasePriority,
            boolean movementFailOpen,
            int behaviorOccupancyLevel,
            int behaviorOccupancyReason
    ) {
    }
}
