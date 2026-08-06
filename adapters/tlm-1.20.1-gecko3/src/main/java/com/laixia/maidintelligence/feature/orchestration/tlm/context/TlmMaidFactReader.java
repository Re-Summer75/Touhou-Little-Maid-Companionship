package com.laixia.maidintelligence.feature.orchestration.tlm.context;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyLevel;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancySnapshot;
import com.laixia.maidintelligence.feature.ai.tlm.TlmBehaviorOccupancyClassifier;
import com.laixia.maidintelligence.feature.behavior.application.forecast.OwnerActivityTracker;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.domain.owner.OwnerFacts;
import com.laixia.maidintelligence.feature.behavior.domain.forecast.CompanionActivity;
import com.laixia.maidintelligence.feature.orchestration.api.insight.MaidInsight;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;

/**
 * Builds one immutable TLM fact snapshot per orchestration read.
 */
public final class TlmMaidFactReader {
    private static final String TLM_NAMESPACE = "touhou_little_maid";

    private final TlmOwnerFactReader ownerFacts =
            new TlmOwnerFactReader();

    /**
     * Fact id to the activity it forecasts, built once from the enum so the
     * dispatch below cannot fall behind {@link CompanionActivity}.
     */
    private static final Map<OrchestrationId, CompanionActivity>
            FORECAST_FACTS = forecastFacts();
    private static final Map<OrchestrationId, CompanionActivity>
            FORECAST_LIFT_FACTS = forecastLiftFacts();

    private final MaidStatusApi<EntityMaid> status;
    private final TlmMaidIntentObserver observer;
    private final MaidSnackCabinetMealSource snackCabinetMeals;
    private final TlmAffordancePerceptionService perception;
    private final OwnerActivityTracker activityTracker;

    public TlmMaidFactReader(
            MaidStatusApi<EntityMaid> status,
            TlmMaidIntentObserver observer,
            MaidSnackCabinetMealSource snackCabinetMeals,
            TlmAffordancePerceptionService perception
    ) {
        this(
                status,
                observer,
                snackCabinetMeals,
                perception,
                new OwnerActivityTracker()
        );
    }

    public TlmMaidFactReader(
            MaidStatusApi<EntityMaid> status,
            TlmMaidIntentObserver observer,
            MaidSnackCabinetMealSource snackCabinetMeals,
            TlmAffordancePerceptionService perception,
            OwnerActivityTracker activityTracker
    ) {
        this.activityTracker = Objects.requireNonNull(
                activityTracker,
                "activityTracker"
        );
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
        LivingEntity owner = validOwner(maid);
        /*
         * Sampled here rather than on a timer of its own because this already
         * runs on the owner's behalf at a sensible cadence. The tracker only
         * records when the activity actually changes, so several maids sharing
         * an owner cannot inflate the same transition.
         */
        if (owner != null) {
            activityTracker.observe(
                    owner.getUUID(),
                    OwnerActivityClassifier.classify(owner, gameTime),
                    gameTime
            );
        }
        for (int index = 0; index < facts.size(); index++) {
            OrchestrationId fact = facts.get(index);
            CompanionActivity forecast = FORECAST_FACTS.get(fact);
            CompanionActivity lift = FORECAST_LIFT_FACTS.get(fact);
            if (forecast == null && lift == null) {
                output[index] = value(fact, snapshot);
            } else if (owner == null) {
                /*
                 * No owner is no evidence, not a prediction of zero: a zero
                 * would veto every intent that multiplies this in. The two
                 * families have different neutral points — a flat probability
                 * for one, "as likely as usual" for the other.
                 */
                output[index] = forecast != null
                        ? 1.0D / CompanionActivity.count()
                        : 0.5D;
            } else if (forecast != null) {
                output[index] = activityTracker.probability(
                        owner.getUUID(),
                        forecast,
                        gameTime
                );
            } else {
                output[index] = activityTracker.lift(
                        owner.getUUID(),
                        lift
                );
            }
        }
    }

    /**
     * How much of the forecast came from observed sequence rather than the time
     * of day. Read by {@code ai explain} so a prediction is never reported
     * without saying how much is actually known.
     */
    public double forecastConfidence(EntityMaid maid) {
        LivingEntity owner = validOwner(maid);
        return owner == null
                ? 0.0D
                : activityTracker.confidence(owner.getUUID());
    }

