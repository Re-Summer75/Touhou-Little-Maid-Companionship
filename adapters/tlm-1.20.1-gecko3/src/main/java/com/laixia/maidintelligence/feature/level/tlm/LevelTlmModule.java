package com.laixia.maidintelligence.feature.level.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;

public final class LevelTlmModule implements TlmFeatureModule {
    @Override
    public void registerTaskData(TaskDataRegister register) {
        LevelTaskData.register(register);
    }
}
