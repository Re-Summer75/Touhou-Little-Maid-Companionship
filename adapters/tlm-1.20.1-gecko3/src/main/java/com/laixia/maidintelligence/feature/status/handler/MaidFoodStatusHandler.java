package com.laixia.maidintelligence.feature.status.handler;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAfterEatEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.status.tlm.TlmMaidStatusService;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MaidFoodStatusHandler {
    private final TlmMaidStatusService statusService;

    public MaidFoodStatusHandler(TlmMaidStatusService statusService) {
        this.statusService = statusService;
    }

    @SubscribeEvent
    public void onItemUseStarted(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            statusService.captureFoodNutrition(maid, event.getItem());
        }
    }

    @SubscribeEvent
    public void onItemUseStopped(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            statusService.onFoodUseStopped(maid);
        }
    }

    @SubscribeEvent
    public void onMaidAfterEat(MaidAfterEatEvent event) {
        statusService.onFoodEaten(event.getMaid(), event.getFoodAfterEat());
    }
}
