package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityConsideration;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityCurve;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.*;

public final class IntentOrchestrationVerification {
    private IntentOrchestrationVerification() {
    }

    public static void main(String[] args) {
        utilityCurvesClampAtBoundaries();
        catalogRejectsInvalidGraphsAndReferences();
        deterministicTieUsesLexicalIntentId();
        IntentBudgetVerification.verify();
        commitmentAndHysteresisPreventThrashing();
        higherInterruptPriorityBreaksCommitment();
        cooldownAndTerminalTransitionsAreApplied();
        runningActionTimesOutIntoFailure();
        guardLossCancelsTheOwnedAction();
        signalIsOneShotAndWorksBeforeFirstTick();
        catalogGenerationCancelsOldRuntime();
        forgettingSubjectCancelsOldRuntime();
        cancelledActionUsesCancelTransition();
        multiStepPlanAdvancesDeterministically();
    }

    private static void utilityCurvesClampAtBoundaries() {
        UtilityConsideration linear = new UtilityConsideration(
                FACT_A,
                0.0D,
                10.0D,
                2.0D,
                UtilityCurve.LINEAR
        );
        require(close(linear.contribution(-1.0D), 0.0D),
                "Linear utility did not clamp below range");
        require(close(linear.contribution(5.0D), 1.0D),
                "Linear utility midpoint is wrong");
        require(close(linear.contribution(20.0D), 2.0D),
                "Linear utility did not clamp above range");
        UtilityConsideration inverse = new UtilityConsideration(
                FACT_A,
                0.0D,
                10.0D,
                1.0D,
                UtilityCurve.INVERSE_LINEAR
        );
        require(close(inverse.contribution(2.0D), 0.8D),
                "Inverse utility is wrong");
    }

    private static void catalogRejectsInvalidGraphsAndReferences() {
        PlanDefinition valid = plan(id("plan/valid"), WAIT, 20);
        IntentCatalog.compile(
                1L,
                List.of(definition(
                        id("intent/valid"),
                        valid.id(),
                        List.of(),
                        1.0D,
                        0,
                        0,
                        0.0D
                )),
                List.of(valid),
                VOCABULARY
        );

        expectFailure(() -> IntentCatalog.compile(
                1L,
                List.of(definition(
                        id("intent/unknown_fact"),
                        valid.id(),
                        List.of(condition(
                                id("fact/unknown"),
                                FactComparison.EQUAL,
                                1.0D
                        )),
                        1.0D,
                        0,
                        0,
                        0.0D
                )),
                List.of(valid),
                VOCABULARY
        ), "Unknown fact was accepted");

        Map<String, PlanDefinition.State> unreachable = new LinkedHashMap<>();
        unreachable.put("start", state(WAIT, PlanDefinition.SUCCESS,
                PlanDefinition.FAILURE, 20));
        unreachable.put("orphan", state(WAIT, PlanDefinition.SUCCESS,
                PlanDefinition.FAILURE, 20));
        expectFailure(() -> IntentCatalog.compile(
                1L,
                List.of(),
                List.of(new PlanDefinition(
                        id("plan/unreachable"),
                        "start",
                        unreachable
                )),
                VOCABULARY
        ), "Unreachable state was accepted");

        PlanDefinition loop = new PlanDefinition(
                id("plan/loop"),
                "loop",
                Map.of("loop", state(WAIT, "loop", "loop", 20))
        );
        expectFailure(() -> IntentCatalog.compile(
                1L,
                List.of(),
                List.of(loop),
                VOCABULARY
        ), "Plan with no terminal path was accepted");

        PlanDefinition unknownParameter = new PlanDefinition(
                id("plan/unknown_parameter"),
                "start",
                Map.of("start", new PlanDefinition.State(
                        WAIT,
                        Map.of("unexpected", "1"),
                        20,
                        PlanDefinition.SUCCESS,
                        PlanDefinition.FAILURE
                ))
        );
        expectFailure(() -> IntentCatalog.compile(
                1L,
                List.of(),
                List.of(unknownParameter),
                VOCABULARY
        ), "Unknown action parameter was accepted");

        IntentDefinition duplicate = definition(
                id("intent/duplicate"),
                valid.id(),
                List.of(),
                1.0D,
                0,
                0,
                0.0D
        );
        expectFailure(() -> IntentCatalog.compile(
                1L,
                List.of(duplicate, duplicate),
                List.of(valid),
                VOCABULARY
        ), "Duplicate intent id was accepted");
    }

