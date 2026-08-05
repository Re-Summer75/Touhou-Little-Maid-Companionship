package com.laixia.maidintelligence.feature.orchestration.domain.task;

import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns an intent that names a task into the ordinary intents and plans the
 * rest of the system already understands.
 *
 * <p>One authored intent becomes one intent per decomposition branch. That is
 * the same shape as writing the variants out by hand — which is what the three
 * hunger intents are today — except the guards are derived rather than
 * maintained in parallel, so a change to the goal cannot be applied to two of
 * the three copies.
 */
public final class TaskIntentCompiler {
    private TaskIntentCompiler() {
    }

    public record Compiled(
            List<IntentDefinition> intents,
            List<PlanDefinition> plans
    ) {
        public Compiled {
            intents = List.copyOf(intents);
            plans = List.copyOf(plans);
        }
    }

    /**
     * @param template an intent whose {@code plan} names a task rather than a
     *                 plan; every other field is inherited unchanged by each
     *                 generated variant
     */
    public static Compiled compile(
            IntentDefinition template,
            Map<OrchestrationId, TaskDefinition> tasks
    ) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(tasks, "tasks");
        List<TaskExpansion.Branch> branches =
                TaskExpansion.expand(template.plan(), tasks);
        List<IntentDefinition> intents = new ArrayList<>(branches.size());
        List<PlanDefinition> plans = new ArrayList<>(branches.size());
        for (int index = 0; index < branches.size(); index++) {
            TaskExpansion.Branch branch = branches.get(index);
            String suffix = suffix(index);
            OrchestrationId planId = derive(template.plan(), suffix);
            plans.add(TaskExpansion.toPlan(planId, branch));
            intents.add(branch.selection().applyTo(
                    template,
                    derive(template.id(), suffix),
                    planId,
                    mergeConditions(template.conditions(), branch.conditions()),
                    // An empty override inherits rather than clears: a method
                    // that says nothing about utility wants the intent's, not
                    // none at all.
                    branch.considerations().isEmpty()
                            ? template.considerations()
                            : branch.considerations()
            ));
        }
        return new Compiled(intents, plans);
    }

    /**
     * Both the template's own guards and the branch's, in that order.
     *
     * <p>A branch cannot loosen what the authored intent already required: the
     * lists are concatenated and every entry still has to hold, so decomposition
     * can only ever narrow the situations an intent applies to.
     */
    private static List<FactCondition> mergeConditions(
            List<FactCondition> template,
            List<FactCondition> branch
    ) {
        LinkedHashSet<FactCondition> merged = new LinkedHashSet<>(template);
        merged.addAll(branch);
        return List.copyOf(merged);
    }

    /**
     * Zero padded, because ids are compared as text and that comparison is the
     * final tie-break between two intents that score identically. Without the
     * padding {@code m10} would sort before {@code m2} and the tenth method
     * would quietly outrank the third.
     */
    private static String suffix(int index) {
        return index < 10 ? "m0" + index : "m" + index;
    }

    private static OrchestrationId derive(
            OrchestrationId base,
            String suffix
    ) {
        return new OrchestrationId(
                base.namespace(),
                base.path() + "/" + suffix
        );
    }
}
