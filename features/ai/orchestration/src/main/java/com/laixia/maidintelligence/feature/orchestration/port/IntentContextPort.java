package com.laixia.maidintelligence.feature.orchestration.port;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.List;

@FunctionalInterface
public interface IntentContextPort<M> {
    void readFacts(
            M subject,
            long gameTime,
            List<OrchestrationId> facts,
            double[] output
    );
}
