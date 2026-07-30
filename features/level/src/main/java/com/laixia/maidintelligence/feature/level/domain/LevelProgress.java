package com.laixia.maidintelligence.feature.level.domain;

public record LevelProgress(int level, int experience) {
    public LevelProgress {
        if (level < 1) {
            throw new IllegalArgumentException("level must be at least 1");
        }
        if (experience < 0) {
            throw new IllegalArgumentException("experience must not be negative");
        }
    }

    public static LevelProgress initial() {
        return new LevelProgress(1, 0);
    }
}
