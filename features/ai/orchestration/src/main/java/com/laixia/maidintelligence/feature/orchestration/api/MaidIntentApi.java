package com.laixia.maidintelligence.feature.orchestration.api;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.Belief;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.EpisodicEvent;

public interface MaidIntentApi<M> {
    boolean tick(M subject, long gameTime);

    boolean signal(
            M subject,
            OrchestrationId signal,
            long gameTime,
            int ttlTicks
    );

    IntentTrace inspect(M subject);

    default IntentTrace inspectShadow(M subject) {
        return IntentTrace.idle();
    }

    default IntentTraceComparison compare(M subject) {
        return IntentTraceComparison.unavailable(inspect(subject));
    }

    default DecisionTrace inspectDecision(M subject) {
        return DecisionTrace.idle();
    }

    default CompanionObservationSnapshot observations(
            M subject,
            long gameTime
    ) {
        return CompanionObservationSnapshot.empty();
    }

    default boolean publishEvent(
            M subject,
            EpisodicEvent event,
            long gameTime
    ) {
        return false;
    }

    default boolean rememberBelief(
            M subject,
            Belief belief,
            long gameTime
    ) {
        return false;
    }

    void forget(M subject);

    IntentMetrics metrics();

    void resetMetrics();
}
