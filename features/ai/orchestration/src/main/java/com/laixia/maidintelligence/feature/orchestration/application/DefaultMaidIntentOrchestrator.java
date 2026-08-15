package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.api.CompanionObservationSnapshot;
import com.laixia.maidintelligence.feature.orchestration.api.DecisionTrace;
import com.laixia.maidintelligence.feature.orchestration.api.IntentMetrics;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.Belief;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.EpisodicEvent;
import com.laixia.maidintelligence.feature.orchestration.port.CompanionMemoryPort;
import com.laixia.maidintelligence.feature.orchestration.port.IntentActionPort;
import com.laixia.maidintelligence.feature.orchestration.port.IntentCatalogPort;
import com.laixia.maidintelligence.feature.orchestration.port.IntentContextPort;
import com.laixia.maidintelligence.feature.orchestration.port.OperationOutcomePort;
import com.laixia.maidintelligence.feature.orchestration.port.UtilityModifierPort;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.ToLongFunction;

public final class DefaultMaidIntentOrchestrator<M>
        implements MaidIntentApi<M> {
    private static final int MAX_SIGNALS_PER_SUBJECT = 32;
    private final IntentCatalogPort catalogs;
    private final IntentFactReader<M> facts;
    private final IntentSelectionEngine<M> selection;
    private final RuntimeObservability<M> observability;
    private final IntentPlanExecutor<M> executor;
    private final IntentActivationCoordinator<M> activation;
    private final BooleanSupplier enabled;
    private final IntSupplier evaluationIntervalTicks;
    private final IntSupplier maxCandidateEvaluations;
    private final BooleanSupplier diagnosticsEnabled;
    private final Map<M, MaidIntentRuntimeState> states = new WeakHashMap<>();
    private final IntentMetricsTracker metrics = new IntentMetricsTracker();

    public DefaultMaidIntentOrchestrator(
            IntentCatalogPort catalogs,
            IntentContextPort<M> context,
            IntentActionPort<M> actions,
            ToLongFunction<M> identity,
            BooleanSupplier enabled,
            IntSupplier evaluationIntervalTicks
    ) {
        this(catalogs, context, actions, identity, enabled,
                evaluationIntervalTicks,
                () -> 128, () -> true, CompanionMemoryPort.noop());
    }

    public DefaultMaidIntentOrchestrator(
            IntentCatalogPort catalogs,
            IntentContextPort<M> context,
            IntentActionPort<M> actions,
            ToLongFunction<M> identity,
            BooleanSupplier enabled,
            IntSupplier evaluationIntervalTicks,
            IntSupplier maxCandidateEvaluations,
            BooleanSupplier diagnosticsEnabled
    ) {
        this(catalogs, context, actions, identity, enabled,
                evaluationIntervalTicks, maxCandidateEvaluations,
                diagnosticsEnabled, CompanionMemoryPort.noop());
    }

    public DefaultMaidIntentOrchestrator(
            IntentCatalogPort catalogs,
            IntentContextPort<M> context,
            IntentActionPort<M> actions,
            ToLongFunction<M> identity,
            BooleanSupplier enabled,
            IntSupplier evaluationIntervalTicks,
            IntSupplier maxCandidateEvaluations,
            BooleanSupplier diagnosticsEnabled,
            CompanionMemoryPort<M> memory
    ) {
        this(catalogs, context, actions, identity, enabled,
                evaluationIntervalTicks, maxCandidateEvaluations,
                diagnosticsEnabled, memory, OperationOutcomePort.noop(),
                UtilityModifierPort.noop());
    }

    public DefaultMaidIntentOrchestrator(
            IntentCatalogPort catalogs,
            IntentContextPort<M> context,
            IntentActionPort<M> actions,
            ToLongFunction<M> identity,
            BooleanSupplier enabled,
            IntSupplier evaluationIntervalTicks,
            IntSupplier maxCandidateEvaluations,
            BooleanSupplier diagnosticsEnabled,
            CompanionMemoryPort<M> memory,
            OperationOutcomePort<M> outcomes,
            UtilityModifierPort<M> modifiers
    ) {
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.facts = new IntentFactReader<>(context);
        IntentActionPort<M> checkedActions = Objects.requireNonNull(
                actions, "actions");
        ToLongFunction<M> checkedIdentity = Objects.requireNonNull(
                identity, "identity");
        this.selection = new IntentSelectionEngine<>(
                checkedIdentity,
                Objects.requireNonNull(modifiers, "modifiers")
        );
        this.observability = new RuntimeObservability<>(
                Objects.requireNonNull(memory, "memory"),
                checkedIdentity,
                Objects.requireNonNull(outcomes, "outcomes")
        );
        this.executor = new IntentPlanExecutor<>(checkedActions, observability);
        this.activation = new IntentActivationCoordinator<>(
                executor, observability);
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.evaluationIntervalTicks = Objects.requireNonNull(
                evaluationIntervalTicks, "evaluationIntervalTicks");
        this.maxCandidateEvaluations = Objects.requireNonNull(
                maxCandidateEvaluations, "maxCandidateEvaluations");
        this.diagnosticsEnabled = Objects.requireNonNull(
                diagnosticsEnabled, "diagnosticsEnabled");
    }

    @Override
    public boolean tick(M subject, long gameTime) {
        Objects.requireNonNull(subject, "subject");
        IntentCatalog catalog = catalogs.current();
        MaidIntentRuntimeState state = states.computeIfAbsent(
                subject,
                ignored -> new MaidIntentRuntimeState()
        );
        observability.initialize(subject, state, gameTime);
        boolean clockRolledBack = state.lastTick != Long.MIN_VALUE
                && gameTime < state.lastTick;
        boolean catalogChanged =
                state.catalogGeneration != catalog.generation();
        if (clockRolledBack || catalogChanged) {
            cancelActive(subject, state, "runtime_reset", false);
            state.reset(catalog.generation(), gameTime);
        }
        state.lastTick = gameTime;

        if (!enabled.getAsBoolean() || catalog.intents().isEmpty()) {
            cancelActive(subject, state, "disabled", false);
            executor.abortSuspended(state);
            observability.updateDecision(state, catalog, gameTime);
            return false;
        }

        facts.read(subject, state, catalog, gameTime);
        executor.pruneSuspended(state, catalog, gameTime);
        IntentCatalog.CompiledIntent active =
                activeDefinition(state, catalog);
        if (active != null
                && !IntentSelectionEngine.eligibleWhileActive(
                        active,
                        state.facts,
                        catalog
                )) {
            metrics.interrupted();
            interruptActive(
                    subject,
                    state,
                    active,
                    catalog,
                    gameTime,
                    "active_blocked"
            );
            active = null;
            state.dirty = true;
        }

        if (state.dirty || gameTime >= state.nextEvaluationTick) {
            evaluate(subject, state, catalog, gameTime);
            active = activeDefinition(state, catalog);
        }
        if (active == null) {
            observability.updateDecision(state, catalog, gameTime);
            return false;
        }
        IntentPlanExecutor.AdvanceResult result =
                executor.advance(subject, state, active, gameTime);
        if (result == IntentPlanExecutor.AdvanceResult.COMPLETED) {
            metrics.completed();
        } else if (result == IntentPlanExecutor.AdvanceResult.FAILED) {
            metrics.failed();
        }
        observability.updateDecision(state, catalog, gameTime);
        return true;
    }

    @Override
    public boolean signal(
            M subject,
            OrchestrationId signal,
            long gameTime,
            int ttlTicks
    ) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(signal, "signal");
        if (ttlTicks < 1 || ttlTicks > 12_000) {
            return false;
        }
        MaidIntentRuntimeState state = states.computeIfAbsent(
                subject,
                ignored -> new MaidIntentRuntimeState()
        );
        observability.initialize(subject, state, gameTime);
        IntentCatalog catalog = catalogs.current();
        if (state.catalogGeneration != catalog.generation()
                || (state.lastTick != Long.MIN_VALUE
                && gameTime < state.lastTick)) {
            cancelActive(subject, state, "signal_runtime_reset", false);
            state.reset(catalog.generation(), gameTime);
        }
        if (!state.signals.containsKey(signal)
                && state.signals.size() >= MAX_SIGNALS_PER_SUBJECT) {
            return false;
        }
        state.signals.put(
                signal,
                new MaidIntentRuntimeState.SignalWindow(
                        gameTime,
                        IntentRuntimeTrace.deadline(gameTime, ttlTicks)
                )
        );
        state.dirty = true;
        observability.publishSignal(
                subject,
                state,
                signal,
                gameTime,
                ttlTicks
        );
        observability.updateDecision(state, catalog, gameTime);
        return true;
    }

    @Override
    public IntentTrace inspect(M subject) {
        MaidIntentRuntimeState state = states.get(subject);
        return state == null ? IntentTrace.idle() : state.trace;
    }

    @Override
    public DecisionTrace inspectDecision(M subject) {
        MaidIntentRuntimeState state = states.get(subject);
        return state == null ? DecisionTrace.idle() : state.decisionTrace;
    }

    @Override
    public CompanionObservationSnapshot observations(
            M subject,
            long gameTime
    ) {
        MaidIntentRuntimeState state = states.get(subject);
        return state == null
                ? CompanionObservationSnapshot.empty()
                : state.mailbox.snapshot(gameTime);
    }

    @Override
    public boolean publishEvent(
            M subject,
            EpisodicEvent event,
            long gameTime
    ) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(event, "event");
        MaidIntentRuntimeState state = states.computeIfAbsent(
                subject,
                ignored -> new MaidIntentRuntimeState()
        );
        observability.initialize(subject, state, gameTime);
        boolean published = observability.publishEvent(
                state,
                event,
                gameTime
        );
        observability.updateDecision(state, catalogs.current(), gameTime);
        return published;
    }

    @Override
    public boolean rememberBelief(
            M subject,
            Belief belief,
            long gameTime
    ) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(belief, "belief");
        MaidIntentRuntimeState state = states.computeIfAbsent(
                subject,
                ignored -> new MaidIntentRuntimeState()
        );
        observability.initialize(subject, state, gameTime);
        boolean remembered = observability.rememberBelief(
                subject,
                state,
                belief,
                gameTime
        );
        observability.updateDecision(state, catalogs.current(), gameTime);
        return remembered;
    }

    @Override
    public void forget(M subject) {
        MaidIntentRuntimeState state = states.remove(subject);
        if (state != null) {
            cancelActive(subject, state, "forgotten", false);
        }
    }

    @Override
    public IntentMetrics metrics() {
        return metrics.snapshot(catalogs.current());
    }

    @Override
    public void resetMetrics() {
        metrics.reset();
    }

    private void evaluate(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            long gameTime
    ) {
        metrics.evaluated();
        observability.beginDecision(subject, state, catalog, gameTime);
        state.dirty = false;
        state.nextEvaluationTick = IntentRuntimeTrace.deadline(
                gameTime,
                Math.max(1, evaluationIntervalTicks.getAsInt())
        );
        IntentSelectionEngine.Selection result = selection.select(
                subject,
                state,
                catalog,
                gameTime,
                Math.max(1, maxCandidateEvaluations.getAsInt()),
                diagnosticsEnabled.getAsBoolean()
        );
        IntentSelectionEngine.ScoredIntent winner = result.winner();
        if (winner == null) {
            IntentRuntimeTrace.selection(
                    state,
                    catalog,
                    result.trace(),
                    result.status()
            );
            return;
        }
        if (state.activeIntent == null) {
            String transition = activateSelected(
                    subject,
                    state,
                    catalog,
                    winner,
                    gameTime
            );
            IntentRuntimeTrace.selection(
                    state,
                    catalog,
                    result.trace(),
                    transition
            );
            return;
        }
        if (winner.intent().id().equals(state.activeIntent)) {
            IntentRuntimeTrace.selection(
                    state,
                    catalog,
                    result.trace(),
                    "retained"
            );
            return;
        }

        IntentCatalog.CompiledIntent active =
                catalog.intent(state.activeIntent);
        if (active != null
                && !selection.canSwitch(state, active, winner, gameTime)) {
            IntentRuntimeTrace.selection(
                    state,
                    catalog,
                    result.trace(),
                    "committed"
            );
            return;
        }
        IntentActivationCoordinator.InterruptResult interrupted =
                activation.interrupt(
                        subject,
                        state,
                        active,
                        catalog,
                        gameTime,
                        "preempted"
                );
        if (interrupted
                == IntentActivationCoordinator.InterruptResult.CANCELLED) {
            metrics.cancelled();
        }
        metrics.switched();
        metrics.interrupted();
        String transition = activateSelected(
                subject,
                state,
                catalog,
                winner,
                gameTime
        );
        IntentRuntimeTrace.selection(
                state,
                catalog,
                result.trace(),
                "switched:" + transition
        );
    }

    private String activateSelected(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            IntentSelectionEngine.ScoredIntent selected,
            long gameTime
    ) {
        IntentActivationCoordinator.StartResult result = activation.start(
                subject,
                state,
                catalog,
                selected,
                gameTime
        );
        if (result == IntentActivationCoordinator.StartResult.ACTIVATED) {
            metrics.activated();
            return "activated:" + selected.intent().id();
        }
        return "resumed:" + selected.intent().id();
    }

    private void cancelActive(
            M subject,
            MaidIntentRuntimeState state,
            String reason,
            boolean count
    ) {
        boolean cancelled = executor.cancelActive(subject, state, reason);
        if (cancelled && count) {
            metrics.cancelled();
        }
    }

    private void interruptActive(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent active,
            IntentCatalog catalog,
            long gameTime,
            String reason
    ) {
        IntentActivationCoordinator.InterruptResult result =
                activation.interrupt(
                        subject,
                        state,
                        active,
                        catalog,
                        gameTime,
                        reason
                );
        if (result
                == IntentActivationCoordinator.InterruptResult.CANCELLED) {
            metrics.cancelled();
        }
    }

    private static IntentCatalog.CompiledIntent activeDefinition(
            MaidIntentRuntimeState state,
            IntentCatalog catalog
    ) {
        return state.activeIntent == null
                ? null
                : catalog.intent(state.activeIntent);
    }

}
