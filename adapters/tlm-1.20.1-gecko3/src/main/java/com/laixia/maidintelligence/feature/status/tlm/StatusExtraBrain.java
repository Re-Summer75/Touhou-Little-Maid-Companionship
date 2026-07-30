package com.laixia.maidintelligence.feature.status.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.List;

public final class StatusExtraBrain implements IExtraMaidBrain {
    private static final int CORE_PRIORITY = 4;

    private final TlmMaidStatusService statusService;

    public StatusExtraBrain(TlmMaidStatusService statusService) {
        this.statusService = statusService;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getCoreBehaviors() {
        return List.of(Pair.of(CORE_PRIORITY, new StatusFeedbackBehavior(statusService)));
    }
}
