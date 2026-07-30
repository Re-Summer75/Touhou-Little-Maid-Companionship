package com.laixia.maidintelligence.feature.advancement.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.codec.MaidStatisticsCodec;
import com.laixia.maidintelligence.feature.advancement.domain.MaidStatistics;
import com.laixia.maidintelligence.feature.advancement.port.MaidStatisticsStore;
import com.laixia.maidintelligence.platform.resource.ModResources;

/**
 * 统计量的 TaskData 存取。存档与同步共用一份 Codec：统计量本身就很小，
 * 而且客户端要靠它显示阈值类进度的完成度。
 */
public final class MaidStatisticsData
        implements MaidStatisticsStore<EntityMaid> {
    private TaskDataKey<MaidStatistics> key;

    public void register(TaskDataRegister register) {
        if (key != null) {
            throw new IllegalStateException("Maid statistics task data has already been registered");
        }
        key = register.register(
                ModResources.id("maid_statistics"),
                MaidStatisticsCodec.CODEC
        );
    }

    public boolean isRegistered() {
        return key != null;
    }

    @Override
    public MaidStatistics get(EntityMaid maid) {
        if (key == null) {
            return MaidStatistics.empty();
        }
        MaidStatistics statistics = maid.getData(key);
        return statistics != null ? statistics : MaidStatistics.empty();
    }

    @Override
    public void set(EntityMaid maid, MaidStatistics statistics) {
        if (key == null) {
            return;
        }
        if (maid.level().isClientSide()) {
            maid.setData(key, statistics);
            return;
        }
        maid.setAndSyncData(key, statistics);
    }
}
