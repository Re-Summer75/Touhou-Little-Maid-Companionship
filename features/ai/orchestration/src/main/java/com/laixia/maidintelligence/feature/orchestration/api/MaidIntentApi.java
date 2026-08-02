package com.laixia.maidintelligence.feature.orchestration.api;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

public interface MaidIntentApi<M> {
    boolean tick(M subject, long gameTime);

    boolean signal(
            M subject,
            OrchestrationId signal,
            long gameTime,
            int ttlTicks
    );

    IntentTrace inspect(M subject);

    void forget(M subject);

    IntentMetrics metrics();

    void resetMetrics();
}
