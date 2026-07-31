package com.laixia.maidintelligence.feature.status.handler;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.laixia.maidintelligence.feature.status.tlm.TlmMaidStatusService;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MaidHungerRegenerationHandler {
    private final TlmMaidStatusService statusService;

    public MaidHungerRegenerationHandler(TlmMaidStatusService statusService) {
        this.statusService = statusService;
    }

    @SubscribeEvent
    public void onMaidTick(MaidTickEvent event) {
        statusService.tickHungerRegeneration(event.getMaid());
    }
}
