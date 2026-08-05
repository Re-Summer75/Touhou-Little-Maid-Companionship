package com.laixia.maidintelligence.feature.orchestration.domain.task;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.List;
import java.util.Objects;

/**
 * A goal stated once, with the alternative ways of reaching it listed beneath
 * it.
 *
 * <p>This is the piece the flat plan model could not express. Three intents
 * that differ only in which food source they reach for are three copies of the
 * same goal, and every later edit has to find all three. Here the goal is one
 * definition and the alternatives are its methods.
 *
 * <p>Nothing about that reaches the runtime. {@link TaskExpansion} flattens a
 * definition back into ordinary plans and conditions before the catalog is
 * published, so the executor keeps running the same state machines it always
 * has and {@code ai explain} keeps listing one candidate per branch rather than
 * one opaque candidate with a hidden decision inside it.
 */
public record TaskDefinition(
        OrchestrationId id,
        List<TaskMethod> methods
) {
    public TaskDefinition {
        Objects.requireNonNull(id, "id");
        methods = List.copyOf(methods);
        if (methods.isEmpty()) {
            throw new IllegalArgumentException(
                    "Task " + id + " must declare at least one method"
            );
        }
    }
}
