package com.laixia.maidintelligence.feature.behavior.codec;

import com.laixia.maidintelligence.feature.behavior.domain.learning.AffordanceReliability;
import com.laixia.maidintelligence.feature.behavior.domain.learning.CompanionLearningProfile;
import com.laixia.maidintelligence.feature.behavior.domain.learning.HabitForecast;
import com.laixia.maidintelligence.feature.behavior.domain.learning.OwnerPreference;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CompanionLearningProfileCodec {
    private static final Codec<OrchestrationId> ID =
            Codec.STRING.comapFlatMap(
                    CompanionLearningProfileCodec::parseId,
                    OrchestrationId::toString
            );
    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.comapFlatMap(
                    CompanionLearningProfileCodec::parseUuid,
                    UUID::toString
            );
    private static final Codec<AffordanceReliability> RELIABILITY =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(0, 1_000).fieldOf("successes")
                            .forGetter(AffordanceReliability::successes),
                    Codec.intRange(0, 1_000).fieldOf("failures")
                            .forGetter(AffordanceReliability::failures),
                    Codec.LONG.fieldOf("last_updated_tick")
                            .forGetter(AffordanceReliability::lastUpdatedTick)
            ).apply(instance, AffordanceReliability::new));
    private static final Codec<OwnerPreference> PREFERENCE =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.doubleRange(-1.0D, 1.0D).fieldOf("value")
                            .forGetter(OwnerPreference::value),
                    Codec.intRange(0, 1_000).fieldOf("samples")
                            .forGetter(OwnerPreference::samples),
                    Codec.LONG.fieldOf("last_updated_tick")
                            .forGetter(OwnerPreference::lastUpdatedTick)
            ).apply(instance, OwnerPreference::new));
    private static final Codec<HabitForecast> HABIT =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.listOf().fieldOf("day_buckets")
                            .forGetter(HabitForecast::dayBuckets),
                    Codec.intRange(0, 1_000).fieldOf("samples")
                            .forGetter(HabitForecast::samples),
                    Codec.LONG.fieldOf("last_updated_tick")
                            .forGetter(HabitForecast::lastUpdatedTick)
            ).apply(instance, HabitForecast::new));

    public static final Codec<CompanionLearningProfile> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.optionalFieldOf("revision", 0L)
                            .forGetter(CompanionLearningProfile::revision),
                    Codec.BOOL.optionalFieldOf("frozen", false)
                            .forGetter(CompanionLearningProfile::frozen),
                    Codec.unboundedMap(ID, RELIABILITY)
                            .optionalFieldOf("reliability", Map.of())
                            .forGetter(CompanionLearningProfile::reliability),
                    Codec.unboundedMap(ID, PREFERENCE)
                            .optionalFieldOf("preferences", Map.of())
                            .forGetter(CompanionLearningProfile::preferences),
                    Codec.unboundedMap(ID, HABIT)
                            .optionalFieldOf("habits", Map.of())
                            .forGetter(CompanionLearningProfile::habits),
                    UUID_CODEC.listOf()
                            .optionalFieldOf("recent_signals", List.of())
                            .forGetter(
                                    CompanionLearningProfile::recentSignals
                            )
            ).apply(instance, CompanionLearningProfile::new));

    private CompanionLearningProfileCodec() {
    }

    private static DataResult<OrchestrationId> parseId(String value) {
        try {
            return DataResult.success(OrchestrationId.parse(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<UUID> parseUuid(String value) {
        try {
            return DataResult.success(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }
}
