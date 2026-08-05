package com.laixia.maidintelligence.feature.orchestration.data;

import com.google.gson.JsonElement;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityAggregation;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityConsideration;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Locale;

final class IntentDefinitionCodec {
    /**
     * Package private so {@link TaskDefinitionCodec} parses a method guard the
     * same way an intent guard is parsed. A second declaration would be free to
     * drift, and a guard that means one thing in a task and another in an
     * intent is exactly the kind of difference nobody would look for.
     */
    static final Codec<FactCondition> CONDITION =
            RecordCodecBuilder.create(instance -> instance.group(
                    OrchestrationCodecSupport.ID.fieldOf("fact")
                            .forGetter(FactCondition::fact),
                    OrchestrationCodecSupport.COMPARISON.fieldOf("operator")
                            .forGetter(FactCondition::comparison),
                    Codec.DOUBLE.fieldOf("value")
                            .forGetter(FactCondition::expected)
            ).apply(instance, FactCondition::new));

    /** Shared with {@link TaskDefinitionCodec} for the same reason as
     * {@link #CONDITION}: one spelling of a consideration, not two. */
    static final Codec<UtilityConsideration> CONSIDERATION =
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
                    Codec.intRange(1, 2)
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
                    OrchestrationCodecSupport.AGGREGATION.optionalFieldOf(
                            "aggregation",
                            UtilityAggregation.SUM
                    ).forGetter(Payload::aggregation),
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
        if (payload.formatVersion() < 2
                && payload.aggregation() != UtilityAggregation.SUM) {
            /*
             * A version 1 file that names an aggregation is refused rather than
             * honoured. Weights and base scores mean different things under the
             * two modes, so silently accepting the field would reinterpret
             * every number around it instead of just the one that was written.
             */
            return DataResult.error(() ->
                    "Intent " + id + " uses aggregation '"
                            + payload.aggregation().name()
                                    .toLowerCase(Locale.ROOT)
                            + "' which requires format_version 2"
            );
        }
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
                    selection.cooldownTicks(),
                    payload.aggregation()
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
            UtilityAggregation aggregation,
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
