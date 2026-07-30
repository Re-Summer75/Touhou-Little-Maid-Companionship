package com.laixia.maidintelligence.feature.level.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

@FunctionalInterface
public interface LevelNotificationPort {
    void notifyLevelUp(EntityMaid maid, int oldLevel, int newLevel);
}
