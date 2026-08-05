package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.application.forecast.OwnerActivityTracker;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.domain.forecast.CompanionActivity;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.application.DefaultMaidIntentOrchestrator;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityAggregation;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityConsideration;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityCurve;
import com.laixia.maidintelligence.feature.orchestration.port.IntentActionPort;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The whole anticipation path in one place: a routine is observed, it becomes a
 * fact, and an intent written against that fact starts firing — but only once
 * the routine is actually established.
 *
 * <p>This exists because the statistics passing in isolation says nothing about
 * whether a data pack can reach them. It also pins the numbers: the threshold a
 * pack has to write, and how many repetitions clear it. Those are the two
 * things a "she anticipates you" feature lives or dies on, and both were
 * guesses until they were measured here.
 */
public final class ForecastDrivenIntentVerification {
    private static final OrchestrationId INTENT = id("intent/prepare_ahead");
    private static final OrchestrationId PLAN = id("plan/prepare_ahead");

    /**
     * Chosen from the measured curve rather than picked as a round number. The
     * flat prior over seven activities is {@code 0.143}; this is roughly
     * {@code 1.75x} that, which the routine clears on its third repetition and
     * an unrelated owner never reaches.
     */
    private static final double TRIGGER = 0.25D;

    private static final UUID OWNER = UUID.nameUUIDFromBytes(
            "routine-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    );

    private ForecastDrivenIntentVerification() {
    }

    public static void main(String[] args) {
        aColdOwnerNeverTriggers();
        anEstablishedRoutineTriggers();
        theWrongContextDoesNotTrigger();
        aChangedRoutineStopsTriggering();
        reportSensitivity();
    }

    /**
     * The failure that would matter most in a real world: a maid that starts
     * anticipating before it has grounds to.
     */
    private static void aColdOwnerNeverTriggers() {
        Harness harness = new Harness();
        require(!harness.activatesAfter(0),
                "An intent fired with no observations at all");
        require(!harness.activatesAfter(1),
                "One repetition was enough to trigger anticipation");
    }

    private static void anEstablishedRoutineTriggers() {
        require(new Harness().activatesAfter(3),
                "Three repetitions did not reach the trigger threshold");
        require(new Harness().activatesAfter(10),
                "A thoroughly learned routine did not trigger");
    }

    /**
     * The prediction has to be about the context, not about the activity being
     * common. Sitting in a context that never preceded building must not
     * inherit building's popularity.
     */
    private static void theWrongContextDoesNotTrigger() {
        Harness harness = new Harness();
        harness.learnRoutine(10);
        // Land in FARMING -> RESTING, a context the routine never visited.
        harness.tracker.observe(OWNER, CompanionActivity.FARMING, 9_000L);
        harness.tracker.observe(OWNER, CompanionActivity.RESTING, 9_020L);
        require(!harness.run(9_020L),
                "A context that never preceded the routine still triggered");
    }

    /**
     * Forgetting is what lets a maid stop acting on a habit the owner dropped.
     */
    private static void aChangedRoutineStopsTriggering() {
        Harness harness = new Harness();
        harness.learnRoutine(40);
        require(harness.armAndRun(),
                "The learned routine did not trigger before the change");
        for (int repeat = 0; repeat < 400; repeat++) {
            harness.tracker.observe(OWNER, CompanionActivity.MINING, 0L);
            harness.tracker.observe(OWNER, CompanionActivity.TRAVELLING, 20L);
            harness.tracker.observe(OWNER, CompanionActivity.COMBAT, 40L);
        }
        require(!harness.armAndRun(),
                "The maid kept anticipating a routine the owner abandoned");
    }

    /**
     * Not an assertion — the measured curve, printed so the threshold above can
     * be re-derived rather than trusted when the blend is retuned.
     */
    private static void reportSensitivity() {
        StringBuilder report = new StringBuilder(
                "forecast sensitivity (trigger " + TRIGGER + "): "
        );
        for (int repeats : new int[]{1, 2, 3, 5, 10, 20}) {
            Harness harness = new Harness();
            harness.learnRoutine(repeats);
            harness.arm();
            report.append(repeats)
                    .append("x=")
                    .append(String.format(
                            "%.3f",
                            harness.forecast()
                    ))
                    .append(harness.forecast() >= TRIGGER ? "* " : " ");
        }
        System.out.println(report);
    }

    private static final class Harness {
        private final OwnerActivityTracker tracker = new OwnerActivityTracker();
        private final MutableIntentCatalog catalog = new MutableIntentCatalog();
        private final MaidIntentApi<String> orchestrator;
        private long clock;

        private Harness() {
            orchestrator = new DefaultMaidIntentOrchestrator<>(
                    catalog,
                    (subject, gameTime, requested, output) -> {
                        for (int index = 0; index < requested.size(); index++) {
                            output[index] = fact(
                                    requested.get(index),
                                    gameTime
                            );
                        }
                    },
                    (subject, action, parameters, gameTime, elapsed) ->
                            ActionResult.RUNNING,
                    String::hashCode,
                    () -> true,
                    () -> 1,
                    () -> 64,
                    () -> true
            );
            catalog.publish(IntentCatalog.compile(
                    1L,
                    List.of(anticipationIntent()),
                    List.of(waitPlan()),
                    CompanionIntentIds.vocabulary()
            ));
        }

        private double fact(OrchestrationId requested, long gameTime) {
            if (requested.equals(CompanionIntentIds.OWNER_VALID)) {
                return 1.0D;
            }
            for (CompanionActivity activity : CompanionActivity.values()) {
                if (requested.equals(activity.forecastFact())) {
                    return tracker.probability(OWNER, activity, gameTime);
                }
            }
            return 0.0D;
        }

        private void learnRoutine(int repeats) {
            for (int repeat = 0; repeat < repeats; repeat++) {
                tracker.observe(OWNER, CompanionActivity.MINING, clock);
                tracker.observe(
                        OWNER,
                        CompanionActivity.TRAVELLING,
                        clock + 20L
                );
                tracker.observe(
                        OWNER,
                        CompanionActivity.BUILDING,
                        clock + 40L
                );
                clock += 60L;
            }
        }

        /** Leaves the owner mid-routine, where the prediction is asked. */
        private void arm() {
            tracker.observe(OWNER, CompanionActivity.MINING, clock);
            tracker.observe(OWNER, CompanionActivity.TRAVELLING, clock + 20L);
            clock += 40L;
        }

        private double forecast() {
            return tracker.probability(
                    OWNER,
                    CompanionActivity.BUILDING,
                    clock
            );
        }

        private boolean activatesAfter(int repeats) {
            learnRoutine(repeats);
            return armAndRun();
        }

        private boolean armAndRun() {
            arm();
            return run(clock);
        }

        private boolean run(long gameTime) {
            orchestrator.tick("maid", gameTime);
            return INTENT.equals(
                    orchestrator.inspect("maid").activeIntent()
            );
        }
    }

    /**
     * What a data pack would author: fire when the owner is predicted to head
     * for the next stage of their routine, and score on how sure that is.
     */
    private static IntentDefinition anticipationIntent() {
        OrchestrationId forecast =
                CompanionActivity.BUILDING.forecastFact();
        return new IntentDefinition(
                INTENT,
                PLAN,
                List.of(
                        new FactCondition(
                                CompanionIntentIds.OWNER_VALID,
                                FactComparison.EQUAL,
                                1.0D
                        ),
                        new FactCondition(
                                forecast,
                                FactComparison.GREATER_OR_EQUAL,
                                TRIGGER
                        )
                ),
                List.of(new UtilityConsideration(
                        forecast,
                        TRIGGER,
                        1.0D,
                        1.0D,
                        UtilityCurve.LOGISTIC
                )),
                1.0D,
                0.0D,
                1.0D,
                1,
                0,
                0.0D,
                0,
                0,
                UtilityAggregation.PRODUCT
        );
    }

    private static PlanDefinition waitPlan() {
        return new PlanDefinition(PLAN, "start", Map.of(
                "start",
                new PlanDefinition.State(
                        CompanionIntentIds.APPROACH_OWNER,
                        Map.of(),
                        200,
                        PlanDefinition.SUCCESS,
                        PlanDefinition.FAILURE
                )
        ));
    }

    private static OrchestrationId id(String path) {
        return new OrchestrationId("tlm_companionship", path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
