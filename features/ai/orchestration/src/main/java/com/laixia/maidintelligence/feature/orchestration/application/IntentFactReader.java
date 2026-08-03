package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.port.IntentContextPort;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

final class IntentFactReader<M> {
    private final IntentContextPort<M> context;

    IntentFactReader(IntentContextPort<M> context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    void read(
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
}
