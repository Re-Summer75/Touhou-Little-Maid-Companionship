package com.laixia.maidintelligence.feature.level.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record LevelProgress(int level, int experience) {
    public static final Codec<LevelProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("level", 1).forGetter(LevelProgress::level),
            Codec.INT.optionalFieldOf("experience", 0).forGetter(LevelProgress::experience)
    ).apply(instance, LevelProgress::new));

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
