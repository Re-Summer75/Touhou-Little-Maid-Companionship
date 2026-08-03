package com.laixia.maidintelligence.feature.orchestration.domain.observation;

import java.util.List;
import java.util.Objects;

/**
 * Small, high-value subset allowed to survive entity unload and save/load.
 */
public record CompanionPersistentMemory(
        int schemaVersion,
        List<Belief> beliefs,
        List<OperationOutcome> outcomes
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_BELIEFS = 32;
    public static final int MAX_OUTCOMES = 32;

    public CompanionPersistentMemory {
        if (schemaVersion < 1
                || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported companion memory schema " + schemaVersion
            );
        }
        Objects.requireNonNull(beliefs, "beliefs");
        Objects.requireNonNull(outcomes, "outcomes");
        if (beliefs.size() > MAX_BELIEFS
                || outcomes.size() > MAX_OUTCOMES) {
            throw new IllegalArgumentException(
                    "Companion persistent memory exceeds its bounds"
            );
        }
        beliefs = beliefs.stream()
                .filter(Belief::persistent)
                .toList();
        outcomes = List.copyOf(outcomes);
    }

    public static CompanionPersistentMemory initial() {
        return new CompanionPersistentMemory(
                CURRENT_SCHEMA_VERSION,
                List.of(),
                List.of()
        );
    }
}
