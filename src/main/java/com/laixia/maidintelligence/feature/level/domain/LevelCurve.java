package com.laixia.maidintelligence.feature.level.domain;

public interface LevelCurve {
    int maxLevel();

    int experienceRequiredForNextLevel(int currentLevel);
}
