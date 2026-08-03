package com.laixia.maidintelligence.feature.orchestration.data;

import com.google.gson.JsonElement;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.ResumePolicy;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;
import java.util.List;
import java.util.Set;

final class PlanDefinitionCodec {
    private static final Codec<ResumePolicy> RESUME_POLICY =
            Codec.STRING.comapFlatMap(
                    PlanDefinitionCodec::parseResumePolicy,
                    policy -> policy.name().toLowerCase(
                            java.util.Locale.ROOT
                    )
            );
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
                    Codec.intRange(1, 2)
                            .fieldOf("format_version")
                            .forGetter(Payload::formatVersion),
                    Codec.STRING.fieldOf("initial_state")
                            .forGetter(Payload::initialState),
                    Codec.unboundedMap(Codec.STRING, STATE)
                            .fieldOf("states")
                            .forGetter(Payload::states),
                    RESUME_POLICY.optionalFieldOf(
                            "resume_policy",
                            ResumePolicy.NEVER_RESUME
                    ).forGetter(Payload::resumePolicy),
                    Codec.STRING.listOf()
                            .optionalFieldOf("checkpoints", List.of())
                            .forGetter(Payload::checkpoints),
                    Codec.intRange(1, 72_000)
                            .optionalFieldOf("maximum_suspend_ticks", 1_200)
                            .forGetter(Payload::maximumSuspendTicks)
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
                    payload.states(),
                    payload.resumePolicy(),
                    Set.copyOf(payload.checkpoints()),
                    payload.maximumSuspendTicks()
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private record Payload(
            int formatVersion,
            String initialState,
            Map<String, PlanDefinition.State> states,
            ResumePolicy resumePolicy,
            List<String> checkpoints,
            int maximumSuspendTicks
    ) {
    }

    private static DataResult<ResumePolicy> parseResumePolicy(String value) {
        try {
            return DataResult.success(ResumePolicy.valueOf(
                    value.toUpperCase(java.util.Locale.ROOT)
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(
                    () -> "Unknown resume policy: " + value
            );
        }
    }
}
