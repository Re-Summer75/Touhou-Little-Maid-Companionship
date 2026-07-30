package com.laixia.maidintelligence.feature.advancement.port;

/**
 * Emits experience granted by a completed advancement without depending on
 * the level feature.
 */
@FunctionalInterface
public interface MaidExperienceRewardPort<S> {
    void reward(S subject, int points);
}
