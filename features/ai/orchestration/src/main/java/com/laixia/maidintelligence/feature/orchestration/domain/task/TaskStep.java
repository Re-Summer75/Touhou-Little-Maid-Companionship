package com.laixia.maidintelligence.feature.orchestration.domain.task;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Map;
import java.util.Objects;

/**
 * One entry in a {@link TaskMethod}: either an action to run or another task to
 * decompose in its place.
 */
public record TaskStep(
        OrchestrationId target,
        Kind kind,
        Map<String, String> parameters,
        int timeoutTicks
) {
    public TaskStep {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
        parameters = Map.copyOf(parameters);
        if (timeoutTicks < 1 || timeoutTicks > 12_000) {
            throw new IllegalArgumentException(
                    "Task step timeout must be in [1, 12000]"
            );
        }
        if (kind == Kind.TASK && !parameters.isEmpty()) {
            /*
             * Parameters belong to the action that finally runs. A task
             * expands into several actions, so there is no single place to
             * put them and nothing sensible to do with a name that half the
             * expansion does not accept.
             */
            throw new IllegalArgumentException(
                    "Task reference " + target + " cannot carry parameters"
            );
        }
    }

    public static TaskStep action(
            OrchestrationId action,
            Map<String, String> parameters,
            int timeoutTicks
    ) {
        return new TaskStep(action, Kind.ACTION, parameters, timeoutTicks);
    }

    public static TaskStep task(OrchestrationId task, int timeoutTicks) {
        return new TaskStep(task, Kind.TASK, Map.of(), timeoutTicks);
    }

    public boolean isTask() {
        return kind == Kind.TASK;
    }

    public enum Kind {
        ACTION,
        TASK
    }
}
