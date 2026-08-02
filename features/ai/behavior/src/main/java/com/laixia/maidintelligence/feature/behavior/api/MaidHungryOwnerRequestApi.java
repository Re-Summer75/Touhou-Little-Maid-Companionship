package com.laixia.maidintelligence.feature.behavior.api;

/**
 * Probabilistic request that lets a hungry maid seek her owner for food.
 */
public interface MaidHungryOwnerRequestApi<M> {
    boolean shouldEvaluate(M maid, long gameTime);

    boolean tryRequest(M maid);
}
