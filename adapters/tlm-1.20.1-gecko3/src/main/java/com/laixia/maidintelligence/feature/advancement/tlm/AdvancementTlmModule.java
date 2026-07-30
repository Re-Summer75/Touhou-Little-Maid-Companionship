package com.laixia.maidintelligence.feature.advancement.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;

import java.util.Objects;

public final class AdvancementTlmModule implements TlmFeatureModule {
    private final MaidStatisticsData statisticsData;

    public AdvancementTlmModule(MaidStatisticsData statisticsData) {
        this.statisticsData = Objects.requireNonNull(
                statisticsData,
                "statisticsData"
        );
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        statisticsData.register(register);
        LegacyAchievementData.register(register);
    }
}
