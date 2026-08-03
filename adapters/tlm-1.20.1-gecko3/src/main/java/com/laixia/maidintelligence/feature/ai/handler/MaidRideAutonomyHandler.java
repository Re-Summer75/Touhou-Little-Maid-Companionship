package com.laixia.maidintelligence.feature.ai.handler;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.tlm.MaidSeatAutonomyBridge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MaidRideAutonomyHandler {
    @SubscribeEvent
    public void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        MaidSeatAutonomyBridge.leaveSeatForCombat(maid);
        MaidSeatAutonomyBridge.leaveOrdinaryBoatWhenOwnerLeaves(maid);
    }
}
