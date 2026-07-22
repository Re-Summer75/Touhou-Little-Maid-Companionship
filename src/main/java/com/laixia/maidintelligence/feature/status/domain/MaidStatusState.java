package com.laixia.maidintelligence.feature.status.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record MaidStatusState(int hunger, float saturation, float exhaustion) {
    public static final Codec<MaidStatusState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("hunger", DefaultHungerPolicy.MAX_HUNGER)
                    .forGetter(MaidStatusState::hunger),
            Codec.FLOAT.optionalFieldOf("saturation", 0.0F)
                    .forGetter(MaidStatusState::saturation),
            Codec.FLOAT.optionalFieldOf("exhaustion", 0.0F)
                    .forGetter(MaidStatusState::exhaustion)
    ).apply(instance, MaidStatusState::new));

    public MaidStatusState {
        if (hunger < 0 || hunger > DefaultHungerPolicy.MAX_HUNGER) {
            throw new IllegalArgumentException(
                    "hunger must be between 0 and " + DefaultHungerPolicy.MAX_HUNGER
            );
        }
        if (!Float.isFinite(saturation) || saturation < 0.0F || saturation > hunger) {
            throw new IllegalArgumentException(
                    "saturation must be between 0 and the current hunger"
            );
        }
        if (!Float.isFinite(exhaustion)
                || exhaustion < 0.0F
                || exhaustion > DefaultHungerPolicy.MAX_EXHAUSTION) {
            throw new IllegalArgumentException(
                    "exhaustion must be between 0 and "
                            + DefaultHungerPolicy.MAX_EXHAUSTION
            );
        }
    }

    public MaidStatusState(int hunger) {
        this(hunger, 0.0F, 0.0F);
    }

    public static MaidStatusState initial() {
        return new MaidStatusState(
                DefaultHungerPolicy.MAX_HUNGER,
                DefaultHungerPolicy.INITIAL_SATURATION,
                0.0F
        );
    }
}
