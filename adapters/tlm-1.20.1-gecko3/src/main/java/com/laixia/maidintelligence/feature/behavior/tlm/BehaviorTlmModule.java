package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.tlm.CompanionTaskData;

import java.util.Objects;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

public final class BehaviorTlmModule implements TlmFeatureModule {
    /**
     * The one task this mod contributes. Registered here rather than in a task
     * module of its own, because what it does is entirely about behaviour:
     * it removes every competing behaviour so companion intents have the floor.
     */
    @Override
    public void registerTasks(TaskManager manager) {
        manager.add(new FreedomMaidTask());
    }

    private final MaidIntentApi<EntityMaid> intents;
    private final TlmDeployBoatAutonomy boatAutonomy;

    public BehaviorTlmModule(
            MaidIntentApi<EntityMaid> intents,
            TlmDeployBoatAutonomy boatAutonomy
    ) {
        this.intents = Objects.requireNonNull(
                intents,
                "intents"
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
                boatAutonomy
        ));
    }
}
