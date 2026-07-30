package com.laixia.maidintelligence.feature.status.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.laixia.maidintelligence.feature.status.service.DefaultMaidStatusService;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MaidHungerRegenerationHandler {
    private final DefaultMaidStatusService statusService;

    public MaidHungerRegenerationHandler(DefaultMaidStatusService statusService) {
        this.statusService = statusService;
    }

    @SubscribeEvent
    public void onMaidTick(MaidTickEvent event) {
        statusService.tickHungerRegeneration(event.getMaid());
    }
}
