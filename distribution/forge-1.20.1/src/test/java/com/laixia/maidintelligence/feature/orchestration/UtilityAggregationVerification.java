package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.application.DefaultMaidIntentOrchestrator;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityAggregation;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityConsideration;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityCurve;
import com.laixia.maidintelligence.feature.orchestration.port.IntentActionPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.FACT_A;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.FACT_B;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.VOCABULARY;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.WAIT;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.close;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.expectFailure;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.id;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.plan;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.require;

/**
 * Product aggregation, its compensation, and the early exit it enables.
 *
 * <p>The early exit is the part that needs an end-to-end check rather than an
 * arithmetic one. It returns an upper bound instead of a score, so the claim
 * being tested is not "the number is right" but "the maid does the same thing
 * either way" — and the shared fixture cannot show that, because it hardcodes
 * diagnostics on and therefore never takes the pruned path at all.
 */
public final class UtilityAggregationVerification {
    private static final OrchestrationId PLAN = id("plan/wait");
    private static final int TICKS = 400;
    /** Score is the only tie-break available, so the bound is load bearing. */
    private static final int[] FLAT_BAND = {10, 10, 10, 10, 10, 10};
    /** Exercises the priority skip, which the flat band cannot reach. */
    private static final int[] MIXED_BANDS = {10, 10, 10, 20, 20, 30};

    private UtilityAggregationVerification() {
    }

    public static void main(String[] args) {
        weightInterpolatesTowardsNeutral();
        unreadableFactUsesEachAggregationIdentity();
        factorStaysInsideTheUnitInterval();
        compensationIsAnchoredAndMonotonic();
        productScoreStaysBounded();
        oneZeroFactorVetoesTheIntent();
        productRejectsInputsItCannotBound();
        legacyConstructionStaysSummed();
        pruningDoesNotChangeBehaviour();
    }

    /**
     * Weight cannot scale a factor the way it scales a summed term, so it
     * interpolates towards one instead. Full weight keeps the veto; zero
     * weight makes the consideration inert rather than fatal.
     */
    private static void weightInterpolatesTowardsNeutral() {
        require(close(consideration(1.0D).factor(0.0D), 0.0D),
                "Full weight lost the veto at the bottom of the range");
        require(close(consideration(1.0D).factor(1.0D), 1.0D),
                "Full weight altered a fully satisfied consideration");
        require(close(consideration(0.0D).factor(0.0D), 1.0D),
                "Zero weight did not make the consideration inert");
        require(close(consideration(0.5D).factor(0.0D), 0.5D),
                "Half weight did not land halfway to neutral");
        require(close(consideration(0.25D).factor(0.0D), 0.75D),
                "Quarter weight did not land a quarter of the way down");
    }

    /**
     * The fact reader publishes NaN for a fact it cannot supply. Each mode
     * absorbs that into its own identity, so a broken reader degrades one
     * consideration instead of silently disabling every intent using it.
     */
    private static void unreadableFactUsesEachAggregationIdentity() {
        UtilityConsideration rising = consideration(1.0D);
        require(close(rising.contribution(Double.NaN), 0.0D),
                "Unreadable fact stopped being neutral for a sum");
        require(close(rising.factor(Double.NaN), 1.0D),
                "Unreadable fact was not neutral for a product");

        // The inverse curve is the case that would break if `contribution`
        // ever routed NaN through the curve instead of returning early.
        UtilityConsideration falling = new UtilityConsideration(
                FACT_A,
                0.0D,
                1.0D,
                1.0D,
                UtilityCurve.INVERSE_LINEAR
        );
        require(close(falling.contribution(Double.NaN), 0.0D),
                "Unreadable fact leaked through an inverse curve");
    }

    private static void factorStaysInsideTheUnitInterval() {
        double[] weights = {0.0D, 0.25D, 0.5D, 0.75D, 1.0D};
        for (UtilityCurve curve : UtilityCurve.values()) {
            for (double weight : weights) {
                UtilityConsideration subject = new UtilityConsideration(
                        FACT_A,
                        0.0D,
                        1.0D,
                        weight,
                        curve
                );
                for (int sample = 0; sample <= 100; sample++) {
                    double value = subject.factor(sample / 100.0D);
                    require(value >= 0.0D && value <= 1.0D,
                            "Factor left the unit interval for " + curve
                                    + " at weight " + weight);
                }
            }
        }
    }

    private static void compensationIsAnchoredAndMonotonic() {
        for (int count = 1; count <= 8; count++) {
            require(close(
                            UtilityAggregation.PRODUCT.finish(0.0D, count),
                            0.0D),
                    "Compensation lifted a vetoed product off zero");
            require(close(
                            UtilityAggregation.PRODUCT.finish(1.0D, count),
                            1.0D),
                    "Compensation pushed a perfect product past one");
            double previous = -1.0D;
            for (int sample = 0; sample <= 100; sample++) {
                double raw = sample / 100.0D;
                double finished =
                        UtilityAggregation.PRODUCT.finish(raw, count);
                require(finished >= raw - 1.0E-12D,
                        "Compensation reduced the product at " + raw);
                require(finished >= previous - 1.0E-12D,
                        "Compensation is not monotonic at " + raw);
                require(finished >= 0.0D && finished <= 1.0D,
                        "Compensated score left the unit interval at " + raw);
                previous = finished;
            }
        }
        require(close(UtilityAggregation.SUM.finish(3.5D, 4), 3.5D),
                "Sum aggregation must not compensate");
    }

