package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import com.laixia.maidintelligence.feature.behavior.api.MaidHungryOwnerRequestApi;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

public final class HungryOwnerRequestBehavior extends MaidCheckRateTask {
    private final MaidHungryOwnerRequestApi<EntityMaid> request;

    public HungryOwnerRequestBehavior(
            MaidHungryOwnerRequestApi<EntityMaid> request
    ) {
        super(ImmutableMap.of());
        this.request = Objects.requireNonNull(request, "request");
        this.setMaxCheckRate(1);
    }

    @Override
    protected boolean checkExtraStartConditions(
            ServerLevel level,
            EntityMaid maid
    ) {
        return super.checkExtraStartConditions(level, maid)
                && request.shouldEvaluate(maid, level.getGameTime());
    }

    @Override
    protected void start(
            ServerLevel level,
            EntityMaid maid,
            long gameTime
    ) {
        request.tryRequest(maid);
    }
}
