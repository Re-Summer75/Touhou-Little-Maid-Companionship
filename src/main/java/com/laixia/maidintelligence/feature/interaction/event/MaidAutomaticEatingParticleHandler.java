package com.laixia.maidintelligence.feature.interaction.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.platform.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MaidAutomaticEatingParticleHandler {
    private static final int NORMAL_FOOD_WARMUP_TICKS = 7;
    private static final int PARTICLE_INTERVAL_TICKS = 4;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof EntityMaid maid)
                || !(maid.level() instanceof ServerLevel)) {
            return;
        }

        ItemStack food = event.getItem();
        if (food.isEmpty()) {
            return;
        }
        var foodProperties = food.getFoodProperties(maid);
        if (foodProperties == null) {
            return;
        }

        int remainingTicks = event.getDuration();
        boolean shouldShowEffects = foodProperties.isFastFood()
                || remainingTicks <= food.getUseDuration() - NORMAL_FOOD_WARMUP_TICKS;
        if (!shouldShowEffects || remainingTicks % PARTICLE_INTERVAL_TICKS != 0) {
            return;
        }

        ItemStack particleFood = food.copy();
        particleFood.setCount(1);
        ModNetwork.sendMaidEatingParticlesFromTrackedFace(maid, particleFood);
    }
}
