package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.api.DecisionTrace;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.application.observation.BoundedCompanionMailbox;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.recovery.SuspendedPlanFrame;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

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
    OrchestrationId activePlan;
    String activeStateId = "";
    String lastCheckpointStateId = "";
    OrchestrationId activeAction;
    Map<String, String> activeParameters = Map.of();
    UUID activeOperationId;
    UUID correlationId;
    long operationStartedAtTick = -1L;
    long eventSequence;
    long activationSequence;
    long operationSequence;
    long decisionSequence;
    boolean memoryLoaded;
    UUID decisionId = new UUID(0L, 0L);
    long decisionAtTick = -1L;

    final Map<OrchestrationId, SignalWindow> signals = new HashMap<>();
    final Deque<SuspendedPlanFrame> suspendedPlans = new ArrayDeque<>();
    final Map<OrchestrationId, Long> cooldowns = new HashMap<>();
    final Map<OrchestrationId, Long> nextIntentEvaluation = new HashMap<>();
    final BoundedCompanionMailbox mailbox = new BoundedCompanionMailbox();
    IntentTrace trace = IntentTrace.idle();
    DecisionTrace decisionTrace = DecisionTrace.idle();

    void clearActive() {
        activeIntent = null;
        activeState = -1;
        activeSinceTick = -1L;
        stateSinceTick = -1L;
        committedUntilTick = 0L;
        activeScore = 0.0D;
        activePlan = null;
        activeStateId = "";
        lastCheckpointStateId = "";
        activeAction = null;
        activeParameters = Map.of();
        activeOperationId = null;
        correlationId = null;
        operationStartedAtTick = -1L;
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
        suspendedPlans.clear();
        trace = IntentTrace.idle();
        decisionTrace = DecisionTrace.idle();
        decisionId = new UUID(0L, 0L);
        decisionAtTick = -1L;
    }

    boolean hasSuspended(OrchestrationId intent) {
        return suspendedPlans.stream()
                .anyMatch(frame -> frame.intent().equals(intent));
    }

    SuspendedPlanFrame takeSuspended(OrchestrationId intent) {
        Iterator<SuspendedPlanFrame> iterator =
                suspendedPlans.iterator();
        while (iterator.hasNext()) {
            SuspendedPlanFrame frame = iterator.next();
            if (frame.intent().equals(intent)) {
                iterator.remove();
                return frame;
            }
        }
        return null;
    }

    record SignalWindow(long startedAtTick, long expiresAtTick) {
        boolean activeAt(long gameTime) {
            return gameTime >= startedAtTick && gameTime < expiresAtTick;
        }
    }
}
