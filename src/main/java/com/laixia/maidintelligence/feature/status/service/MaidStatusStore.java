package com.laixia.maidintelligence.feature.status.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;

public interface MaidStatusStore {
    MaidStatusState get(EntityMaid maid);

    void set(EntityMaid maid, MaidStatusState state);
}
