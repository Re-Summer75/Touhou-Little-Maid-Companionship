package com.laixia.maidintelligence.feature.status.port;

import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;

/**
 * Persists status against a platform-neutral subject type supplied by an adapter.
 */
public interface MaidStatusStore<S> {
    MaidStatusState get(S subject);

    void set(S subject, MaidStatusState state);
}