    private static void deterministicTieUsesLexicalIntentId() {
        Fixture fixture = new Fixture();
        PlanDefinition plan = plan(id("plan/tie"), WAIT, 20);
        fixture.publish(
                1L,
                List.of(
                        definition(id("intent/b"), plan.id(), List.of(),
                                1.0D, 0, 0, 0.0D),
                        definition(id("intent/a"), plan.id(), List.of(),
                                1.0D, 0, 0, 0.0D)
                ),
                List.of(plan)
        );
        fixture.intents.tick("maid", 0L);
        require(id("intent/a").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "Equal-score selection was not deterministic");
    }

    private static void commitmentAndHysteresisPreventThrashing() {
        Fixture fixture = new Fixture();
        PlanDefinition plan = plan(id("plan/hysteresis"), WAIT, 100);
        IntentDefinition first = scored(
                id("intent/a"),
                plan.id(),
                FACT_A,
                20,
                10,
                0.2D
        );
        IntentDefinition second = scored(
                id("intent/b"),
                plan.id(),
                FACT_B,
                0,
                10,
                0.0D
        );
        fixture.publish(1L, List.of(first, second), List.of(plan));
        fixture.facts.put(FACT_A, 0.6D);
        fixture.facts.put(FACT_B, 0.5D);
        fixture.intents.tick("maid", 0L);
        fixture.facts.put(FACT_B, 0.75D);
        fixture.intents.tick("maid", 10L);
        require(id("intent/a").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "Commitment was broken by an ordinary candidate");
        fixture.facts.put(FACT_B, 0.9D);
        fixture.intents.tick("maid", 20L);
        require(id("intent/b").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "Hysteresis did not allow a materially better candidate");
    }