    private static void productScoreStaysBounded() {
        double accumulated = 1.0D;
        List<UtilityConsideration> considerations = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            UtilityConsideration subject = consideration(1.0D);
            considerations.add(subject);
            accumulated = UtilityAggregation.PRODUCT.combine(
                    accumulated,
                    UtilityAggregation.PRODUCT.term(subject, 0.7D)
            );
        }
        double finished = UtilityAggregation.PRODUCT.finish(
                accumulated,
                considerations.size()
        );
        require(finished >= 0.0D && finished <= 1.0D,
                "Six considerations pushed the product out of range");
    }

    private static void oneZeroFactorVetoesTheIntent() {
        double accumulated = 1.0D;
        accumulated = UtilityAggregation.PRODUCT.combine(
                accumulated,
                UtilityAggregation.PRODUCT.term(consideration(1.0D), 1.0D)
        );
        accumulated = UtilityAggregation.PRODUCT.combine(
                accumulated,
                UtilityAggregation.PRODUCT.term(consideration(1.0D), 0.0D)
        );
        accumulated = UtilityAggregation.PRODUCT.combine(
                accumulated,
                UtilityAggregation.PRODUCT.term(consideration(1.0D), 1.0D)
        );
        require(close(UtilityAggregation.PRODUCT.finish(accumulated, 3), 0.0D),
                "A zero factor failed to veto the intent");

        // The same three terms under a sum are merely out-voted, which is the
        // behaviour product aggregation exists to replace.
        double summed = UtilityAggregation.SUM.term(consideration(1.0D), 1.0D)
                + UtilityAggregation.SUM.term(consideration(1.0D), 0.0D)
                + UtilityAggregation.SUM.term(consideration(1.0D), 1.0D);
        require(summed > 0.0D,
                "Sum aggregation unexpectedly vetoed on a zero term");
    }

    private static void productRejectsInputsItCannotBound() {
        expectFailure(
                () -> productIntent(1.5D, List.of(consideration(1.0D))),
                "Product intent accepted a base score above one"
        );
        expectFailure(
                () -> productIntent(0.5D, List.of(consideration(2.0D))),
                "Product intent accepted a weight above one"
        );
        expectFailure(
                () -> productIntent(0.5D, List.of(consideration(-0.1D))),
                "Product intent accepted a negative weight"
        );
        // A summed intent keeps both freedoms, because that is what a negative
        // weight and an unbounded base score mean there.
        IntentDefinition summed = new IntentDefinition(
                id("intent/summed"),
                PLAN,
                List.of(),
                List.of(consideration(-2.0D)),
                7.5D,
                0.0D,
                1.0D,
                1,
                0,
                0.0D,
                0,
                0
        );
        require(summed.aggregation() == UtilityAggregation.SUM,
                "Legacy construction did not default to sum aggregation");
    }

    private static void legacyConstructionStaysSummed() {
        IntentDefinition legacy = new IntentDefinition(
                id("intent/legacy"),
                PLAN,
                List.of(),
                List.of(consideration(1.0D)),
                0.25D,
                0.0D,
                1.0D,
                1,
                0,
                0.0D,
                0,
                0
        );
        require(legacy.aggregation() == UtilityAggregation.SUM,
                "Legacy intents must stay summed");
        require(close(
                        UtilityAggregation.SUM.term(consideration(1.0D), 0.5D),
                        0.5D),
                "Sum term stopped matching the original contribution");
    }

    /**
     * The load-bearing claim of the early exit: with diagnostics off the engine
     * stops scoring intents it can prove cannot win, and the maid still runs
     * exactly the same actions in exactly the same order as an unpruned pass.
     */
    private static void pruningDoesNotChangeBehaviour() {
        /*
         * Two scenarios, because the two early exits are independent and a
         * mixed-band catalog hides the score one: once the top band owns the
         * incumbent, everything below it is skipped on priority alone and the
         * score comparison never decides anything.
         *
         * The flat band therefore runs with no commitment and no switch margin,
         * so the winner is decided purely by score every single tick and an
         * over-eager bound shows up immediately.
         */
        assertEquivalent(FLAT_BAND, 0, 0.0D, "flat band");
        assertEquivalent(MIXED_BANDS, 3, 0.05D, "mixed bands");
    }

    private static void assertEquivalent(
            int[] priorities,
            int commitment,
            double margin,
            String label
    ) {
        List<String> unpruned = run(true, priorities, commitment, margin);
        List<String> pruned = run(false, priorities, commitment, margin);
        for (int index = 0; index < unpruned.size(); index++) {
            require(unpruned.get(index).equals(pruned.get(index)),
                    "Pruning changed the selection in " + label
                            + " at entry " + index
                            + ": unpruned=" + unpruned.get(index)
                            + " pruned=" + pruned.get(index));
        }
        require(unpruned.size() == pruned.size(),
                "Pruning changed how many decisions were taken in " + label);

        /*
         * An equivalence test over a scenario that only ever picks one intent
         * would pass without exercising anything, so the scenario has to be
         * shown to move first.
         */
        long distinct = unpruned.stream()
                .map(entry -> entry.substring(entry.indexOf('=') + 1))
                .distinct()
                .count();
        require(distinct >= 3L,
                "Scenario " + label + " only reached " + distinct
                        + " distinct selections; it cannot detect divergence");
    }

    private static List<String> run(
            boolean diagnostics,
            int[] priorities,
            int commitment,
            double margin
    ) {
        MutableIntentCatalog catalog = new MutableIntentCatalog();
        Map<OrchestrationId, Double> facts = new HashMap<>();
        RecordingActions actions = new RecordingActions();
        MaidIntentApi<String> orchestrator =
                new DefaultMaidIntentOrchestrator<>(
                        catalog,
                        (subject, gameTime, requested, output) -> {
                            for (int index = 0;
                                 index < requested.size();
                                 index++) {
                                output[index] = facts.getOrDefault(
                                        requested.get(index),
                                        Double.NaN
                                );
                            }
                        },
                        actions,
                        String::hashCode,
                        () -> true,
                        () -> 1,
                        () -> 128,
                        () -> diagnostics
                );
        catalog.publish(IntentCatalog.compile(
                1L,
                scenarioIntents(priorities, commitment, margin),
                List.of(plan(PLAN, WAIT, 12)),
                VOCABULARY
        ));
        List<String> selected = new ArrayList<>();
        for (int tick = 0; tick < TICKS; tick++) {
            // Deterministic but uneven, so intents keep trading places across
            // and within priority bands instead of one winning permanently.
            facts.put(FACT_A, (tick % 17) / 16.0D);
            facts.put(FACT_B, (tick % 23) / 22.0D);
            orchestrator.tick("maid", tick);
            /*
             * The winning intent is the signal, not the executed action: every
             * intent in this scenario runs the same plan, so an action log
             * would match even if selection had diverged completely.
             */
            selected.add(
                    tick + "=" + orchestrator.inspect("maid").activeIntent()
            );
        }
        selected.add("executions=" + actions.log.size());
        return selected;
    }

    private static List<IntentDefinition> scenarioIntents(
            int[] priorities,
            int commitment,
            double margin
    ) {
        List<IntentDefinition> intents = new ArrayList<>();
        for (int index = 0; index < priorities.length; index++) {
            OrchestrationId fact = index % 2 == 0 ? FACT_A : FACT_B;
            UtilityCurve curve = index % 3 == 0
                    ? UtilityCurve.LOGISTIC
                    : UtilityCurve.LINEAR;
            intents.add(new IntentDefinition(
                    id("intent/candidate_" + index),
                    PLAN,
                    List.of(),
                    List.of(
                            new UtilityConsideration(
                                    fact,
                                    0.0D,
                                    1.0D,
                                    1.0D,
                                    curve
                            ),
                            new UtilityConsideration(
                                    index % 2 == 0 ? FACT_B : FACT_A,
                                    0.0D,
                                    1.0D,
                                    0.5D + index * 0.05D,
                                    UtilityCurve.INVERSE_LINEAR
                            )
                    ),
                    0.9D,
                    0.0D,
                    1.0D,
                    1,
                    commitment,
                    margin,
                    priorities[index],
                    0,
                    UtilityAggregation.PRODUCT
            ));
        }
        return intents;
    }

    private static IntentDefinition productIntent(
            double baseScore,
            List<UtilityConsideration> considerations
    ) {
        return new IntentDefinition(
                id("intent/product"),
                PLAN,
                List.of(),
                considerations,
                baseScore,
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

    private static UtilityConsideration consideration(double weight) {
        return new UtilityConsideration(
                FACT_A,
                0.0D,
                1.0D,
                weight,
                UtilityCurve.LINEAR
        );
    }

    private static final class RecordingActions
            implements IntentActionPort<String> {
        private final List<String> log = new ArrayList<>();

        @Override
        public ActionResult execute(
                String subject,
                OrchestrationId action,
                Map<String, String> parameters,
                long gameTime,
                int elapsedTicks
        ) {
            log.add(gameTime + ":run:" + action);
            return ActionResult.RUNNING;
        }

        @Override
        public void cancel(
                String subject,
                OrchestrationId action,
                Map<String, String> parameters
        ) {
            log.add("cancel:" + action);
        }
    }
}
