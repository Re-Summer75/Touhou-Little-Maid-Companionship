package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityGrantSet;
import com.laixia.maidintelligence.feature.behavior.port.AbilityGrantPort;
import com.laixia.maidintelligence.feature.orchestration.tlm.CompanionTaskData;

public final class TlmAbilityGrantPort
        implements AbilityGrantPort<EntityMaid> {
    @Override
    public AbilityGrantSet load(EntityMaid maid) {
        return maid.getOrCreateData(
                CompanionTaskData.abilityGrantsKey(),
                AbilityGrantSet.empty()
        );
    }

    @Override
    public void save(EntityMaid maid, AbilityGrantSet grants) {
        maid.setAndSyncData(
                CompanionTaskData.abilityGrantsKey(),
                grants
        );
    }
}
