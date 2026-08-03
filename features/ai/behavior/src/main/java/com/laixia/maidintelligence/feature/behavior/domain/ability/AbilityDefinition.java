package com.laixia.maidintelligence.feature.behavior.domain.ability;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record AbilityDefinition(
        OrchestrationId id,
        AbilityTemplate template,
        OrchestrationId action,
        Map<String, String> parameters,
        int timeoutTicks,
        int requestTtlTicks,
        int cooldownTicks,
        double commandScore,
        double autonomousScore,
        int interruptPriority
) {
    public AbilityDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(action, "action");
        parameters = Map.copyOf(new LinkedHashMap<>(parameters));
        if (parameters.size() > 32) {
            throw new IllegalArgumentException(
                    "Ability parameters exceed maximum 32"
            );
        }
        requireRange(timeoutTicks, 1, 72_000, "timeoutTicks");
        requireRange(requestTtlTicks, 1, 12_000, "requestTtlTicks");
        requireRange(cooldownTicks, 0, 72_000, "cooldownTicks");
        requireScore(commandScore, "commandScore");
        requireScore(autonomousScore, "autonomousScore");
        requireRange(interruptPriority, 0, 1_000, "interruptPriority");
    }

    private static void requireScore(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D || value > 1_000.0D) {
            throw new IllegalArgumentException(
                    name + " must be finite and in [0, 1000]"
            );
        }
    }

    private static void requireRange(
            int value,
            int minimum,
            int maximum,
            String name
    ) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be in [" + minimum + ", " + maximum + "]"
            );
        }
    }
}
