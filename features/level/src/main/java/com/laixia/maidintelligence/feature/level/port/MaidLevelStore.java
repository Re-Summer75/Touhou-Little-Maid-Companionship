package com.laixia.maidintelligence.feature.level.port;

import com.laixia.maidintelligence.feature.level.domain.LevelProgress;

/**
 * Persists progress against a platform-neutral subject type supplied by an adapter.
 */
public interface MaidLevelStore<S> {
    LevelProgress get(S subject);

    void set(S subject, LevelProgress progress);
}
