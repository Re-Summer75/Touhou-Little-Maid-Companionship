package com.laixia.maidintelligence.feature.level.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;

public interface MaidLevelStore {
    LevelProgress get(EntityMaid maid);

    void set(EntityMaid maid, LevelProgress progress);
}
