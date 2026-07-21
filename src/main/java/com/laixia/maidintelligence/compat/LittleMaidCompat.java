package com.laixia.maidintelligence.compat;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.core.feature.FeatureCatalog;

/**
 * 车万女仆扩展入口。后续的工作模式、AI 行为和互动功能统一从这里注册。
 */
@LittleMaidExtension
public final class LittleMaidCompat implements ILittleMaid {
    public LittleMaidCompat() {
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        FeatureCatalog.tlmFeatures().forEach(feature -> feature.registerTaskData(register));
    }

    @Override
    public void addMaidTask(TaskManager manager) {
        FeatureCatalog.tlmFeatures().forEach(feature -> feature.registerTasks(manager));
    }

    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        FeatureCatalog.tlmFeatures().forEach(feature -> feature.registerExtraBrain(manager));
    }
}
