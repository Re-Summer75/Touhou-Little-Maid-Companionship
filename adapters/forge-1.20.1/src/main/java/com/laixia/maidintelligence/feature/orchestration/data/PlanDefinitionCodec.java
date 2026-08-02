package com.laixia.maidintelligence.feature.orchestration.data;

import com.google.gson.JsonElement;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

final class PlanDefinitionCodec {
    private static final Codec<PlanDefinition.State> STATE =
            RecordCodecBuilder.create(instance -> instance.group(
                    OrchestrationCodecSupport.ID.fieldOf("action")
                            .forGetter(PlanDefinition.State::action),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING)
                            .optionalFieldOf("parameters", Map.of())
                            .forGetter(PlanDefinition.State::parameters),
                    Codec.intRange(1, 72_000)
                            .optionalFieldOf("timeout_ticks", 200)
                            .forGetter(PlanDefinition.State::timeoutTicks),
                    Codec.STRING.fieldOf("on_success")
                            .forGetter(PlanDefinition.State::onSuccess),
                    Codec.STRING.fieldOf("on_failure")
                            .forGetter(PlanDefinition.State::onFailure),
                    Codec.STRING.optionalFieldOf(
                            "on_cancel",
                            PlanDefinition.FAILURE
                    ).forGetter(PlanDefinition.State::onCancelled)
            ).apply(instance, PlanDefinition.State::new));

    private static final Codec<Payload> PAYLOAD =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(1, 1)
                            .fieldOf("format_version")
                            .forGetter(Payload::formatVersion),
                    Codec.STRING.fieldOf("initial_state")
                            .forGetter(Payload::initialState),
                    Codec.unboundedMap(Codec.STRING, STATE)
                            .fieldOf("states")
                            .forGetter(Payload::states)
            ).apply(instance, Payload::new));

    private PlanDefinitionCodec() {
    }

    static DataResult<PlanDefinition> parse(
            OrchestrationId id,
            JsonElement json
    ) {
        return PAYLOAD.parse(JsonOps.INSTANCE, json)
                .flatMap(payload -> create(id, payload));
    }

    private static DataResult<PlanDefinition> create(
            OrchestrationId id,
            Payload payload
    ) {
        try {
            return DataResult.success(new PlanDefinition(
                    id,
                    payload.initialState(),
                    payload.states()
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private record Payload(
            int formatVersion,
            String initialState,
            Map<String, PlanDefinition.State> states
    ) {
    }
}
