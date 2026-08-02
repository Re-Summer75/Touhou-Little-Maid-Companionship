package com.laixia.maidintelligence.feature.behavior.api;

/**
 * Explicit owner gesture that asks one maid to approach.
 */
@FunctionalInterface
public interface MaidGazeRecallApi<O, M> {
    boolean tryRecall(O owner, M maid);
}
