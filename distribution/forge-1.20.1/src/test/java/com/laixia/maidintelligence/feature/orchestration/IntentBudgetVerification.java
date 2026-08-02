package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;

import java.util.List;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.*;

final class IntentBudgetVerification {
    private IntentBudgetVerification() {
    }

    static void verify() {
        candidateBudgetRotatesWithoutStarvation();
        failedActivationConsumesOneShotSignal();
        consumedSignalDoesNotCancelRunningPlan();
    }

    private static void candidateBudgetRotatesWithoutStarvation() {
        IntentVerificationFixture fixture =
                new IntentVerificationFixture(1);
        PlanDefinition plan = plan(id("plan/budget"), WAIT, 100);
        FactCondition blocked = condition(
                FACT_A,
                FactComparison.EQUAL,
                1.0D
        );
        fixture.publish(
                1L,
                List.of(
                        definition(id("intent/a"), plan.id(),
                                List.of(blocked), 1.0D, 0, 0, 0.0D),
                        definition(id("intent/b"), plan.id(),
                                List.of(blocked), 1.0D, 0, 0, 0.0D),
                        definition(id("intent/c"), plan.id(),
                                List.of(), 1.0D, 0, 0, 0.0D)
                ),
                List.of(plan)
        );
        fixture.facts.put(FACT_A, 0.0D);
        fixture.intents.tick("maid", 0L);
        fixture.intents.tick("maid", 1L);
        fixture.intents.tick("maid", 2L);
        require(id("intent/c").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "Candidate budget permanently starved a later intent");
    }

    private static void failedActivationConsumesOneShotSignal() {
        IntentVerificationFixture fixture =
                new IntentVerificationFixture();
        PlanDefinition plan = plan(id("plan/chance"), SUCCEED, 20);
        IntentDefinition intent = new IntentDefinition(
                id("intent/chance"),
                plan.id(),
                List.of(condition(
                        SIGNAL,
                        FactComparison.EQUAL,
                        1.0D
                )),
                List.of(),
                1.0D,
                0.0D,
                0.0D,
                1,
                0,
                0.0D,
                0,
                0
        );
        fixture.publish(1L, List.of(intent), List.of(plan));
        fixture.intents.signal("maid", SIGNAL, 0L, 20);
        fixture.intents.tick("maid", 0L);
        fixture.intents.tick("maid", 1L);
        require(fixture.intents.metrics().activations() == 0L,
                "Zero activation chance unexpectedly started an intent");
        require("no_candidate".equals(
                        fixture.intents.inspect("maid").lastTransition()),
                "Failed activation did not consume its one-shot signal");
    }

    private static void consumedSignalDoesNotCancelRunningPlan() {
        IntentVerificationFixture fixture =
                new IntentVerificationFixture();
        PlanDefinition plan = plan(id("plan/signal_running"), WAIT, 20);
        OrchestrationId intentId = id("intent/signal_running");
        fixture.publish(
                1L,
                List.of(definition(
                        intentId,
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

        require(intentId.equals(
                        fixture.intents.inspect("maid").activeIntent()
                ),
                "Consumed activation signal cancelled the running plan");
        require(fixture.actions.executions == 2,
                "Signal-gated plan did not advance after activation");
    }
}
