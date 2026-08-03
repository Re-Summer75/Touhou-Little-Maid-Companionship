package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.recovery.SuspendedPlanFrame;

import java.util.Objects;

final class IntentActivationCoordinator<M> {
    private static final int FAILED_RESUME_BACKOFF_TICKS = 20;
    private final IntentPlanExecutor<M> executor;
    private final RuntimeObservability<M> observability;

    IntentActivationCoordinator(
            IntentPlanExecutor<M> executor,
            RuntimeObservability<M> observability
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.observability = Objects.requireNonNull(
                observability,
                "observability"
        );
    }

    StartResult start(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            IntentSelectionEngine.ScoredIntent selected,
            long gameTime
    ) {
        IntentCatalog.CompiledIntent intent = selected.intent();
        SuspendedPlanFrame frame = state.takeSuspended(intent.id());
        if (frame != null) {
            if (executor.resume(
                    subject,
                    state,
                    intent,
                    frame,
                    catalog.generation(),
                    gameTime
            )) {
                state.activeScore = selected.score();
                return StartResult.RESUMED;
            }
            state.cooldowns.put(
                    intent.id(),
                    IntentRuntimeTrace.deadline(
                            gameTime,
                            FAILED_RESUME_BACKOFF_TICKS
                    )
            );
            return StartResult.ABORTED;
        }

        state.activeIntent = intent.id();
        state.activePlan = intent.plan().id();
        state.activeState = intent.plan().initialState();
        state.activeSinceTick = gameTime;
        state.stateSinceTick = gameTime;
        state.committedUntilTick = IntentRuntimeTrace.deadline(
                gameTime,
                intent.definition().minimumCommitTicks()
        );
        state.activeScore = selected.score();
        if (intent.plan().checkpoints().contains(state.activeState)) {
            state.lastCheckpointStateId = intent.plan()
                    .states()
                    .get(state.activeState)
                    .id();
        }
        observability.startCorrelation(
                subject,
                state,
                intent.id(),
                gameTime
        );
        IntentSelectionEngine.consumeSignals(state, catalog, intent);
        return StartResult.ACTIVATED;
    }

    InterruptResult interrupt(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent active,
            IntentCatalog catalog,
            long gameTime,
            String reason
    ) {
        if (active == null) {
            return InterruptResult.NONE;
        }
        if (executor.suspendActive(
                subject,
                state,
                active,
                catalog.generation(),
                gameTime,
                reason
        )) {
            return InterruptResult.SUSPENDED;
        }
        return executor.cancelActive(subject, state, reason)
                ? InterruptResult.CANCELLED
                : InterruptResult.NONE;
    }

    enum StartResult {
        ACTIVATED,
        RESUMED,
        ABORTED
    }

    enum InterruptResult {
        SUSPENDED,
        CANCELLED,
        NONE
    }
}
