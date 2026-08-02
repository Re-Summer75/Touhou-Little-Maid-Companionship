package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.api.IntentMetrics;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.port.IntentActionPort;
import com.laixia.maidintelligence.feature.orchestration.port.IntentCatalogPort;
import com.laixia.maidintelligence.feature.orchestration.port.IntentContextPort;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
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
    private final IntentContextPort<M> context;
    private final IntentActionPort<M> actions;
    private final IntentSelectionEngine<M> selection;
    private final BooleanSupplier enabled;
    private final IntSupplier evaluationIntervalTicks;
    private final IntSupplier maxCandidateEvaluations;
    private final BooleanSupplier diagnosticsEnabled;
    private final Map<M, MaidIntentRuntimeState> states = new WeakHashMap<>();
    private long evaluations;
    private long activations;
    private long switches;
    private long interruptions;
    private long completions;
    private long failures;
    private long cancellations;

    public DefaultMaidIntentOrchestrator(
            IntentCatalogPort catalogs,
            IntentContextPort<M> context,
            IntentActionPort<M> actions,
            ToLongFunction<M> identity,
            BooleanSupplier enabled,
            IntSupplier evaluationIntervalTicks
    ) {
        this(
                catalogs,
                context,
                actions,
                identity,
                enabled,
                evaluationIntervalTicks,
                () -> 128,
                () -> true
        );
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
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.context = Objects.requireNonNull(context, "context");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.selection = new IntentSelectionEngine<>(
                Objects.requireNonNull(identity, "identity")
        );
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.evaluationIntervalTicks = Objects.requireNonNull(
                evaluationIntervalTicks,
                "evaluationIntervalTicks"
        );
        this.maxCandidateEvaluations = Objects.requireNonNull(
                maxCandidateEvaluations,
                "maxCandidateEvaluations"
        );
        this.diagnosticsEnabled = Objects.requireNonNull(
                diagnosticsEnabled,
                "diagnosticsEnabled"
        );
    }

    @Override
    public boolean tick(M subject, long gameTime) {
        Objects.requireNonNull(subject, "subject");
        IntentCatalog catalog = catalogs.current();
        MaidIntentRuntimeState state = states.computeIfAbsent(
                subject,
                ignored -> new MaidIntentRuntimeState()
        );
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
            return false;
        }

        readFacts(subject, state, catalog, gameTime);
        IntentCatalog.CompiledIntent active =
                activeDefinition(state, catalog);
        if (active != null
                && !IntentSelectionEngine.eligibleWhileActive(
                        active,
                        state.facts,
                        catalog
                )) {
            interruptions++;
            cancelActive(subject, state, "active_blocked", true);
            active = null;
            state.dirty = true;
        }

        if (state.dirty || gameTime >= state.nextEvaluationTick) {
            evaluate(subject, state, catalog, gameTime);
            active = activeDefinition(state, catalog);
        }
        if (active == null) {
            return false;
        }
        return advance(subject, state, active, gameTime);
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
                        deadline(gameTime, ttlTicks)
                )
        );
        state.dirty = true;
        return true;
    }

    @Override
    public IntentTrace inspect(M subject) {
        MaidIntentRuntimeState state = states.get(subject);
        return state == null ? IntentTrace.idle() : state.trace;
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
        IntentCatalog catalog = catalogs.current();
        return new IntentMetrics(
                catalog.generation(),
                catalog.intents().size(),
                catalog.planCount(),
                evaluations,
                activations,
                switches,
                interruptions,
                completions,
                failures,
                cancellations
        );
    }

    @Override
    public void resetMetrics() {
        evaluations = 0L;
        activations = 0L;
        switches = 0L;
        interruptions = 0L;
        completions = 0L;
        failures = 0L;
        cancellations = 0L;
    }

    private void readFacts(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            long gameTime
    ) {
        int factCount = catalog.facts().size();
        if (state.facts.length != factCount) {
            state.facts = new double[factCount];
        }
        Arrays.fill(state.facts, Double.NaN);
        context.readFacts(
                subject,
                gameTime,
                catalog.facts(),
                state.facts
        );

        Iterator<Map.Entry<OrchestrationId,
                MaidIntentRuntimeState.SignalWindow>> iterator =
                state.signals.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<OrchestrationId,
                    MaidIntentRuntimeState.SignalWindow> entry =
                    iterator.next();
            if (!entry.getValue().activeAt(gameTime)) {
                iterator.remove();
            }
        }
        for (int index = 0; index < factCount; index++) {
            MaidIntentRuntimeState.SignalWindow signal =
                    state.signals.get(catalog.facts().get(index));
            if (signal != null && signal.activeAt(gameTime)) {
                state.facts[index] = 1.0D;
            }
        }
    }

    private void evaluate(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            long gameTime
    ) {
        evaluations++;
        state.dirty = false;
        state.nextEvaluationTick = deadline(
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
            updateTrace(state, catalog, result.trace(), result.status());
            return;
        }
        if (state.activeIntent == null) {
            start(state, catalog, winner, gameTime);
            updateTrace(state, catalog, result.trace(),
                    "activated:" + winner.intent().id());
            return;
        }
        if (winner.intent().id().equals(state.activeIntent)) {
            updateTrace(state, catalog, result.trace(), "retained");
            return;
        }

        IntentCatalog.CompiledIntent active =
                catalog.intent(state.activeIntent);
        if (active != null
                && !selection.canSwitch(state, active, winner, gameTime)) {
            updateTrace(state, catalog, result.trace(), "committed");
            return;
        }
        cancelActive(subject, state, "preempted", true);
        switches++;
        interruptions++;
        start(state, catalog, winner, gameTime);
        updateTrace(state, catalog, result.trace(),
                "switched:" + winner.intent().id());
    }

    private boolean advance(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent active,
            long gameTime
    ) {
        IntentCatalog.CompiledState step =
                active.plan().states().get(state.activeState);
        state.activeAction = step.action();
        state.activeParameters = step.parameters();
        long elapsed = Math.max(0L, gameTime - state.stateSinceTick);
        boolean timedOut = elapsed >= step.timeoutTicks();
        ActionResult result;
        if (timedOut) {
            actions.cancel(subject, step.action(), step.parameters());
            result = ActionResult.FAILED;
        } else {
            result = actions.execute(
                        subject,
                        step.action(),
                        step.parameters(),
                        gameTime,
                        (int) Math.min(Integer.MAX_VALUE, elapsed)
                );
        }
        if (result == ActionResult.RUNNING) {
            updateActiveTrace(state, active, step.id());
            return true;
        }

        int transition = switch (result) {
            case SUCCEEDED -> step.successState();
            case FAILED -> step.failureState();
            case CANCELLED -> step.cancellationState();
            case RUNNING -> throw new IllegalStateException(
                    "Running action reached terminal transition"
            );
        };
        if (transition == IntentCatalog.TERMINAL_SUCCESS) {
            finishActive(state, active, gameTime, true);
            return true;
        }
        if (transition == IntentCatalog.TERMINAL_FAILURE) {
            finishActive(state, active, gameTime, false);
            return true;
        }
        state.activeState = transition;
        state.stateSinceTick = gameTime;
        state.activeAction = null;
        state.activeParameters = Map.of();
        IntentCatalog.CompiledState next =
                active.plan().states().get(transition);
        updateActiveTrace(state, active, next.id());
        return true;
    }

    private void start(
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            IntentSelectionEngine.ScoredIntent selected,
            long gameTime
    ) {
        IntentCatalog.CompiledIntent intent = selected.intent();
        state.activeIntent = intent.id();
        state.activeState = intent.plan().initialState();
        state.activeSinceTick = gameTime;
        state.stateSinceTick = gameTime;
        state.committedUntilTick = deadline(
                gameTime,
                intent.definition().minimumCommitTicks()
        );
        state.activeScore = selected.score();
        IntentSelectionEngine.consumeSignals(state, catalog, intent);
        activations++;
    }

    private void finishActive(
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent active,
            long gameTime,
            boolean succeeded
    ) {
        state.cooldowns.put(
                active.id(),
                deadline(gameTime, active.definition().cooldownTicks())
        );
        String transition = succeeded
                ? "completed:" + active.id()
                : "failed:" + active.id();
        if (succeeded) {
            completions++;
        } else {
            failures++;
        }
        List<IntentTrace.Candidate> candidates = state.trace.candidates();
        state.clearActive();
        state.trace = new IntentTrace(
                null,
                "",
                -1L,
                candidates,
                transition
        );
        state.dirty = true;
    }

    private void cancelActive(
            M subject,
            MaidIntentRuntimeState state,
            String reason,
            boolean count
    ) {
        if (state.activeIntent == null) {
            return;
        }
        if (state.activeAction != null) {
            actions.cancel(
                    subject,
                    state.activeAction,
                    state.activeParameters
            );
        }
        OrchestrationId cancelled = state.activeIntent;
        List<IntentTrace.Candidate> candidates = state.trace.candidates();
        state.clearActive();
        state.trace = new IntentTrace(
                null,
                "",
                -1L,
                candidates,
                reason + ":" + cancelled
        );
        if (count) {
            cancellations++;
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

    private static void updateTrace(
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            List<IntentTrace.Candidate> candidates,
            String transition
    ) {
        String activeState = "";
        IntentCatalog.CompiledIntent active =
                activeDefinition(state, catalog);
        if (active != null) {
            activeState = active.plan()
                    .states()
                    .get(state.activeState)
                    .id();
        }
        state.trace = new IntentTrace(
                state.activeIntent,
                activeState,
                state.activeSinceTick,
                candidates,
                transition
        );
    }

    private static void updateActiveTrace(
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent active,
            String stateId
    ) {
        state.trace = new IntentTrace(
                active.id(),
                stateId,
                state.activeSinceTick,
                state.trace.candidates(),
                state.trace.lastTransition()
        );
    }

    private static long deadline(long gameTime, long duration) {
        return gameTime > Long.MAX_VALUE - duration
                ? Long.MAX_VALUE
                : gameTime + duration;
    }
}
