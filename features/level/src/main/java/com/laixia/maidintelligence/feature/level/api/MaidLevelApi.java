package com.laixia.maidintelligence.feature.level.api;

import com.laixia.maidintelligence.feature.level.domain.LevelProgress;

/**
 * Level operations keyed by an adapter-owned subject or stable handle.
 */
public interface MaidLevelApi<S> {
    LevelProgress getProgress(S subject);

    LevelChange awardExperience(S subject, int amount, ExperienceSource source);

    LevelProgress setProgress(S subject, int level, int experience);
}
