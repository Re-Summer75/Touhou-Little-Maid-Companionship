package com.laixia.maidintelligence.feature.orchestration.domain.task;

import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityConsideration;

import java.util.List;
import java.util.Objects;

/**
 * One way of accomplishing a {@link TaskDefinition}: a guard, the steps to take
 * when it holds, and optionally how eagerly to attempt it.
 *
 * <p>Methods are ordered, and the generated intents inherit that order as a
 * tie-break. Order alone only settles an exact tie though — where two methods
 * differ in urgency rather than in preference, that belongs in
 * {@link #selection()}, and where they differ in what makes them attractive it
 * belongs in {@link #considerations()}.
 */
public record TaskMethod(
        List<FactCondition> conditions,
        List<TaskStep> steps,
        MethodSelection selection,
        List<UtilityConsideration> considerations
) {
    public TaskMethod {
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(selection, "selection");
        conditions = List.copyOf(conditions);
        steps = List.copyOf(steps);
        considerations = List.copyOf(considerations);
        if (steps.isEmpty()) {
            /*
             * An empty method would expand into a plan with no states, which
             * the plan model rejects anyway — but it would do so with an error
             * about the generated plan rather than about the method that is
             * actually wrong.
             */
            throw new IllegalArgumentException(
                    "Task method must declare at least one step"
            );
        }
    }

    /**
     * A method that differs from its siblings only in guard and steps, which is
     * the common case and the only one the original shape could express.
     */
    public TaskMethod(
            List<FactCondition> conditions,
            List<TaskStep> steps
    ) {
        this(conditions, steps, MethodSelection.inherit(), List.of());
    }

    /**
     * Whether this method leaves everything about selection to the intent that
     * named the task. Nested tasks must, since they expand into the middle of
     * another branch where there is nothing of their own to select.
     */
    public boolean inheritsSelection() {
        return selection.overridesNothing() && considerations.isEmpty();
    }
}
