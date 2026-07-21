package com.laixia.maidintelligence.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

/**
 * 特性与车万女仆注册系统之间的窄适配接口。
 */
public interface TlmFeatureModule {
    default void registerTaskData(TaskDataRegister register) {
    }

    default void registerTasks(TaskManager manager) {
    }

    default void registerExtraBrain(ExtraMaidBrainManager manager) {
    }
}