    /**
     * The owner activity that is most unusually likely right now, or
     * {@code null} when there is no owner.
     *
     * <p>Ranked by lift rather than by raw probability. Travelling is close to
     * half of all transitions, so ranking by probability would nominate it
     * almost always and the panel would say the same thing forever.
     */
    public MaidInsight.Hunch bestHunch(EntityMaid maid) {
        LivingEntity owner = validOwner(maid);
        if (owner == null) {
            return null;
        }
        CompanionActivity best = null;
        double bestLift = 0.0D;
        for (CompanionActivity activity : CompanionActivity.values()) {
            double lift = activityTracker.lift(owner.getUUID(), activity);
            if (best == null || lift > bestLift) {
                best = activity;
                bestLift = lift;
            }
        }
        return new MaidInsight.Hunch(
                best.name().toLowerCase(java.util.Locale.ROOT),
                Math.max(0.0D, Math.min(0.999D, bestLift)),
                activityTracker.evidence(owner.getUUID())
        );
    }

    public CompanionActivity currentOwnerActivity(EntityMaid maid) {
        LivingEntity owner = validOwner(maid);
        return owner == null
                ? CompanionActivity.IDLE
                : activityTracker.current(owner.getUUID());
    }

    private static Map<OrchestrationId, CompanionActivity> forecastFacts() {
        Map<OrchestrationId, CompanionActivity> facts =
                new LinkedHashMap<>();
        for (CompanionActivity activity : CompanionActivity.values()) {
            facts.put(activity.forecastFact(), activity);
        }
        return Map.copyOf(facts);
    }

    private static Map<OrchestrationId, CompanionActivity> forecastLiftFacts() {
        Map<OrchestrationId, CompanionActivity> facts =
                new LinkedHashMap<>();
        for (CompanionActivity activity : CompanionActivity.values()) {
            facts.put(activity.forecastLiftFact(), activity);
        }
        return Map.copyOf(facts);
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
        /*
         * Only whether there is a meal to be had, and deliberately nothing
         * about whether she is free to go and get it.
         *
         * <p>This used to fold in hunger, movement, combat, panic, item use and
         * occupancy as well. Every one of those is already a fact of its own
         * that the intent tests separately, so the duplication bought nothing —
         * and it cost a great deal, because an opportunity is re-tested every
         * tick while she walks. A single tick of soft occupancy made the meal
         * "unavailable", cancelled the errand, handed her to the intent that
         * asks her owner for food instead, and handed her back when it cleared.
         * Measured against a fact that blinks every twenty ticks, she changed
         * her mind ten times in two hundred; every blink cost a switch.
         */
        boolean snackCabinetMealAvailable =
                snackCabinetMeals.findAvailableMeal(maid, gameTime)
                        .isPresent();
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
                occupancy.reason().code(),
                /*
                 * Only whether something edible is lying about, for the same
                 * reason the cabinet fact above says only whether a meal is to
                 * be had: an opportunity is re-tested every tick she walks, so
                 * folding her freedom into it turns a passing distraction into
                 * an abandoned errand. Her freedom is already several facts of
                 * its own, and the intent tests them itself.
                 */
                !perception.queryLooseFood(maid, 1, gameTime).isEmpty(),
                homeDistance(maid),
                ownerFacts.read(owner, gameTime)
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
        if (fact.equals(CompanionIntentIds.LOOSE_FOOD_AVAILABLE)) {
            return bool(snapshot.looseFoodAvailable());
        }
        if (fact.equals(CompanionIntentIds.HOME_DISTANCE)) {
            return snapshot.homeDistance();
        }
        // Owner facts answer for themselves, and NaN for anything that is
        // not one, which is the same answer this method gave before.
        return snapshot.owner().value(fact);
    }

    /**
     * How far she is from the middle of her home, or NaN when she has none.
     *
     * <p>Not zero: a maid without a home is not standing in the middle of one,
     * and a condition asking whether she has strayed should fail rather than
     * quietly hold.
     */
    private static double homeDistance(EntityMaid maid) {
        if (!maid.isHomeModeEnable()) {
            return Double.NaN;
        }
        BlockPos home = maid.getRestrictCenter();
        if (home == null || BlockPos.ZERO.equals(home)) {
            return Double.NaN;
        }
        return Math.sqrt(maid.distanceToSqr(
                home.getX() + 0.5D,
                home.getY() + 0.5D,
                home.getZ() + 0.5D
        ));
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
            int behaviorOccupancyReason,
            boolean looseFoodAvailable,
            double homeDistance,
            OwnerFacts owner
    ) {
    }
}
