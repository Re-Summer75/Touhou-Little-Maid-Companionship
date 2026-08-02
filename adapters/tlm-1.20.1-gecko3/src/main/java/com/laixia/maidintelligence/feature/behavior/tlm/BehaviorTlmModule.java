package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentObserver;

import java.util.Objects;

public final class BehaviorTlmModule implements TlmFeatureModule {
    private final MaidIntentApi<EntityMaid> intents;
    private final TlmMaidIntentObserver observer;

    public BehaviorTlmModule(
            MaidIntentApi<EntityMaid> intents,
            TlmMaidIntentObserver observer
    ) {
        this.intents = Objects.requireNonNull(
                intents,
                "intents"
        );
        this.observer = Objects.requireNonNull(
                observer,
                "observer"
        );
    }

    @Override
    public void registerExtraBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new BehaviorExtraBrain(
                intents,
                observer
        ));
    }
}
