package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityConsideration;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityCurve;
import com.laixia.maidintelligence.feature.orchestration.domain.task.MethodSelection;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskExpansion;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskIntentCompiler;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskMethod;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.FACT_A;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.FACT_B;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.SUCCEED;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.VOCABULARY;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.WAIT;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.condition;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.expectFailure;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.id;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.require;

/**
 * Compile-time decomposition: what a task flattens into, and what it refuses
 * to flatten.
 *
 * <p>The point of the whole layer is that nothing survives into the runtime, so
 * the closing check does not inspect shapes at all — it feeds the generated
 * definitions straight into {@link IntentCatalog#compile} and requires that
 * they are accepted as ordinary data.
 *
 * <p>What the generated intents inherit from the intent that named the task is
 * {@code MethodSelectionVerification}'s subject, not this one's.
 */
public final class TaskDecompositionVerification {
    private static final OrchestrationId OBTAIN = id("task/obtain_food");
    private static final OrchestrationId REACH = id("task/reach_owner");
    private static final OrchestrationId TEMPLATE = id("intent/obtain_food");

    private TaskDecompositionVerification() {
    }

    public static void main(String[] args) {
        methodsBecomeBranchesInOrder();
        stepsBecomeASequentialPlan();
        nestedTasksAreInlined();
        twoNestedReferencesMultiply();
        cyclesAreRejected();
        depthIsBounded();
        branchCountIsBounded();
        unknownTaskIsRejected();
        decompositionOnlyNarrowsGuards();
        generatedIdsSortInMethodOrder();
        generatedDefinitionsCompileAsOrdinaryData();
    }

    private static void methodsBecomeBranchesInOrder() {
        List<TaskExpansion.Branch> branches =
                TaskExpansion.expand(OBTAIN, Map.of(OBTAIN, hungerTask()));
        require(branches.size() == 3,
                "Three methods should expand into three branches, got "
                        + branches.size());
        require(branches.get(0).conditions().contains(
                        condition(FACT_A, FactComparison.GREATER_OR_EQUAL, 1.0D)),
                "First branch lost its own guard");
        require(branches.get(0).steps().size() == 1,
                "First branch should be a single action");
        require(branches.get(2).steps().size() == 2,
                "Third branch should keep both of its actions");
    }

    private static void stepsBecomeASequentialPlan() {
        TaskExpansion.Branch branch = TaskExpansion
                .expand(OBTAIN, Map.of(OBTAIN, parameterisedTask()))
                .get(0);
        PlanDefinition plan = TaskExpansion.toPlan(id("plan/generated"), branch);
        require("s0".equals(plan.initialState()),
                "Generated plan does not start at its first step");
        require(plan.states().size() == 2,
                "Generated plan should have one state per step");
        PlanDefinition.State first = plan.states().get("s0");
        PlanDefinition.State second = plan.states().get("s1");
        require("s1".equals(first.onSuccess()),
                "First step does not advance to the second");
        require(PlanDefinition.SUCCESS.equals(second.onSuccess()),
                "Last step does not finish the plan");
        require(PlanDefinition.FAILURE.equals(first.onFailure())
                        && PlanDefinition.FAILURE.equals(second.onFailure()),
                "A failing step must fail the branch");
        require(first.parameters().containsKey("speed"),
                "Step parameters were dropped during expansion");
    }

    private static void nestedTasksAreInlined() {
        Map<OrchestrationId, TaskDefinition> tasks = new LinkedHashMap<>();
        tasks.put(REACH, reachTask());
        tasks.put(OBTAIN, new TaskDefinition(OBTAIN, List.of(
                new TaskMethod(
                        List.of(),
                        List.of(
                                TaskStep.task(REACH, 40),
                                TaskStep.action(SUCCEED, Map.of(), 20)
                        )
                )
        )));
        List<TaskExpansion.Branch> branches =
                TaskExpansion.expand(OBTAIN, tasks);
        require(branches.size() == 2,
                "A nested task with two methods should yield two branches");
        for (TaskExpansion.Branch branch : branches) {
            require(branch.steps().stream().noneMatch(TaskStep::isTask),
                    "A task reference survived expansion");
            require(branch.steps()
                            .get(branch.steps().size() - 1)
                            .target()
                            .equals(SUCCEED),
                    "The step following the nested task was lost");
        }
    }

