package com.laixia.maidintelligence.gametest.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.application.DefaultMaidIntentOrchestrator;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentContext;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentObserver;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class IntentGameTestRuntime {
    private static final OrchestrationId GAZE_INTENT = id("test/gaze");
    private static final OrchestrationId HUNGER_INTENT = id("test/hunger");
    private static final OrchestrationId POST_TASK_INTENT =
            id("test/post_task");
    private static final OrchestrationId WANDER_INTENT = id("test/wander");
    private static final OrchestrationId APPROACH_PLAN = id("test/approach");
    private static final OrchestrationId REQUEST_PLAN = id("test/request");

    private IntentGameTestRuntime() {
    }

    public static Runtime create(
            MaidStatusApi<EntityMaid> status,
            Consumer<EntityMaid> requestAction,
            IntentDefinition... intents
    ) {
        MutableIntentCatalog catalog = new MutableIntentCatalog();
        catalog.publish(IntentCatalog.compile(
                1L,
                List.of(intents),
                List.of(approachPlan(), requestPlan()),
                CompanionIntentIds.vocabulary()
        ));
        TlmMaidIntentObserver observer = new TlmMaidIntentObserver();
        MaidIntentApi<EntityMaid> orchestrator =
                new DefaultMaidIntentOrchestrator<>(
                        catalog,
                        new TlmMaidIntentContext(status, observer),
                        new TlmMaidIntentActions(requestAction),
                        EntityMaid::getId,
                        () -> true,
                        () -> 1
                );
        observer.bind(orchestrator);
        return new Runtime(orchestrator, observer);
    }

    public static IntentDefinition gazeIntent() {
        return intent(
                GAZE_INTENT,
                APPROACH_PLAN,
                List.of(
                        condition(
                                CompanionIntentIds.GAZE_RECALL,
                                FactComparison.GREATER_OR_EQUAL,
                                1.0D
                        ),
                        condition(
                                CompanionIntentIds.FAVORABILITY,
                                FactComparison.GREATER_OR_EQUAL,
                                1.0D
                        )
                ),
                80
        );
    }

    public static IntentDefinition hungerIntent() {
        return intent(
                HUNGER_INTENT,
                REQUEST_PLAN,
                List.of(condition(
                        CompanionIntentIds.HUNGER,
                        FactComparison.LESS_OR_EQUAL,
                        40.0D
                )),
                40
        );
    }

    public static IntentDefinition postTaskIntent() {
        return intent(
                POST_TASK_INTENT,
                APPROACH_PLAN,
                List.of(
                        condition(
                                CompanionIntentIds.POST_TASK_RETURN,
                                FactComparison.GREATER_OR_EQUAL,
                                1.0D
                        ),
                        condition(
                                CompanionIntentIds.WORK_RELEASE_AGE,
                                FactComparison.GREATER_OR_EQUAL,
                                20.0D
                        ),
                        condition(
                                CompanionIntentIds.BUILT_IN_TASK,
                                FactComparison.EQUAL,
                                1.0D
                        )
                ),
                30
        );
    }

    public static IntentDefinition wanderIntent() {
        return intent(
                WANDER_INTENT,
                APPROACH_PLAN,
                List.of(
                        condition(
                                CompanionIntentIds.RANDOM_STROLL_RETURN,
                                FactComparison.GREATER_OR_EQUAL,
                                1.0D
                        ),
                        condition(
                                CompanionIntentIds.BUILT_IN_TASK,
                                FactComparison.EQUAL,
                                1.0D
                        )
                ),
                20
        );
    }

    private static IntentDefinition intent(
            OrchestrationId id,
            OrchestrationId plan,
            List<FactCondition> specificConditions,
            int priority
    ) {
        List<FactCondition> conditions = new java.util.ArrayList<>(
                specificConditions
        );
        conditions.addAll(List.of(
                condition(
                        CompanionIntentIds.OWNER_VALID,
                        FactComparison.EQUAL,
                        1.0D
                ),
                condition(
                        CompanionIntentIds.FOLLOW_MODE,
                        FactComparison.EQUAL,
                        1.0D
                ),
                condition(
                        CompanionIntentIds.CAN_MOVE,
                        FactComparison.EQUAL,
                        1.0D
                ),
                condition(
                        CompanionIntentIds.BEHAVIOR_OCCUPANCY_LEVEL,
                        FactComparison.EQUAL,
                        0.0D
                )
        ));
        return new IntentDefinition(
                id,
                plan,
                conditions,
                List.of(),
                1.0D,
                0.0D,
                1.0D,
                1,
                0,
                0.0D,
                priority,
                0
        );
    }

    private static PlanDefinition approachPlan() {
        return new PlanDefinition(
                APPROACH_PLAN,
                "approach",
                Map.of("approach", new PlanDefinition.State(
                        CompanionIntentIds.APPROACH_OWNER,
                        Map.of(
                                "speed",
                                "0.6",
                                "close_distance",
                                "2"
                        ),
                        200,
                        PlanDefinition.SUCCESS,
                        PlanDefinition.FAILURE
                ))
        );
    }

    private static PlanDefinition requestPlan() {
        Map<String, PlanDefinition.State> states = new LinkedHashMap<>();
        states.put("approach", new PlanDefinition.State(
                CompanionIntentIds.APPROACH_OWNER,
                Map.of(
                        "speed",
                        "0.55",
                        "close_distance",
                        "2"
                ),
                200,
                "request",
                PlanDefinition.FAILURE
        ));
        states.put("request", new PlanDefinition.State(
                CompanionIntentIds.REQUEST_HUNGER_ATTENTION,
                Map.of(),
                20,
                PlanDefinition.SUCCESS,
                PlanDefinition.FAILURE
        ));
        return new PlanDefinition(REQUEST_PLAN, "approach", states);
    }

    private static FactCondition condition(
            OrchestrationId fact,
            FactComparison comparison,
            double expected
    ) {
        return new FactCondition(fact, comparison, expected);
    }

    private static OrchestrationId id(String path) {
        return new OrchestrationId("tlm_companionship", path);
    }

    public record Runtime(
            MaidIntentApi<EntityMaid> intents,
            TlmMaidIntentObserver observer
    ) {
        public boolean tick(EntityMaid maid, long gameTime) {
            observer.observe(maid, gameTime);
            return intents.tick(maid, gameTime);
        }
    }
}
