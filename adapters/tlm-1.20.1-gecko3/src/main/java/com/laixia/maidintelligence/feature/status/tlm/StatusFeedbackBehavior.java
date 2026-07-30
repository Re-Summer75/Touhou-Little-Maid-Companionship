package com.laixia.maidintelligence.feature.status.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;

public final class StatusFeedbackBehavior extends MaidCheckRateTask {
    private static final int CHECK_RATE_TICKS = 20;

    private final TlmMaidStatusService statusService;

    public StatusFeedbackBehavior(TlmMaidStatusService statusService) {
        super(ImmutableMap.of());
        this.statusService = statusService;
        this.setMaxCheckRate(CHECK_RATE_TICKS);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return super.checkExtraStartConditions(level, maid) && !maid.isDeadOrDying();
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        statusService.tick(maid);
    }
}
