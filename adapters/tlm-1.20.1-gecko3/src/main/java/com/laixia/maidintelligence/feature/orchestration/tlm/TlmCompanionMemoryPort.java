package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.CompanionPersistentMemory;
import com.laixia.maidintelligence.feature.orchestration.port.CompanionMemoryPort;

public final class TlmCompanionMemoryPort
        implements CompanionMemoryPort<EntityMaid> {
    @Override
    public CompanionPersistentMemory load(EntityMaid maid) {
        return maid.getOrCreateData(
                CompanionTaskData.memoryKey(),
                CompanionPersistentMemory.initial()
        );
    }

    @Override
    public void save(
            EntityMaid maid,
            CompanionPersistentMemory memory
    ) {
        maid.setAndSyncData(CompanionTaskData.memoryKey(), memory);
    }
}
