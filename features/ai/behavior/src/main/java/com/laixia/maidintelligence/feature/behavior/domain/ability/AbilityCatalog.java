package com.laixia.maidintelligence.feature.behavior.domain.ability;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AbilityCatalog {
    private static final int MAX_ABILITIES = 64;

    private final long generation;
    private final Map<OrchestrationId, AbilityDefinition> definitions;

    private AbilityCatalog(
            long generation,
            Map<OrchestrationId, AbilityDefinition> definitions
    ) {
        this.generation = generation;
        this.definitions = Map.copyOf(definitions);
    }

    public static AbilityCatalog empty() {
        return new AbilityCatalog(0L, Map.of());
    }

    public static AbilityCatalog compile(
            long generation,
            Collection<AbilityDefinition> definitions
    ) {
        Objects.requireNonNull(definitions, "definitions");
        if (definitions.size() > MAX_ABILITIES) {
            throw new IllegalArgumentException(
                    "Ability definitions exceed maximum " + MAX_ABILITIES
            );
        }
        Map<OrchestrationId, AbilityDefinition> indexed =
                new LinkedHashMap<>();
        definitions.stream()
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .forEach(definition -> {
                    if (indexed.putIfAbsent(
                            definition.id(),
                            definition
                    ) != null) {
                        throw new IllegalArgumentException(
                                "Duplicate ability id: " + definition.id()
                        );
                    }
                });
        return new AbilityCatalog(generation, indexed);
    }

    public long generation() {
        return generation;
    }

    public AbilityDefinition definition(OrchestrationId id) {
        return definitions.get(id);
    }

    public List<AbilityDefinition> definitions() {
        return List.copyOf(definitions.values());
    }
}
