package com.laixia.maidintelligence.feature.behavior.data;

import com.google.gson.JsonElement;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityDefinition;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityTemplate;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;
import java.util.Map;

public final class AbilityDefinitionCodec {
    private static final Codec<OrchestrationId> ID =
            Codec.STRING.comapFlatMap(
                    AbilityDefinitionCodec::parseId,
                    OrchestrationId::toString
            );
    private static final Codec<AbilityTemplate> TEMPLATE =
            Codec.STRING.comapFlatMap(
                    AbilityDefinitionCodec::parseTemplate,
                    template -> template.name()
                            .toLowerCase(Locale.ROOT)
            );
    private static final Codec<Payload> PAYLOAD =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(1, 1).fieldOf("format_version")
                            .forGetter(Payload::formatVersion),
                    TEMPLATE.fieldOf("template")
                            .forGetter(Payload::template),
                    ID.fieldOf("action")
                            .forGetter(Payload::action),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING)
                            .optionalFieldOf("parameters", Map.of())
                            .forGetter(Payload::parameters),
                    Codec.intRange(1, 72_000)
                            .optionalFieldOf("timeout_ticks", 100)
                            .forGetter(Payload::timeoutTicks),
                    Codec.intRange(1, 12_000)
                            .optionalFieldOf("request_ttl_ticks", 100)
                            .forGetter(Payload::requestTtlTicks),
                    Codec.intRange(0, 72_000)
                            .optionalFieldOf("cooldown_ticks", 200)
                            .forGetter(Payload::cooldownTicks),
                    Codec.doubleRange(0.0D, 1_000.0D)
                            .optionalFieldOf("command_score", 900.0D)
                            .forGetter(Payload::commandScore),
                    Codec.doubleRange(0.0D, 1_000.0D)
                            .optionalFieldOf("autonomous_score", 120.0D)
                            .forGetter(Payload::autonomousScore),
                    Codec.intRange(0, 1_000)
                            .optionalFieldOf("interrupt_priority", 500)
                            .forGetter(Payload::interruptPriority)
            ).apply(instance, Payload::new));

    private AbilityDefinitionCodec() {
    }

    public static DataResult<AbilityDefinition> parse(
            OrchestrationId id,
            JsonElement json
    ) {
        return PAYLOAD.parse(JsonOps.INSTANCE, json)
                .flatMap(payload -> create(id, payload));
    }

    private static DataResult<AbilityDefinition> create(
            OrchestrationId id,
            Payload payload
    ) {
        try {
            return DataResult.success(new AbilityDefinition(
                    id,
                    payload.template(),
                    payload.action(),
                    payload.parameters(),
                    payload.timeoutTicks(),
                    payload.requestTtlTicks(),
                    payload.cooldownTicks(),
                    payload.commandScore(),
                    payload.autonomousScore(),
                    payload.interruptPriority()
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<OrchestrationId> parseId(String value) {
        try {
            return DataResult.success(OrchestrationId.parse(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<AbilityTemplate> parseTemplate(String value) {
        try {
            return DataResult.success(AbilityTemplate.valueOf(
                    value.toUpperCase(Locale.ROOT)
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() ->
                    "Unknown ability template: " + value);
        }
    }

    private record Payload(
            int formatVersion,
            AbilityTemplate template,
            OrchestrationId action,
            Map<String, String> parameters,
            int timeoutTicks,
            int requestTtlTicks,
            int cooldownTicks,
            double commandScore,
            double autonomousScore,
            int interruptPriority
    ) {
    }
}