    private static void higherInterruptPriorityBreaksCommitment() {
        Fixture fixture = new Fixture();
        PlanDefinition plan = plan(id("plan/interrupt"), WAIT, 100);
        IntentDefinition normal = definition(
                id("intent/normal"),
                plan.id(),
                List.of(),
                1.0D,
                100,
                10,
                0.0D
        );
        IntentDefinition urgent = definition(
                id("intent/urgent"),
                plan.id(),
                List.of(condition(
                        FACT_B,
                        FactComparison.GREATER_OR_EQUAL,
                        1.0D
                )),
                0.5D,
                0,
                20,
                0.0D
        );
        fixture.publish(1L, List.of(normal, urgent), List.of(plan));
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 0L);
        fixture.facts.put(FACT_B, 1.0D);
        fixture.intents.tick("maid", 1L);
        require(id("intent/urgent").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "Hard interrupt priority did not preempt commitment");
    }

    private static void cooldownAndTerminalTransitionsAreApplied() {
        Fixture fixture = new Fixture();
        PlanDefinition plan = plan(id("plan/complete"), SUCCEED, 20);
        IntentDefinition intent = definition(
                id("intent/cooldown"),
                plan.id(),
                List.of(),
                1.0D,
                0,
                10,
                0.0D,
                10
        );
        fixture.publish(1L, List.of(intent), List.of(plan));
        fixture.intents.tick("maid", 0L);
        fixture.intents.tick("maid", 1L);
        require(fixture.intents.metrics().activations() == 1L,
                "Cooldown allowed an immediate reactivation");
        fixture.intents.tick("maid", 10L);
        require(fixture.intents.metrics().activations() == 2L,
                "Intent did not reactivate after cooldown");
    }

    private static void runningActionTimesOutIntoFailure() {
        Fixture fixture = new Fixture();
        PlanDefinition plan = plan(id("plan/timeout"), WAIT, 2);
        fixture.publish(
                1L,
                List.of(definition(
                        id("intent/timeout"),
                        plan.id(),
                        List.of(),
                        1.0D,
                        0,
                        0,
                        0.0D
                )),
                List.of(plan)
        );
        fixture.intents.tick("maid", 0L);
        fixture.intents.tick("maid", 1L);
        fixture.intents.tick("maid", 2L);
        require(fixture.intents.metrics().failures() == 1L,
                "Timed-out action did not enter failure terminal");
        require(fixture.actions.cancellations == 1,
                "Timed-out action did not release its owned side effect");
    }

    private static void guardLossCancelsTheOwnedAction() {
        Fixture fixture = new Fixture();
        PlanDefinition plan = plan(id("plan/cancel"), WAIT, 20);
        fixture.publish(
                1L,
                List.of(definition(
                        id("intent/cancel"),
                        plan.id(),
                        List.of(condition(
                                FACT_A,
                                FactComparison.EQUAL,
                                1.0D
                        )),
                        1.0D,
                        0,
                        0,
                        0.0D
                )),
                List.of(plan)
        );
        fixture.facts.put(FACT_A, 1.0D);
        fixture.intents.tick("maid", 0L);
        fixture.facts.put(FACT_A, 0.0D);
        fixture.intents.tick("maid", 1L);
        require(fixture.actions.cancellations == 1,
                "Guard loss did not cancel the running action");
        require(fixture.intents.metrics().cancellations() == 1L,
                "Cancellation metric was not updated");
    }

    private static void signalIsOneShotAndWorksBeforeFirstTick() {
        Fixture fixture = new Fixture();
        PlanDefinition plan = plan(id("plan/signal"), SUCCEED, 20);
        fixture.publish(
                1L,
                List.of(definition(
                        id("intent/signal"),
                        plan.id(),
                        List.of(condition(
                                SIGNAL,
                                FactComparison.EQUAL,
                                1.0D
                        )),
                        1.0D,
                        0,
                        0,
                        0.0D
                )),
                List.of(plan)
        );
        fixture.intents.signal("maid", SIGNAL, 0L, 20);
        fixture.intents.tick("maid", 0L);
        fixture.intents.tick("maid", 1L);
        require(fixture.intents.metrics().activations() == 1L,
                "Signal was dropped or consumed more than once");
    }

    private static void catalogGenerationCancelsOldRuntime() {
        Fixture fixture = new Fixture();
        PlanDefinition plan = plan(id("plan/reload"), WAIT, 100);
        IntentDefinition intent = definition(
                id("intent/reload"),
                plan.id(),
                List.of(),
                1.0D,
                0,
                0,
                0.0D
        );
        fixture.publish(1L, List.of(intent), List.of(plan));
        fixture.intents.tick("maid", 0L);
        fixture.publish(2L, List.of(intent), List.of(plan));
        fixture.intents.tick("maid", 1L);
        require(fixture.actions.cancellations == 1,
                "Catalog generation switch did not cancel old action");
    }

    private static void forgettingSubjectCancelsOldRuntime() {
        Fixture fixture = new Fixture();
        PlanDefinition plan = plan(id("plan/forget"), WAIT, 100);
        fixture.publish(
                1L,
                List.of(definition(
                        id("intent/forget"),
                        plan.id(),
                        List.of(),
                        1.0D,
                        0,
                        0,
                        0.0D
                )),
                List.of(plan)
        );
        fixture.intents.tick("maid", 0L);
        fixture.intents.forget("maid");
        require(fixture.actions.cancellations == 1,
                "Forgetting a subject did not cancel its owned action");
        require(fixture.intents.inspect("maid").activeIntent() == null,
                "Forgotten subject retained its runtime state");
    }

    private static void cancelledActionUsesCancelTransition() {
        Fixture fixture = new Fixture();
        Map<String, PlanDefinition.State> states = new LinkedHashMap<>();
        states.put("start", new PlanDefinition.State(
                CANCEL,
                Map.of(),
                20,
                PlanDefinition.SUCCESS,
                PlanDefinition.FAILURE,
                "cleanup"
        ));
        states.put("cleanup", state(
                SUCCEED,
                PlanDefinition.SUCCESS,
                PlanDefinition.FAILURE,
                20
        ));
        PlanDefinition plan = new PlanDefinition(
                id("plan/cancel_transition"),
                "start",
                states
        );
        fixture.publish(
                1L,
                List.of(definition(
                        id("intent/cancel_transition"),
                        plan.id(),
                        List.of(),
                        1.0D,
                        0,
                        0,
                        0.0D
                )),
                List.of(plan)
        );
        fixture.intents.tick("maid", 0L);
        require("cleanup".equals(
                        fixture.intents.inspect("maid").activeState()),
                "Cancelled action did not follow on_cancel transition");
        fixture.intents.tick("maid", 1L);
        require(fixture.intents.metrics().completions() == 1L,
                "Cancellation cleanup state did not complete");
    }

    private static void multiStepPlanAdvancesDeterministically() {
        Fixture fixture = new Fixture();
        Map<String, PlanDefinition.State> states = new LinkedHashMap<>();
        states.put("first", state(
                SUCCEED,
                "second",
                PlanDefinition.FAILURE,
                20
        ));
        states.put("second", state(
                SUCCEED,
                PlanDefinition.SUCCESS,
                PlanDefinition.FAILURE,
                20
        ));
        PlanDefinition plan = new PlanDefinition(
                id("plan/multi"),
                "first",
                states
        );
        fixture.publish(
                1L,
                List.of(definition(
                        id("intent/multi"),
                        plan.id(),
                        List.of(),
                        1.0D,
                        0,
                        0,
                        0.0D
                )),
                List.of(plan)
        );
        fixture.intents.tick("maid", 0L);
        require("second".equals(
                        fixture.intents.inspect("maid").activeState()),
                "Successful step did not advance state");
        fixture.intents.tick("maid", 1L);
        require(fixture.intents.metrics().completions() == 1L,
                "Multi-step plan did not complete");
    }

    private static final class Fixture extends IntentVerificationFixture {
    }
}
