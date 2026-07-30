package com.laixia.maidintelligence.feature.level.api;

import com.laixia.maidintelligence.feature.level.domain.LevelProgress;

public record LevelChange(
        LevelProgress before,
        LevelProgress after,
        int awardedExperience,
        ExperienceSource source
) {
    public boolean leveledUp() {
        return after.level() > before.level();
    }
}
