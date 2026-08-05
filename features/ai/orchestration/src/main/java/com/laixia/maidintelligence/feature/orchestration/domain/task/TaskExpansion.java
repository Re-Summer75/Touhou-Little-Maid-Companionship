package com.laixia.maidintelligence.feature.orchestration.domain.task;

import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityConsideration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Flattens a {@link TaskDefinition} into ordinary plans and guards.
 *
 * <p>All of it happens while the catalog is being compiled. Method choice
 * therefore costs nothing at runtime — it has already become a condition on a
 * generated intent, evaluated by the same guard machinery as every hand-written
 * intent, and visible in {@code ai explain} as its own candidate. A runtime
 * decomposer would have had to re-derive the same answer every tick and would
 * have hidden it behind one candidate while doing so.
 *
 * <p>The price is combinatorial: a method holding two task references, each
 * with three methods, is nine branches. {@link #MAX_BRANCHES} bounds that
 * rather than trusting authors to notice, because the explosion shows up as
 * catalog size rather than as anything visible while writing the data.
 */
public final class TaskExpansion {
    /**
     * Nesting allowed below the root task. Four is deep enough for
     * "goal to sub-goal to concrete action" with room to spare, and shallow
     * enough that a mistake surfaces as an error rather than as a catalog that
     * merely takes a long time to compile.
     */
    public static final int MAX_DEPTH = 4;

    public static final int MAX_BRANCHES = 32;

    private TaskExpansion() {
    }

    /**
     * One fully decomposed way of accomplishing a task: the guards that must
     * all hold, and the actions to run in order once they do.
     *
     * <p>Branches come back in method order, outermost method first, so a
     * caller can use the list position as the preference rank.
     */
    public record Branch(
            List<FactCondition> conditions,
            List<TaskStep> steps,
            MethodSelection selection,
            List<UtilityConsideration> considerations
    ) {
        public Branch {
            conditions = List.copyOf(conditions);
            steps = List.copyOf(steps);
            considerations = List.copyOf(considerations);
        }

        Branch withStep(TaskStep step) {
            List<TaskStep> grown = new ArrayList<>(steps);
            grown.add(step);
            return new Branch(conditions, grown, selection, considerations);
        }
    }

    public static List<Branch> expand(
            OrchestrationId root,
            Map<OrchestrationId, TaskDefinition> tasks
    ) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(tasks, "tasks");
        return expand(root, tasks, new ArrayList<>(), 0);
    }

    /**
     * Expands every task in the catalog, so a definition that cannot be
     * flattened is rejected at reload rather than the first time some intent
     * happens to reference it.
     */
    public static Map<OrchestrationId, List<Branch>> expandAll(
            Map<OrchestrationId, TaskDefinition> tasks
    ) {
        Map<OrchestrationId, List<Branch>> expanded = new LinkedHashMap<>();
        for (OrchestrationId id : tasks.keySet()) {
            expanded.put(id, expand(id, tasks));
        }
        return Map.copyOf(expanded);
    }

    /**
     * Renders one branch as the flat state machine the executor already runs.
     * Every step fails the whole plan, matching what a hand-written sequential
     * plan does; recovering from a failed step is the job of the sibling branch
     * that the guard will select next time, not of this one.
     */
    public static PlanDefinition toPlan(
            OrchestrationId planId,
            Branch branch
    ) {
        Objects.requireNonNull(planId, "planId");
        List<TaskStep> steps = branch.steps();
        if (steps.isEmpty()) {
            throw new IllegalArgumentException(
                    "Branch for " + planId + " has no steps"
            );
        }
        Map<String, PlanDefinition.State> states = new LinkedHashMap<>();
        for (int index = 0; index < steps.size(); index++) {
            TaskStep step = steps.get(index);
            String next = index + 1 < steps.size()
                    ? stateId(index + 1)
                    : PlanDefinition.SUCCESS;
            states.put(stateId(index), new PlanDefinition.State(
                    step.target(),
                    step.parameters(),
                    step.timeoutTicks(),
                    next,
                    PlanDefinition.FAILURE
            ));
        }
        return new PlanDefinition(planId, stateId(0), states);
    }

    private static List<Branch> expand(
            OrchestrationId id,
            Map<OrchestrationId, TaskDefinition> tasks,
            List<OrchestrationId> path,
            int depth
    ) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "Task " + id + " exceeds the maximum decomposition depth "
                            + MAX_DEPTH
            );
        }
        if (path.contains(id)) {
            throw new IllegalArgumentException(
                    "Task decomposition is cyclic: "
                            + describeCycle(path, id)
            );
        }
        TaskDefinition definition = tasks.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown task: " + id);
        }
        path.add(id);
        List<Branch> expanded = new ArrayList<>();
        for (TaskMethod method : definition.methods()) {
            if (depth > 0 && !method.inheritsSelection()) {
                /*
                 * A nested method expands into the middle of some other
                 * branch, which already carries the selection of the method
                 * that started it. Honouring both would need a rule for which
                 * one wins, and any such rule would be invisible in the data.
                 */
                throw new IllegalArgumentException(
                        "Nested task " + id + " may not override selection or"
                                + " utility; only a task named by an intent can"
                );
            }
            List<Branch> partial = new ArrayList<>();
            partial.add(new Branch(
                    method.conditions(),
                    List.of(),
                    method.selection(),
                    method.considerations()
            ));
            for (TaskStep step : method.steps()) {
                partial = step.isTask()
                        ? product(
                                partial,
                                expand(step.target(), tasks, path, depth + 1),
                                id
                        )
                        : append(partial, step);
            }
            expanded.addAll(partial);
            requireBranchBudget(expanded.size(), id);
        }
        path.remove(path.size() - 1);
        return List.copyOf(expanded);
    }

    private static List<Branch> append(List<Branch> partial, TaskStep step) {
        List<Branch> grown = new ArrayList<>(partial.size());
        for (Branch branch : partial) {
            grown.add(branch.withStep(step));
        }
        return grown;
    }

    private static List<Branch> product(
            List<Branch> partial,
            List<Branch> nested,
            OrchestrationId owner
    ) {
        List<Branch> combined =
                new ArrayList<>(partial.size() * nested.size());
        for (Branch branch : partial) {
            for (Branch inner : nested) {
                // A duplicated guard is harmless but noisy: the same condition
                // would be evaluated twice per tick and listed twice in a
                // rejection message.
                LinkedHashSet<FactCondition> conditions =
                        new LinkedHashSet<>(branch.conditions());
                conditions.addAll(inner.conditions());
                List<TaskStep> steps = new ArrayList<>(branch.steps());
                steps.addAll(inner.steps());
                // The outer method's selection survives; the nested one was
                // required to inherit, so there is nothing of its own to lose.
                combined.add(new Branch(
                        new ArrayList<>(conditions),
                        steps,
                        branch.selection(),
                        branch.considerations()
                ));
            }
            requireBranchBudget(combined.size(), owner);
        }
        return combined;
    }

    private static void requireBranchBudget(int size, OrchestrationId owner) {
        if (size > MAX_BRANCHES) {
            throw new IllegalArgumentException(
                    "Task " + owner + " expands into more than "
                            + MAX_BRANCHES + " branches"
            );
        }
    }

    private static String describeCycle(
            List<OrchestrationId> path,
            OrchestrationId repeated
    ) {
        StringBuilder description = new StringBuilder();
        for (OrchestrationId step : path) {
            description.append(step).append(" -> ");
        }
        return description.append(repeated).toString();
    }

    private static String stateId(int index) {
        return "s" + index;
    }
}
