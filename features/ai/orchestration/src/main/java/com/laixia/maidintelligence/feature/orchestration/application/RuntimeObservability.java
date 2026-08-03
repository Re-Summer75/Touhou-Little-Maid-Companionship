package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.api.DecisionTrace;
import com.laixia.maidintelligence.feature.orchestration.application.observation.RuntimeIdentity;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.Belief;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.CompanionPersistentMemory;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.EpisodicEvent;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationOutcome;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationStatus;
import com.laixia.maidintelligence.feature.orchestration.port.CompanionMemoryPort;
import com.laixia.maidintelligence.feature.orchestration.port.OperationOutcomePort;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToLongFunction;

final class RuntimeObservability<M> {
    private static final UUID NONE = new UUID(0L, 0L);
    private final CompanionMemoryPort<M> memory;
    private final ToLongFunction<M> identity;
    private final OperationOutcomePort<M> outcomes;

    RuntimeObservability(
            CompanionMemoryPort<M> memory,
            ToLongFunction<M> identity,
            OperationOutcomePort<M> outcomes
    ) {
        this.memory = Objects.requireNonNull(memory, "memory");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.outcomes = Objects.requireNonNull(outcomes, "outcomes");
    }

    void initialize(
            M subject,
            MaidIntentRuntimeState state,
            long gameTime
    ) {
        if (state.memoryLoaded) {
            return;
        }
        CompanionPersistentMemory loaded = memory.load(subject);
        state.mailbox.restore(
                loaded == null
                        ? CompanionPersistentMemory.initial()
                        : loaded,
                gameTime
        );
        state.memoryLoaded = true;
    }

    void beginDecision(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            long gameTime
    ) {
        state.decisionId = RuntimeIdentity.decision(
                identity.applyAsLong(subject),
                catalog.generation(),
                gameTime,
                state.decisionSequence++
        );
        state.decisionAtTick = gameTime;
    }

    void startCorrelation(
            M subject,
            MaidIntentRuntimeState state,
            OrchestrationId intent,
            long gameTime
    ) {
        state.correlationId = RuntimeIdentity.correlation(
                identity.applyAsLong(subject),
                intent,
                gameTime,
                state.activationSequence++
        );
    }

    void ensureOperation(
            MaidIntentRuntimeState state,
            String stateId,
            long gameTime
    ) {
        if (state.activeOperationId != null) {
            return;
        }
        UUID correlation = state.correlationId == null
                ? NONE
                : state.correlationId;
        state.activeOperationId = RuntimeIdentity.operation(
                correlation,
                stateId,
                state.operationSequence++
        );
        state.operationStartedAtTick = gameTime;
    }

    boolean recordOutcome(
            M subject,
            MaidIntentRuntimeState state,
            OrchestrationId intent,
            OrchestrationId plan,
            String stateId,
            OrchestrationId action,
            ActionResult result,
            long gameTime,
            String detail
    ) {
        if (result == ActionResult.RUNNING
                || state.activeOperationId == null) {
            return false;
        }
        UUID correlation = state.correlationId == null
                ? NONE
                : state.correlationId;
        OperationStatus status = OperationStatus.from(result);
        OperationOutcome outcome = new OperationOutcome(
                RuntimeIdentity.outcome(
                        state.activeOperationId,
                        correlation,
                        status.name(),
                        gameTime
                ),
                intent,
                plan,
                stateId,
                action,
                status,
                state.operationStartedAtTick,
                gameTime,
                detail
        );
        boolean recorded = state.mailbox.record(outcome);
        if (recorded) {
            persist(subject, state, gameTime);
            try {
                outcomes.record(subject, outcome);
            } catch (RuntimeException ignored) {
                // Learning/telemetry is never allowed to break execution.
            }
        }
        return recorded;
    }

    boolean publishSignal(
            M subject,
            MaidIntentRuntimeState state,
            OrchestrationId signal,
            long gameTime,
            int ttlTicks
    ) {
        EpisodicEvent event = new EpisodicEvent(
                RuntimeIdentity.signal(
                        identity.applyAsLong(subject),
                        signal,
                        gameTime,
                        state.eventSequence++
                ),
                signal,
                gameTime,
                deadline(gameTime, ttlTicks),
                Map.of("source", "signal")
        );
        return state.mailbox.publish(event, gameTime);
    }

    boolean publishEvent(
            MaidIntentRuntimeState state,
            EpisodicEvent event,
            long gameTime
    ) {
        return state.mailbox.publish(event, gameTime);
    }

    boolean rememberBelief(
            M subject,
            MaidIntentRuntimeState state,
            Belief belief,
            long gameTime
    ) {
        boolean remembered = state.mailbox.remember(belief);
        if (remembered && belief.persistent()) {
            persist(subject, state, gameTime);
        }
        return remembered;
    }

    void updateDecision(
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            long gameTime
    ) {
        Map<OrchestrationId, Double> facts = new LinkedHashMap<>();
        int count = Math.min(catalog.facts().size(), state.facts.length);
        for (int index = 0; index < count; index++) {
            facts.put(catalog.facts().get(index), state.facts[index]);
        }
        state.decisionTrace = new DecisionTrace(
                state.decisionId,
                state.decisionAtTick,
                catalog.generation(),
                state.trace,
                facts,
                state.mailbox.snapshot(gameTime),
                state.activeOperationId == null
                        ? NONE
                        : state.activeOperationId,
                state.correlationId == null ? NONE : state.correlationId
        );
    }

    private void persist(
            M subject,
            MaidIntentRuntimeState state,
            long gameTime
    ) {
        memory.save(
                subject,
                state.mailbox.persistentMemory(gameTime)
        );
    }

    private static long deadline(long gameTime, long duration) {
        return gameTime > Long.MAX_VALUE - duration
                ? Long.MAX_VALUE
                : gameTime + duration;
    }
}
