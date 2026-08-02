package com.laixia.maidintelligence.feature.ai.handler;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.laixia.maidintelligence.feature.ai.tlm.MaidSeatAutonomyBridge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MaidSeatCombatHandler {
    @SubscribeEvent
    public void onMaidTick(MaidTickEvent event) {
        MaidSeatAutonomyBridge.leaveSeatForCombat(event.getMaid());
    }
}
