package com.laixia.maidintelligence.feature.status.codec;

import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Mojang serialization stays in the TLM adapter while preserving legacy defaults.
 */
public final class MaidStatusStateCodec {
    public static final Codec<MaidStatusState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf(
                            "hunger",
                            DefaultHungerPolicy.MAX_HUNGER
                    ).forGetter(MaidStatusState::hunger),
                    Codec.FLOAT.optionalFieldOf("saturation", 0.0F)
                            .forGetter(MaidStatusState::saturation),
                    Codec.FLOAT.optionalFieldOf("exhaustion", 0.0F)
                            .forGetter(MaidStatusState::exhaustion)
            ).apply(instance, MaidStatusState::new));

    private MaidStatusStateCodec() {
    }
}
