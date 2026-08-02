package com.laixia.maidintelligence.feature.orchestration.data;

import com.google.gson.JsonElement;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.UtilityConsideration;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

final class IntentDefinitionCodec {
    private static final Codec<FactCondition> CONDITION =
            RecordCodecBuilder.create(instance -> instance.group(
                    OrchestrationCodecSupport.ID.fieldOf("fact")
                            .forGetter(FactCondition::fact),
                    OrchestrationCodecSupport.COMPARISON.fieldOf("operator")
                            .forGetter(FactCondition::comparison),
                    Codec.DOUBLE.fieldOf("value")
                            .forGetter(FactCondition::expected)
            ).apply(instance, FactCondition::new));

    private static final Codec<UtilityConsideration> CONSIDERATION =
            RecordCodecBuilder.create(instance -> instance.group(
                    OrchestrationCodecSupport.ID.fieldOf("fact")
                            .forGetter(UtilityConsideration::fact),
                    Codec.DOUBLE.fieldOf("minimum")
                            .forGetter(UtilityConsideration::minimum),
                    Codec.DOUBLE.fieldOf("maximum")
                            .forGetter(UtilityConsideration::maximum),
                    Codec.DOUBLE.fieldOf("weight")
                            .forGetter(UtilityConsideration::weight),
                    OrchestrationCodecSupport.CURVE.fieldOf("curve")
                            .forGetter(UtilityConsideration::curve)
            ).apply(instance, UtilityConsideration::new));

    private static final Codec<Selection> SELECTION =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.DOUBLE.optionalFieldOf("base_score", 0.0D)
                            .forGetter(Selection::baseScore),
                    Codec.DOUBLE.optionalFieldOf("minimum_score", 0.0D)
                            .forGetter(Selection::minimumScore),
                    Codec.DOUBLE.optionalFieldOf("activation_chance", 1.0D)
                            .forGetter(Selection::activationChance),
                    Codec.intRange(1, 12_000)
                            .optionalFieldOf("evaluation_interval_ticks", 1)
                            .forGetter(Selection::evaluationIntervalTicks),
                    Codec.intRange(0, 12_000)
                            .optionalFieldOf("minimum_commit_ticks", 0)
                            .forGetter(Selection::minimumCommitTicks),
                    Codec.DOUBLE.optionalFieldOf("switch_margin", 0.0D)
                            .forGetter(Selection::switchMargin),
                    Codec.intRange(0, 1_000)
                            .optionalFieldOf("interrupt_priority", 0)
                            .forGetter(Selection::interruptPriority),
                    Codec.intRange(0, 72_000)
                            .optionalFieldOf("cooldown_ticks", 0)
                            .forGetter(Selection::cooldownTicks)
            ).apply(instance, Selection::new));

    private static final Codec<Payload> PAYLOAD =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(1, 1)
                            .fieldOf("format_version")
                            .forGetter(Payload::formatVersion),
                    OrchestrationCodecSupport.ID.fieldOf("plan")
                            .forGetter(Payload::plan),
                    CONDITION.listOf().optionalFieldOf(
                            "conditions",
                            List.of()
                    ).forGetter(Payload::conditions),
                    CONSIDERATION.listOf().optionalFieldOf(
                            "utility",
                            List.of()
                    ).forGetter(Payload::utility),
                    SELECTION.optionalFieldOf(
                            "selection",
                            Selection.DEFAULT
                    ).forGetter(Payload::selection)
            ).apply(instance, Payload::new));

    private IntentDefinitionCodec() {
    }

    static DataResult<IntentDefinition> parse(
            OrchestrationId id,
            JsonElement json
    ) {
        return PAYLOAD.parse(JsonOps.INSTANCE, json)
                .flatMap(payload -> create(id, payload));
    }

    private static DataResult<IntentDefinition> create(
            OrchestrationId id,
            Payload payload
    ) {
        Selection selection = payload.selection();
        try {
            return DataResult.success(new IntentDefinition(
                    id,
                    payload.plan(),
                    payload.conditions(),
                    payload.utility(),
                    selection.baseScore(),
                    selection.minimumScore(),
                    selection.activationChance(),
                    selection.evaluationIntervalTicks(),
                    selection.minimumCommitTicks(),
                    selection.switchMargin(),
                    selection.interruptPriority(),
                    selection.cooldownTicks()
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private record Payload(
            int formatVersion,
            OrchestrationId plan,
            List<FactCondition> conditions,
            List<UtilityConsideration> utility,
            Selection selection
    ) {
    }

    private record Selection(
            double baseScore,
            double minimumScore,
            double activationChance,
            int evaluationIntervalTicks,
            int minimumCommitTicks,
            double switchMargin,
            int interruptPriority,
            int cooldownTicks
    ) {
        private static final Selection DEFAULT = new Selection(
                0.0D,
                0.0D,
                1.0D,
                1,
                0,
                0.0D,
                0,
                0
        );
    }
}
