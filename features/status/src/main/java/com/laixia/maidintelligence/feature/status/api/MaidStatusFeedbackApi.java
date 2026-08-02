package com.laixia.maidintelligence.feature.status.api;

/**
 * Adapter-neutral status feedback triggered by maid runtime integrations.
 */
public interface MaidStatusFeedbackApi<S> {
    boolean reportInventoryFull(S subject);
}
