package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.HashMap;
import java.util.Map;

final class MaidIntentRuntimeState {
    long catalogGeneration = Long.MIN_VALUE;
    long lastTick = Long.MIN_VALUE;
    long nextEvaluationTick;
    int candidateCursor = -1;
    boolean dirty = true;
    double[] facts = new double[0];

    OrchestrationId activeIntent;
    int activeState = -1;
    long activeSinceTick = -1L;
    long stateSinceTick = -1L;
    long committedUntilTick;
    double activeScore;
    OrchestrationId activeAction;
    Map<String, String> activeParameters = Map.of();

    final Map<OrchestrationId, SignalWindow> signals = new HashMap<>();
    final Map<OrchestrationId, Long> cooldowns = new HashMap<>();
    final Map<OrchestrationId, Long> nextIntentEvaluation = new HashMap<>();
    IntentTrace trace = IntentTrace.idle();

    void clearActive() {
        activeIntent = null;
        activeState = -1;
        activeSinceTick = -1L;
        stateSinceTick = -1L;
        committedUntilTick = 0L;
        activeScore = 0.0D;
        activeAction = null;
        activeParameters = Map.of();
    }

    void reset(long generation, long gameTime) {
        catalogGeneration = generation;
        lastTick = gameTime;
        nextEvaluationTick = gameTime;
        candidateCursor = -1;
        dirty = true;
        facts = new double[0];
        clearActive();
        signals.clear();
        cooldowns.clear();
        nextIntentEvaluation.clear();
        trace = IntentTrace.idle();
    }

    record SignalWindow(long startedAtTick, long expiresAtTick) {
        boolean activeAt(long gameTime) {
            return gameTime >= startedAtTick && gameTime < expiresAtTick;
        }
    }
}
