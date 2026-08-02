package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import com.laixia.maidintelligence.feature.behavior.api.MaidOwnerReturnApi;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

public final class OwnerReturnBehavior extends MaidCheckRateTask {
    private final MaidOwnerReturnApi<EntityMaid> ownerReturn;

    public OwnerReturnBehavior(
            MaidOwnerReturnApi<EntityMaid> ownerReturn
    ) {
        super(ImmutableMap.of());
        this.ownerReturn = Objects.requireNonNull(
                ownerReturn,
                "ownerReturn"
        );
        this.setMaxCheckRate(1);
    }

    @Override
    protected boolean checkExtraStartConditions(
            ServerLevel level,
            EntityMaid maid
    ) {
        return super.checkExtraStartConditions(level, maid);
    }

    @Override
    protected void start(
            ServerLevel level,
            EntityMaid maid,
            long gameTime
    ) {
        ownerReturn.tick(maid, gameTime);
    }
}
