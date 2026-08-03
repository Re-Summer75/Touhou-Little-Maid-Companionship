package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.learning.CompanionLearningProfile;
import com.laixia.maidintelligence.feature.behavior.port.LearningProfilePort;
import com.laixia.maidintelligence.feature.orchestration.tlm.CompanionTaskData;

public final class TlmLearningProfilePort
        implements LearningProfilePort<EntityMaid> {
    @Override
    public CompanionLearningProfile load(EntityMaid maid) {
        return maid.getOrCreateData(
                CompanionTaskData.learningProfileKey(),
                CompanionLearningProfile.empty()
        );
    }

    @Override
    public void save(
            EntityMaid maid,
            CompanionLearningProfile profile
    ) {
        maid.setAndSyncData(
                CompanionTaskData.learningProfileKey(),
                profile
        );
    }
}
