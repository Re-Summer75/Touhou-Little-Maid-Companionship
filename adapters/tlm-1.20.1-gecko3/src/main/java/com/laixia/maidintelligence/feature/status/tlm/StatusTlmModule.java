package com.laixia.maidintelligence.feature.status.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;

import java.util.Objects;

public final class StatusTlmModule implements TlmFeatureModule {
    private final TlmMaidStatusService statusService;

    public StatusTlmModule(TlmMaidStatusService statusService) {
        this.statusService = Objects.requireNonNull(
                statusService,
                "statusService"
        );
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        StatusTaskData.register(register);
    }

    @Override
    public void registerExtraBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new StatusExtraBrain(statusService));
    }
}
