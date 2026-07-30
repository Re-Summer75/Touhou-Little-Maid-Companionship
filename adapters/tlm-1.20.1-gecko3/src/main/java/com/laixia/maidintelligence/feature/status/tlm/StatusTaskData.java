package com.laixia.maidintelligence.feature.status.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;
import com.laixia.maidintelligence.platform.resource.ModResources;

public final class StatusTaskData {
    private static TaskDataKey<MaidStatusState> stateKey;
    private static TaskDataKey<MaidStatusState> legacyStateKey;

    private StatusTaskData() {
    }

    public static void register(TaskDataRegister register) {
        if (stateKey != null) {
            throw new IllegalStateException("Status task data has already been registered");
        }
        stateKey = register.register(ModResources.id("status_state"), MaidStatusState.CODEC);
        legacyStateKey = register.register(
                ModResources.legacyId("status_state"),
                MaidStatusState.CODEC
        );
    }

    public static TaskDataKey<MaidStatusState> stateKey() {
        if (stateKey == null) {
            throw new IllegalStateException("Status task data has not been registered yet");
        }
        return stateKey;
    }

    public static TaskDataKey<MaidStatusState> legacyStateKey() {
        if (legacyStateKey == null) {
            throw new IllegalStateException("Legacy status task data has not been registered yet");
        }
        return legacyStateKey;
    }
}
