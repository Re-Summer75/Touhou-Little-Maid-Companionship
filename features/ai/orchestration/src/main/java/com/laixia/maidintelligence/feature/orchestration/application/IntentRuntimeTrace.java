package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.List;

final class IntentRuntimeTrace {
    private IntentRuntimeTrace() {
    }

    static void selection(
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

    static void active(
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent intent,
            String stateId
    ) {
        state.trace = new IntentTrace(
                intent.id(),
                stateId,
                state.activeSinceTick,
                state.trace.candidates(),
                state.trace.lastTransition()
        );
    }

    static void terminal(
            MaidIntentRuntimeState state,
            String transition
    ) {
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

    static void suspended(
            MaidIntentRuntimeState state,
            String reason
    ) {
        List<IntentTrace.Candidate> candidates = state.trace.candidates();
        OrchestrationId intent = state.activeIntent;
        state.clearActive();
        state.trace = new IntentTrace(
                null,
                "",
                -1L,
                candidates,
                "suspended:" + reason + ":" + intent
        );
        state.dirty = true;
    }

    static OrchestrationId cancelled(
            MaidIntentRuntimeState state,
            String reason
    ) {
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
        return cancelled;
    }

    static long deadline(long gameTime, long duration) {
        return gameTime > Long.MAX_VALUE - duration
                ? Long.MAX_VALUE
                : gameTime + duration;
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
