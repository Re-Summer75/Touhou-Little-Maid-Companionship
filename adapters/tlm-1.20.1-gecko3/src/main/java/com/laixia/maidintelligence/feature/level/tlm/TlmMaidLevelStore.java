package com.laixia.maidintelligence.feature.level.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import com.laixia.maidintelligence.feature.level.port.MaidLevelStore;

public final class TlmMaidLevelStore implements MaidLevelStore<EntityMaid> {
    @Override
    public LevelProgress get(EntityMaid maid) {
        LevelProgress progress = maid.getData(LevelTaskData.progressKey());
        if (progress != null) {
            return progress;
        }

        LevelProgress legacyProgress = maid.getData(LevelTaskData.legacyProgressKey());
        if (legacyProgress != null) {
            if (maid.level().isClientSide()) {
                maid.setData(LevelTaskData.progressKey(), legacyProgress);
            } else {
                maid.setAndSyncData(LevelTaskData.progressKey(), legacyProgress);
            }
            return legacyProgress;
        }

        return maid.getOrCreateData(LevelTaskData.progressKey(), LevelProgress.initial());
    }

    @Override
    public void set(EntityMaid maid, LevelProgress progress) {
        maid.setAndSyncData(LevelTaskData.progressKey(), progress);
    }
}
