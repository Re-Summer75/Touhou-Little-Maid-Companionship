package com.laixia.maidintelligence.feature.advancement.port;

/**
 * Advancement-owned view of an external level provider.
 */
@FunctionalInterface
public interface MaidLevelQueryPort<S> {
    int currentLevel(S subject);
}
