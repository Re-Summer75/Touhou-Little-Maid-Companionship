package com.laixia.maidintelligence.feature.orchestration.domain.action;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ActionSchema(
        OrchestrationId id,
        Map<String, ActionParameterType> parameters,
        Set<String> requiredParameters
) {
    public ActionSchema {
        Objects.requireNonNull(id, "id");
        parameters = Map.copyOf(parameters);
        requiredParameters = Set.copyOf(requiredParameters);
        if (!parameters.keySet().containsAll(requiredParameters)) {
            throw new IllegalArgumentException(
                    "Required action parameters must have declared types"
            );
        }
    }

    public static ActionSchema withoutParameters(OrchestrationId id) {
        return new ActionSchema(id, Map.of(), Set.of());
    }

    public void validate(Map<String, String> supplied) {
        for (String name : supplied.keySet()) {
            if (!parameters.containsKey(name)) {
                throw new IllegalArgumentException(
                        "Action " + id
                                + " received unknown parameter " + name
                );
            }
        }
        if (!supplied.keySet().containsAll(requiredParameters)) {
            throw new IllegalArgumentException(
                    "Action " + id + " is missing a required parameter"
            );
        }
        supplied.forEach((name, value) -> {
            if (!parameters.get(name).accepts(value)) {
                throw new IllegalArgumentException(
                        "Action " + id + " parameter " + name
                                + " has invalid "
                                + parameters.get(name) + " value"
                );
            }
        });
    }
}
