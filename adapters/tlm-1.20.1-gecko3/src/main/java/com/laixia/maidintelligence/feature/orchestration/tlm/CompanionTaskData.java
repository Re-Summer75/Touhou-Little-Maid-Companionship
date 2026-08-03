package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.laixia.maidintelligence.feature.orchestration.codec.CompanionPersistentMemoryCodec;
import com.laixia.maidintelligence.feature.behavior.codec.AbilityGrantSetCodec;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityGrantSet;
import com.laixia.maidintelligence.feature.behavior.codec.CompanionLearningProfileCodec;
import com.laixia.maidintelligence.feature.behavior.domain.learning.CompanionLearningProfile;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.CompanionPersistentMemory;
import com.laixia.maidintelligence.platform.resource.ModResources;

public final class CompanionTaskData {
    private static TaskDataKey<CompanionPersistentMemory> memoryKey;
    private static TaskDataKey<AbilityGrantSet> abilityGrantsKey;
    private static TaskDataKey<CompanionLearningProfile> learningProfileKey;

    private CompanionTaskData() {
    }

    public static void register(TaskDataRegister register) {
        if (memoryKey != null) {
            throw new IllegalStateException(
                    "Companion task data has already been registered"
            );
        }
        memoryKey = register.register(
                ModResources.id("companion_memory"),
                CompanionPersistentMemoryCodec.CODEC
        );
        abilityGrantsKey = register.register(
                ModResources.id("ability_grants"),
                AbilityGrantSetCodec.CODEC
        );
        learningProfileKey = register.register(
                ModResources.id("companion_learning"),
                CompanionLearningProfileCodec.CODEC
        );
    }

    public static TaskDataKey<CompanionPersistentMemory> memoryKey() {
        if (memoryKey == null) {
            throw new IllegalStateException(
                    "Companion task data has not been registered yet"
            );
        }
        return memoryKey;
    }

    public static TaskDataKey<AbilityGrantSet> abilityGrantsKey() {
        if (abilityGrantsKey == null) {
            throw new IllegalStateException(
                    "Companion task data has not been registered yet"
            );
        }
        return abilityGrantsKey;
    }

    public static TaskDataKey<CompanionLearningProfile> learningProfileKey() {
        if (learningProfileKey == null) {
            throw new IllegalStateException(
                    "Companion task data has not been registered yet"
            );
        }
        return learningProfileKey;
    }
}
