package com.laixia.maidintelligence.feature.behavior.domain.ability;

import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;

import java.util.List;
import java.util.Objects;

public record CompiledAbilityTemplate(
        AbilityDefinition definition,
        PlanDefinition plan,
        List<IntentDefinition> intents,
        OrchestrationId commandSignal,
        OrchestrationId autonomousSignal
) {
    public CompiledAbilityTemplate {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(plan, "plan");
        intents = List.copyOf(intents);
        Objects.requireNonNull(commandSignal, "commandSignal");
        Objects.requireNonNull(autonomousSignal, "autonomousSignal");
        if (intents.size() != 2) {
            throw new IllegalArgumentException(
                    "Ability template must compile exactly two intents"
            );
        }
    }
}