    private static void twoNestedReferencesMultiply() {
        Map<OrchestrationId, TaskDefinition> tasks = new LinkedHashMap<>();
        tasks.put(REACH, reachTask());
        tasks.put(OBTAIN, new TaskDefinition(OBTAIN, List.of(
                new TaskMethod(
                        List.of(),
                        List.of(TaskStep.task(REACH, 40), TaskStep.task(REACH, 40))
                )
        )));
        require(TaskExpansion.expand(OBTAIN, tasks).size() == 4,
                "Two references to a two-method task should give four branches");
    }

    private static void cyclesAreRejected() {
        OrchestrationId left = id("task/left");
        OrchestrationId right = id("task/right");
        Map<OrchestrationId, TaskDefinition> tasks = new LinkedHashMap<>();
        tasks.put(left, new TaskDefinition(left, List.of(new TaskMethod(
                List.of(),
                List.of(TaskStep.task(right, 20))
        ))));
        tasks.put(right, new TaskDefinition(right, List.of(new TaskMethod(
                List.of(),
                List.of(TaskStep.task(left, 20))
        ))));
        expectFailure(() -> TaskExpansion.expand(left, tasks),
                "A cyclic decomposition was accepted");
        // Direct self-reference is the same failure and must not slip past the
        // two-task check above.
        OrchestrationId loop = id("task/loop");
        Map<OrchestrationId, TaskDefinition> self = Map.of(
                loop,
                new TaskDefinition(loop, List.of(new TaskMethod(
                        List.of(),
                        List.of(TaskStep.task(loop, 20))
                )))
        );
        expectFailure(() -> TaskExpansion.expand(loop, self),
                "A self-referencing task was accepted");
    }

    private static void depthIsBounded() {
        Map<OrchestrationId, TaskDefinition> tasks = new LinkedHashMap<>();
        int levels = TaskExpansion.MAX_DEPTH + 3;
        for (int level = 0; level < levels; level++) {
            OrchestrationId current = id("task/level" + level);
            List<TaskStep> steps = level + 1 < levels
                    ? List.of(TaskStep.task(id("task/level" + (level + 1)), 20))
                    : List.of(TaskStep.action(SUCCEED, Map.of(), 20));
            tasks.put(current, new TaskDefinition(
                    current,
                    List.of(new TaskMethod(List.of(), steps))
            ));
        }
        expectFailure(
                () -> TaskExpansion.expand(id("task/level0"), tasks),
                "Decomposition deeper than the limit was accepted"
        );
    }

