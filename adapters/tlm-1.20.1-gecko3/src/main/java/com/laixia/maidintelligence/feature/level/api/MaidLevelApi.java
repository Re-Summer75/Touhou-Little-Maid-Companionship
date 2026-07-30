package com.laixia.maidintelligence.feature.level.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;

public interface MaidLevelApi {
    LevelProgress getProgress(EntityMaid maid);

    LevelChange awardExperience(EntityMaid maid, int amount, ExperienceSource source);

    LevelProgress setProgress(EntityMaid maid, int level, int experience);
}
