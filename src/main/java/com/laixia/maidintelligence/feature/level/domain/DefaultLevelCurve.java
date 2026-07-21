package com.laixia.maidintelligence.feature.level.domain;

public enum DefaultLevelCurve implements LevelCurve {
    INSTANCE;

    public static final int MAX_LEVEL = 50;

    @Override
    public int maxLevel() {
        return MAX_LEVEL;
    }

    @Override
    public int experienceRequiredForNextLevel(int currentLevel) {
        if (currentLevel < 1 || currentLevel >= maxLevel()) {
            return 0;
        }
        return 50 + 25 * (currentLevel - 1);
    }
}
