package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.tlm.CompanionTaskData;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentObserver;

import java.util.Objects;

public final class BehaviorTlmModule implements TlmFeatureModule {
    private final MaidIntentApi<EntityMaid> intents;
    private final TlmMaidIntentObserver observer;
    private final TlmDeployBoatAutonomy boatAutonomy;

    public BehaviorTlmModule(
            MaidIntentApi<EntityMaid> intents,
            TlmMaidIntentObserver observer,
            TlmDeployBoatAutonomy boatAutonomy
    ) {
        this.intents = Objects.requireNonNull(
                intents,
                "intents"
        );
        this.observer = Objects.requireNonNull(
                observer,
                "observer"
        );
        this.boatAutonomy = Objects.requireNonNull(
                boatAutonomy,
                "boatAutonomy"
        );
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        CompanionTaskData.register(register);
    }

    @Override
    public void registerExtraBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new BehaviorExtraBrain(
                intents,
                observer,
                boatAutonomy
        ));
    }
}
