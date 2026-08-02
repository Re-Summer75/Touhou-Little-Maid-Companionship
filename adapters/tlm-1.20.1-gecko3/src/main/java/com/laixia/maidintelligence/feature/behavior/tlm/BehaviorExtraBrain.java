package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.MaidHungryOwnerRequestApi;
import com.laixia.maidintelligence.feature.behavior.api.MaidOwnerReturnApi;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.List;
import java.util.Objects;

public final class BehaviorExtraBrain implements IExtraMaidBrain {
    private static final int HUNGRY_REQUEST_PRIORITY = 4;
    private static final int OWNER_RETURN_PRIORITY = 5;

    private final MaidHungryOwnerRequestApi<EntityMaid> hungryRequest;
    private final MaidOwnerReturnApi<EntityMaid> ownerReturn;

    public BehaviorExtraBrain(
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
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>>
    getCoreBehaviors() {
        return List.of(
                Pair.of(
                        HUNGRY_REQUEST_PRIORITY,
                        new HungryOwnerRequestBehavior(hungryRequest)
                ),
                Pair.of(
                        OWNER_RETURN_PRIORITY,
                        new OwnerReturnBehavior(ownerReturn)
                )
        );
    }
}
