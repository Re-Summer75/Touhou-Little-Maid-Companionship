package com.laixia.maidintelligence.feature.behavior.api;

/**
 * Observes work and wandering edges, then performs at most one owner return.
 */
public interface MaidOwnerReturnApi<M> {
    boolean tick(M maid, long gameTime);
}
