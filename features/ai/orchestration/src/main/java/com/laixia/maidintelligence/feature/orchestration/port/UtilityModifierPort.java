package com.laixia.maidintelligence.feature.orchestration.port;

import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;

@FunctionalInterface
public interface UtilityModifierPort<M> {
    double modifier(
            M subject,
            IntentCatalog.CompiledIntent intent,
            long gameTime
    );

    static <M> UtilityModifierPort<M> noop() {
        return (subject, intent, gameTime) -> 0.0D;
    }
}
