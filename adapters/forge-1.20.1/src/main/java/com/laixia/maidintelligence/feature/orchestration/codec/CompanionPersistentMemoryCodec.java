package com.laixia.maidintelligence.feature.orchestration.codec;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.Belief;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.CompanionPersistentMemory;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.EventIdentity;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationOutcome;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationStatus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.UUID;

/**
 * Mojang codecs remain in the platform adapter while domain memory stays pure.
 */
public final class CompanionPersistentMemoryCodec {
    private static final Codec<OrchestrationId> ID =
            Codec.STRING.comapFlatMap(
                    CompanionPersistentMemoryCodec::parseId,
                    OrchestrationId::toString
            );
    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.comapFlatMap(
                    CompanionPersistentMemoryCodec::parseUuid,
                    UUID::toString
            );
    private static final Codec<OperationStatus> STATUS =
            Codec.STRING.comapFlatMap(
                    CompanionPersistentMemoryCodec::parseStatus,
                    OperationStatus::name
            );
    private static final Codec<EventIdentity> EVENT_IDENTITY =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUID_CODEC.fieldOf("event_id")
                            .forGetter(EventIdentity::eventId),
                    UUID_CODEC.fieldOf("operation_id")
                            .forGetter(EventIdentity::operationId),
                    UUID_CODEC.fieldOf("correlation_id")
                            .forGetter(EventIdentity::correlationId),
                    UUID_CODEC.fieldOf("causation_id")
                            .forGetter(EventIdentity::causationId)
            ).apply(instance, EventIdentity::new));
    private static final Codec<Belief> BELIEF =
            RecordCodecBuilder.create(instance -> instance.group(
                    ID.fieldOf("key").forGetter(Belief::key),
                    Codec.DOUBLE.fieldOf("value").forGetter(Belief::value),
                    ID.fieldOf("source").forGetter(Belief::source),
                    Codec.DOUBLE.fieldOf("confidence")
                            .forGetter(Belief::confidence),
                    Codec.LONG.fieldOf("observed_at")
                            .forGetter(Belief::observedAtTick),
                    Codec.LONG.fieldOf("expires_at")
                            .forGetter(Belief::expiresAtTick),
                    Codec.BOOL.optionalFieldOf("persistent", true)
                            .forGetter(Belief::persistent)
            ).apply(instance, Belief::new));
    private static final Codec<OperationOutcome> OUTCOME =
            RecordCodecBuilder.create(instance -> instance.group(
                    EVENT_IDENTITY.fieldOf("identity")
                            .forGetter(OperationOutcome::identity),
                    ID.fieldOf("intent")
                            .forGetter(OperationOutcome::intent),
                    ID.fieldOf("plan")
                            .forGetter(OperationOutcome::plan),
                    Codec.STRING.fieldOf("state")
                            .forGetter(OperationOutcome::state),
                    ID.fieldOf("action")
                            .forGetter(OperationOutcome::action),
                    STATUS.fieldOf("status")
                            .forGetter(OperationOutcome::status),
                    Codec.LONG.fieldOf("started_at")
                            .forGetter(OperationOutcome::startedAtTick),
                    Codec.LONG.fieldOf("completed_at")
                            .forGetter(OperationOutcome::completedAtTick),
                    Codec.STRING.optionalFieldOf("detail", "")
                            .forGetter(OperationOutcome::detail)
            ).apply(instance, OperationOutcome::new));

    public static final Codec<CompanionPersistentMemory> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf(
                            "schema_version",
                            CompanionPersistentMemory.CURRENT_SCHEMA_VERSION
                    ).forGetter(CompanionPersistentMemory::schemaVersion),
                    BELIEF.listOf().optionalFieldOf("beliefs", List.of())
                            .forGetter(CompanionPersistentMemory::beliefs),
                    OUTCOME.listOf().optionalFieldOf("outcomes", List.of())
                            .forGetter(CompanionPersistentMemory::outcomes)
            ).apply(instance, CompanionPersistentMemory::new));

    private CompanionPersistentMemoryCodec() {
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
            return DataResult.error(() -> "Invalid UUID: " + value);
        }
    }

    private static DataResult<OperationStatus> parseStatus(String value) {
        try {
            return DataResult.success(OperationStatus.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(
                    () -> "Invalid operation status: " + value
            );
        }
    }
}
