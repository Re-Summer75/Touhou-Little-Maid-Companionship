package com.laixia.maidintelligence.feature.orchestration.data;

import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentVocabulary;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskMethod;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskStep;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The reload-side half of decomposition: that a task-shaped intent is gone by
 * the time anything is published, and that what replaced it is data the catalog
 * accepts.
 *
 * <p>Runs against the real {@link CompanionIntentIds#vocabulary()} rather than
 * a test one, because the failure this guards against is generated data that is
 * well-formed in the abstract but references an action the mod does not
 * actually register.
 */
public final class TaskIntentExpansionVerification {
    private static final IntentVocabulary VOCABULARY =
            CompanionIntentIds.vocabulary();
    private static final OrchestrationId TASK = id("task/obtain_food");
    private static final OrchestrationId TEMPLATE = id("intent/obtain_food");
    private static final OrchestrationId PLAIN = id("intent/plain");
    private static final OrchestrationId PLAIN_PLAN = id("plan/plain");

    private TaskIntentExpansionVerification() {
    }

    public static void main(String[] args) {
        taskIntentsAreReplacedByTheirVariants();
        ordinaryIntentsAreLeftAlone();
        expandedOutputCompilesAgainstTheRealVocabulary();
        validationAcceptsTaskIntentsAndRejectsUnknownPlans();
        noTasksMeansNoChange();
    }

    private static void taskIntentsAreReplacedByTheirVariants() {
        Map<OrchestrationId, IntentDefinition> intents = new LinkedHashMap<>();
        intents.put(TEMPLATE, template());
        Map<OrchestrationId, PlanDefinition> plans = new LinkedHashMap<>();
        int decomposed = TaskIntentExpansion.expand(intents, plans, tasks());

        require(decomposed == 1, "Expected one decomposed intent");
        require(!intents.containsKey(TEMPLATE),
                "The task-shaped intent survived expansion; the catalog would "
                        + "later reject it for naming a plan that is a task");
        require(intents.size() == 2,
                "Two methods should have produced two intents, got "
                        + intents.size());
        require(plans.size() == 2,
                "Each generated intent needs its own plan");
        for (OrchestrationId generated : intents.keySet()) {
            require(generated.path().startsWith(TEMPLATE.path() + "/m"),
                    "Generated id " + generated + " is not derived from the "
                            + "template");
            require(plans.containsKey(
                            intents.get(generated).plan()),
                    "Generated intent " + generated
                            + " points at a plan that was not emitted");
        }
    }

    private static void ordinaryIntentsAreLeftAlone() {
        Map<OrchestrationId, IntentDefinition> intents = new LinkedHashMap<>();
        intents.put(PLAIN, plain());
        intents.put(TEMPLATE, template());
        Map<OrchestrationId, PlanDefinition> plans = new LinkedHashMap<>();
        plans.put(PLAIN_PLAN, plan(PLAIN_PLAN));
        TaskIntentExpansion.expand(intents, plans, tasks());

        require(intents.containsKey(PLAIN),
                "An intent naming a real plan was rewritten");
        require(intents.get(PLAIN) == plain()
                        || intents.get(PLAIN).plan().equals(PLAIN_PLAN),
                "An untouched intent lost its plan");
        require(plans.get(PLAIN_PLAN) != null,
                "An existing plan was dropped");
    }

    private static void expandedOutputCompilesAgainstTheRealVocabulary() {
        Map<OrchestrationId, IntentDefinition> intents = new LinkedHashMap<>();
        intents.put(TEMPLATE, template());
        Map<OrchestrationId, PlanDefinition> plans = new LinkedHashMap<>();
        TaskIntentExpansion.expand(intents, plans, tasks());
        IntentCatalog catalog = IntentCatalog.compile(
                11L,
                intents.values(),
                plans.values(),
                VOCABULARY
        );
        require(catalog.intents().size() == 2,
                "The catalog rejected part of the generated data");
        require(catalog.generation() == 11L, "Generation was lost");
    }

    private static void validationAcceptsTaskIntentsAndRejectsUnknownPlans() {
        TaskIntentExpansion.validate(
                template(),
                tasks(),
                Map.of(),
                VOCABULARY
        );
        expectFailure(() -> TaskIntentExpansion.validate(
                        plain(),
                        tasks(),
                        Map.of(),
                        VOCABULARY
                ),
                "an intent naming a plan that does not exist"
        );
    }

    private static void noTasksMeansNoChange() {
        Map<OrchestrationId, IntentDefinition> intents = new LinkedHashMap<>();
        intents.put(PLAIN, plain());
        Map<OrchestrationId, PlanDefinition> plans = new LinkedHashMap<>();
        plans.put(PLAIN_PLAN, plan(PLAIN_PLAN));
        require(TaskIntentExpansion.expand(intents, plans, Map.of()) == 0,
                "Expansion reported work with no tasks loaded");
        require(intents.size() == 1 && plans.size() == 1,
                "Expansion altered a catalog that had no tasks");
    }

    private static Map<OrchestrationId, TaskDefinition> tasks() {
        return Map.of(TASK, new TaskDefinition(TASK, List.of(
                new TaskMethod(
                        List.of(new FactCondition(
                                CompanionIntentIds.HUNGER,
                                FactComparison.LESS_OR_EQUAL,
                                20.0D
                        )),
                        List.of(TaskStep.action(
                                CompanionIntentIds.FETCH_SNACK_CABINET_MEAL,
                                Map.of(),
                                200
                        ))
                ),
                new TaskMethod(
                        List.of(),
                        List.of(
                                TaskStep.action(
                                        CompanionIntentIds.APPROACH_OWNER,
                                        Map.of("speed", "0.55"),
                                        200
                                ),
                                TaskStep.action(
                                        CompanionIntentIds
                                                .REQUEST_HUNGER_ATTENTION,
                                        Map.of(),
                                        20
                                )
                        )
                )
        )));
    }

    private static IntentDefinition template() {
        return intent(TEMPLATE, TASK);
    }

    private static IntentDefinition plain() {
        return intent(PLAIN, PLAIN_PLAN);
    }

    private static IntentDefinition intent(
            OrchestrationId id,
            OrchestrationId plan
    ) {
        return new IntentDefinition(
                id,
                plan,
                List.of(new FactCondition(
                        CompanionIntentIds.OWNER_VALID,
                        FactComparison.EQUAL,
                        1.0D
                )),
                List.of(),
                0.5D,
                0.0D,
                1.0D,
                1,
                0,
                0.0D,
                0,
                0
        );
    }

    private static PlanDefinition plan(OrchestrationId id) {
        return new PlanDefinition(id, "start", Map.of(
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

    private static void expectFailure(Runnable operation, String what) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Accepted " + what);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
