package com.laixia.maidintelligence.feature.level.codec;

import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Mojang serialization stays in the TLM adapter while preserving task-data keys.
 */
public final class LevelProgressCodec {
    public static final Codec<LevelProgress> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("level", 1)
                            .forGetter(LevelProgress::level),
                    Codec.INT.optionalFieldOf("experience", 0)
                            .forGetter(LevelProgress::experience)
            ).apply(instance, LevelProgress::new));

    private LevelProgressCodec() {
    }
}
