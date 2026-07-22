package com.laixia.maidintelligence.feature.status.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;
import com.laixia.maidintelligence.feature.status.service.MaidStatusStore;

public final class TlmMaidStatusStore implements MaidStatusStore {
    @Override
    public MaidStatusState get(EntityMaid maid) {
        return maid.getOrCreateData(StatusTaskData.stateKey(), MaidStatusState.initial());
    }

    @Override
    public void set(EntityMaid maid, MaidStatusState state) {
        maid.setAndSyncData(StatusTaskData.stateKey(), state);
    }
}
