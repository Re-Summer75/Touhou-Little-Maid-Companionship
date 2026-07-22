package com.laixia.maidintelligence.feature.status.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record MaidStatusState(int hunger) {
    public static final Codec<MaidStatusState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("hunger", DefaultHungerPolicy.MAX_HUNGER)
                    .forGetter(MaidStatusState::hunger)
    ).apply(instance, MaidStatusState::new));

    public MaidStatusState {
        if (hunger < 0 || hunger > DefaultHungerPolicy.MAX_HUNGER) {
            throw new IllegalArgumentException(
                    "hunger must be between 0 and " + DefaultHungerPolicy.MAX_HUNGER
            );
        }
    }

    public static MaidStatusState initial() {
        return new MaidStatusState(DefaultHungerPolicy.MAX_HUNGER);
    }
}
