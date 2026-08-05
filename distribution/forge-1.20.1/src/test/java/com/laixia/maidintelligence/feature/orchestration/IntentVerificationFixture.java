package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.application.DefaultMaidIntentOrchestrator;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentVocabulary;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityConsideration;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityCurve;
import com.laixia.maidintelligence.feature.orchestration.port.IntentActionPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class IntentVerificationFixture {
    static final OrchestrationId FACT_A = id("fact/a");
    static final OrchestrationId FACT_B = id("fact/b");
    static final OrchestrationId SIGNAL = id("signal/event");
    static final OrchestrationId WAIT = id("action/wait");
    static final OrchestrationId SUCCEED = id("action/succeed");
    static final OrchestrationId FAIL = id("action/fail");
    static final OrchestrationId CANCEL = id("action/cancel");
    static final IntentVocabulary VOCABULARY =
            new IntentVocabulary(
                    Set.of(FACT_A, FACT_B, SIGNAL),
                    Set.of(WAIT, SUCCEED, FAIL, CANCEL),
                    Set.of(SIGNAL)
            );

    final MutableIntentCatalog catalog = new MutableIntentCatalog();
    final Map<OrchestrationId, Double> facts = new HashMap<>();
    final FakeActions actions = new FakeActions();
    final MaidIntentApi<String> intents;

    IntentVerificationFixture() {
        this(128);
    }

    IntentVerificationFixture(int maxCandidateEvaluations) {
        intents = new DefaultMaidIntentOrchestrator<>(
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
                    () -> maxCandidateEvaluations,
                    () -> true
            );
    }

    void publish(
            long generation,
            List<IntentDefinition> definitions,
            List<PlanDefinition> plans
    ) {
        catalog.publish(IntentCatalog.compile(
                generation,
                definitions,
                plans,
                VOCABULARY
        ));
    }

    static IntentDefinition scored(
            OrchestrationId id,
            OrchestrationId plan,
            OrchestrationId fact,
            int commitment,
            int priority,
            double margin
    ) {
        return new IntentDefinition(
                id,
                plan,
                List.of(),
                List.of(new UtilityConsideration(
                        fact,
                        0.0D,
                        1.0D,
                        1.0D,
                        UtilityCurve.LINEAR
                )),
                0.0D,
                0.0D,
                1.0D,
                1,
                commitment,
                margin,
                priority,
                0
        );
    }

    static IntentDefinition definition(
            OrchestrationId id,
            OrchestrationId plan,
            List<FactCondition> conditions,
            double score,
            int commitment,
            int priority,
            double margin
    ) {
        return definition(
                id,
                plan,
                conditions,
                score,
                commitment,
                priority,
                margin,
                0
        );
    }

    static IntentDefinition definition(
            OrchestrationId id,
            OrchestrationId plan,
            List<FactCondition> conditions,
            double score,
            int commitment,
            int priority,
            double margin,
            int cooldown
    ) {
        return new IntentDefinition(
                id,
                plan,
                conditions,
                List.of(),
                score,
                0.0D,
                1.0D,
                1,
                commitment,
                margin,
                priority,
                cooldown
        );
    }

    static PlanDefinition plan(
            OrchestrationId id,
            OrchestrationId action,
            int timeout
    ) {
        return new PlanDefinition(
                id,
                "start",
                Map.of("start", state(
                        action,
                        PlanDefinition.SUCCESS,
                        PlanDefinition.FAILURE,
                        timeout
                ))
        );
    }

    static PlanDefinition.State state(
            OrchestrationId action,
            String success,
            String failure,
            int timeout
    ) {
        return new PlanDefinition.State(
                action,
                Map.of(),
                timeout,
                success,
                failure
        );
    }

    static FactCondition condition(
            OrchestrationId fact,
            FactComparison comparison,
            double expected
    ) {
        return new FactCondition(fact, comparison, expected);
    }

    static OrchestrationId id(String path) {
        return new OrchestrationId("test", path);
    }

    static void expectFailure(Runnable operation, String message) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    static boolean close(double left, double right) {
        return Math.abs(left - right) < 1.0E-9D;
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static final class FakeActions implements IntentActionPort<String> {
        int cancellations;
        int executions;

        @Override
        public ActionResult execute(
                String subject,
                OrchestrationId action,
                Map<String, String> parameters,
                long gameTime,
                int elapsedTicks
        ) {
            executions++;
            if (action.equals(SUCCEED)) {
                return ActionResult.SUCCEEDED;
            }
            if (action.equals(FAIL)) {
                return ActionResult.FAILED;
            }
            if (action.equals(CANCEL)) {
                return ActionResult.CANCELLED;
            }
            return ActionResult.RUNNING;
        }

        @Override
        public void cancel(
                String subject,
                OrchestrationId action,
                Map<String, String> parameters
        ) {
            cancellations++;
        }
    }
}
