package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;
import com.laixia.maidintelligence.feature.behavior.api.MaidHungryOwnerRequestApi;
import com.laixia.maidintelligence.feature.behavior.api.MaidOwnerReturnApi;

import java.util.Objects;

public final class BehaviorTlmModule implements TlmFeatureModule {
    private final MaidHungryOwnerRequestApi<EntityMaid> hungryRequest;
    private final MaidOwnerReturnApi<EntityMaid> ownerReturn;

    public BehaviorTlmModule(
            MaidHungryOwnerRequestApi<EntityMaid> hungryRequest,
            MaidOwnerReturnApi<EntityMaid> ownerReturn
    ) {
        this.hungryRequest = Objects.requireNonNull(
                hungryRequest,
                "hungryRequest"
        );
        this.ownerReturn = Objects.requireNonNull(
                ownerReturn,
                "ownerReturn"
        );
    }

    @Override
    public void registerExtraBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new BehaviorExtraBrain(
                hungryRequest,
                ownerReturn
        ));
    }
}