    private static void branchCountIsBounded() {
        Map<OrchestrationId, TaskDefinition> tasks = new LinkedHashMap<>();
        tasks.put(REACH, reachTask());
        List<TaskStep> many = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            many.add(TaskStep.task(REACH, 20));
        }
        OrchestrationId wide = id("task/wide");
        tasks.put(wide, new TaskDefinition(
                wide,
                List.of(new TaskMethod(List.of(), many))
        ));
        expectFailure(() -> TaskExpansion.expand(wide, tasks),
                "A combinatorial explosion was accepted");
    }

    private static void unknownTaskIsRejected() {
        expectFailure(
                () -> TaskExpansion.expand(OBTAIN, Map.of()),
                "An unknown task reference was accepted"
        );
    }

    private static void decompositionOnlyNarrowsGuards() {
        TaskIntentCompiler.Compiled compiled = TaskIntentCompiler.compile(
                template(List.of(
                        condition(FACT_B, FactComparison.EQUAL, 1.0D)
                )),
                Map.of(OBTAIN, hungerTask())
        );
        require(compiled.intents().size() == 3,
                "Compilation should produce one intent per branch");
        for (IntentDefinition intent : compiled.intents()) {
            require(intent.conditions().contains(
                            condition(FACT_B, FactComparison.EQUAL, 1.0D)),
                    "A generated intent dropped the template guard");
            require(intent.conditions().size()
                            >= template(List.of()).conditions().size(),
                    "Decomposition loosened a guard instead of narrowing it");
        }
        require(compiled.plans().size() == 3,
                "Each generated intent needs its own plan");
    }

    /**
     * Intent ids are compared as text to break an exact scoring tie, so the
     * generated suffixes have to sort the way the methods were written.
     */
    private static void generatedIdsSortInMethodOrder() {
        List<TaskMethod> methods = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            methods.add(new TaskMethod(
                    List.of(condition(FACT_A, FactComparison.EQUAL, index)),
                    List.of(TaskStep.action(WAIT, Map.of(), 20))
            ));
        }
        OrchestrationId wide = id("task/twelve");
        TaskIntentCompiler.Compiled compiled = TaskIntentCompiler.compile(
                new IntentDefinition(
                        TEMPLATE,
                        wide,
                        List.of(),
                        List.of(),
                        0.5D,
                        0.0D,
                        1.0D,
                        1,
                        0,
                        0.0D,
                        0,
                        0
                ),
                Map.of(wide, new TaskDefinition(wide, methods))
        );
        List<IntentDefinition> intents = compiled.intents();
        require(intents.size() == 12, "Expected twelve generated intents");
        for (int index = 1; index < intents.size(); index++) {
            require(intents.get(index - 1).id()
                            .compareTo(intents.get(index).id()) < 0,
                    "Generated id " + intents.get(index).id()
                            + " does not sort after "
                            + intents.get(index - 1).id()
                            + "; the tenth method would outrank the third");
        }
    }

    /**
     * The closing claim: what decomposition emits is not a parallel format but
     * the same intents and plans the catalog already compiles.
     */
    private static void generatedDefinitionsCompileAsOrdinaryData() {
        TaskIntentCompiler.Compiled compiled = TaskIntentCompiler.compile(
                template(List.of()),
                Map.of(OBTAIN, hungerTask())
        );
        IntentCatalog catalog = IntentCatalog.compile(
                7L,
                compiled.intents(),
                compiled.plans(),
                VOCABULARY
        );
        require(catalog.intents().size() == 3,
                "The catalog did not accept every generated intent");
        require(catalog.generation() == 7L,
                "Generated data lost its catalog generation");
    }

    /**
     * The three hunger intents as one goal: eat what is carried, otherwise
     * fetch and eat, otherwise walk over and ask.
     */
    private static TaskDefinition hungerTask() {
        return new TaskDefinition(OBTAIN, List.of(
                new TaskMethod(
                        List.of(condition(
                                FACT_A,
                                FactComparison.GREATER_OR_EQUAL,
                                1.0D
                        )),
                        List.of(TaskStep.action(SUCCEED, Map.of(), 20))
                ),
                new TaskMethod(
                        List.of(condition(
                                FACT_B,
                                FactComparison.GREATER_OR_EQUAL,
                                1.0D
                        )),
                        List.of(TaskStep.action(WAIT, Map.of(), 200))
                ),
                new TaskMethod(
                        List.of(),
                        List.of(
                                TaskStep.action(WAIT, Map.of(), 200),
                                TaskStep.action(SUCCEED, Map.of(), 20)
                        )
                )
        ));
    }

    /**
     * Separate from {@link #hungerTask()} because the test vocabulary declares
     * no action parameters, so anything carrying one cannot also be pushed
     * through {@link IntentCatalog#compile}. Parameter handling is checked at
     * the plan-rendering step instead, which is where it happens.
     */
    private static TaskDefinition parameterisedTask() {
        return new TaskDefinition(OBTAIN, List.of(new TaskMethod(
                List.of(),
                List.of(
                        TaskStep.action(WAIT, Map.of("speed", "0.55"), 200),
                        TaskStep.action(SUCCEED, Map.of(), 20)
                )
        )));
    }

    private static TaskDefinition reachTask() {
        return new TaskDefinition(REACH, List.of(
                new TaskMethod(
                        List.of(condition(
                                FACT_A,
                                FactComparison.LESS_OR_EQUAL,
                                2.0D
                        )),
                        List.of(TaskStep.action(SUCCEED, Map.of(), 20))
                ),
                new TaskMethod(
                        List.of(),
                        List.of(TaskStep.action(WAIT, Map.of(), 100))
                )
        ));
    }

    private static IntentDefinition template(
            List<FactCondition> conditions
    ) {
        return new IntentDefinition(
                TEMPLATE,
                OBTAIN,
                conditions,
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
}
