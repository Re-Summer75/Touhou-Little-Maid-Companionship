package com.laixia.maidintelligence.feature.level.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import com.laixia.maidintelligence.platform.resource.ModResources;

public final class LevelTaskData {
    private static TaskDataKey<LevelProgress> progressKey;
    private static TaskDataKey<LevelProgress> legacyProgressKey;

    private LevelTaskData() {
    }

    public static void register(TaskDataRegister register) {
        if (progressKey != null) {
            throw new IllegalStateException("Level task data has already been registered");
        }
        progressKey = register.register(ModResources.id("level_progress"), LevelProgress.CODEC);
        legacyProgressKey = register.register(
                ModResources.legacyId("level_progress"),
                LevelProgress.CODEC
        );
    }

    public static TaskDataKey<LevelProgress> progressKey() {
        if (progressKey == null) {
            throw new IllegalStateException("Level task data has not been registered yet");
        }
        return progressKey;
    }

    public static TaskDataKey<LevelProgress> legacyProgressKey() {
        if (legacyProgressKey == null) {
            throw new IllegalStateException("Legacy level task data has not been registered yet");
        }
        return legacyProgressKey;
    }
}
