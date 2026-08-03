package com.laixia.maidintelligence.feature.orchestration.application.observation;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.EventIdentity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class RuntimeIdentity {
    private RuntimeIdentity() {
    }

    public static EventIdentity signal(
            long subjectId,
            OrchestrationId signal,
            long gameTime,
            long sequence
    ) {
        return EventIdentity.root(id(
                "signal",
                subjectId,
                signal.toString(),
                gameTime,
                sequence
        ));
    }

    public static UUID decision(
            long subjectId,
            long catalogGeneration,
            long gameTime,
            long sequence
    ) {
        return id(
                "decision",
                subjectId,
                catalogGeneration,
                gameTime,
                sequence
        );
    }

    public static UUID correlation(
            long subjectId,
            OrchestrationId intent,
            long gameTime,
            long sequence
    ) {
        return id(
                "correlation",
                subjectId,
                intent.toString(),
                gameTime,
                sequence
        );
    }

    public static UUID operation(
            UUID correlationId,
            String state,
            long sequence
    ) {
        return id(
                "operation",
                correlationId,
                state,
                sequence
        );
    }

    public static EventIdentity outcome(
            UUID operationId,
            UUID correlationId,
            String status,
            long completedAtTick
    ) {
        UUID eventId = id(
                "outcome",
                operationId,
                status,
                completedAtTick
        );
        return new EventIdentity(
                eventId,
                operationId,
                correlationId,
                operationId
        );
    }

    private static UUID id(Object... parts) {
        StringBuilder value = new StringBuilder("tlm_companionship");
        for (Object part : parts) {
            value.append('\u001f').append(part);
        }
        return UUID.nameUUIDFromBytes(
                value.toString().getBytes(StandardCharsets.UTF_8)
        );
    }
}
