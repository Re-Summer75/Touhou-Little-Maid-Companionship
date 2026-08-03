package com.laixia.maidintelligence.feature.behavior.port;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

@FunctionalInterface
public interface AbilitySignalPort<M> {
    boolean signal(
            M subject,
            OrchestrationId signal,
            long gameTime,
            int ttlTicks
    );
}
