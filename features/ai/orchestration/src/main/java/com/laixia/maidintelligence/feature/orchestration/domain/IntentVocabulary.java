package com.laixia.maidintelligence.feature.orchestration.domain;

import com.laixia.maidintelligence.feature.orchestration.domain.action.ActionSchema;
import com.laixia.maidintelligence.feature.orchestration.domain.fact.FactType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public record IntentVocabulary(
        Set<OrchestrationId> facts,
        Set<OrchestrationId> actions,
        Set<OrchestrationId> signals,
        Map<OrchestrationId, FactType> factTypes,
        Map<OrchestrationId, ActionSchema> actionSchemas
) {
    public IntentVocabulary {
        facts = Set.copyOf(facts);
        actions = Set.copyOf(actions);
        signals = Set.copyOf(signals);
        factTypes = Map.copyOf(factTypes);
        actionSchemas = Map.copyOf(actionSchemas);
        if (!facts.containsAll(signals)) {
            throw new IllegalArgumentException(
                    "Every signal must also be registered as a fact"
            );
        }
        if (!factTypes.keySet().equals(facts)) {
            throw new IllegalArgumentException(
                    "Every registered fact must declare exactly one type"
            );
        }
        for (OrchestrationId signal : signals) {
            if (factTypes.get(signal) != FactType.SIGNAL) {
                throw new IllegalArgumentException(
                        "Signal fact must use SIGNAL type: " + signal
                );
            }
        }
        if (!actionSchemas.keySet().equals(actions)) {
            throw new IllegalArgumentException(
                    "Every registered action must declare one schema"
            );
        }
    }

    public IntentVocabulary(
            Set<OrchestrationId> facts,
            Set<OrchestrationId> actions,
            Set<OrchestrationId> signals
    ) {
        this(
                facts,
                actions,
                signals,
                defaultTypes(facts, signals),
                defaultActionSchemas(actions)
        );
    }

    public IntentVocabulary(
            Set<OrchestrationId> facts,
            Set<OrchestrationId> actions,
            Set<OrchestrationId> signals,
            Map<OrchestrationId, FactType> factTypes
    ) {
        this(
                facts,
                actions,
                signals,
                factTypes,
                defaultActionSchemas(actions)
        );
    }

    public FactType factType(OrchestrationId fact) {
        return factTypes.get(fact);
    }

    public ActionSchema actionSchema(OrchestrationId action) {
        return actionSchemas.get(action);
    }

    public static IntentVocabulary empty() {
        return new IntentVocabulary(Set.of(), Set.of(), Set.of());
    }

    private static Map<OrchestrationId, FactType> defaultTypes(
            Set<OrchestrationId> facts,
            Set<OrchestrationId> signals
    ) {
        Map<OrchestrationId, FactType> types = new HashMap<>();
        facts.forEach(fact -> types.put(fact, FactType.NUMBER));
        signals.forEach(signal -> types.put(signal, FactType.SIGNAL));
        return types;
    }

    private static Map<OrchestrationId, ActionSchema> defaultActionSchemas(
            Set<OrchestrationId> actions
    ) {
        Map<OrchestrationId, ActionSchema> schemas = new HashMap<>();
        actions.forEach(action -> schemas.put(
                action,
                ActionSchema.withoutParameters(action)
        ));
        return schemas;
    }
}
