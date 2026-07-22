package com.laixia.maidintelligence.feature.interaction.service;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.DefaultMaidWorkMeal;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.laixia.maidintelligence.feature.status.StatusFeedbackFeature;
import com.laixia.maidintelligence.platform.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class MaidFeedingService {
    private MaidFeedingService() {
    }

    public static boolean isFeedableFood(EntityMaid maid, ItemStack stack) {
        FoodProperties food = stack.getFoodProperties(maid);
        return food == Foods.GOLDEN_APPLE
                || food == Foods.ENCHANTED_GOLDEN_APPLE
                || DefaultMaidWorkMeal.isWorkMeal(stack);
    }

    public static boolean feed(
            ServerPlayer player,
            EntityMaid maid,
            Vec3 worldCenter,
            Vec3 worldNormal
    ) {
        ItemStack stack = player.getMainHandItem();
        if (!maid.isAlive() || !maid.isOwnedBy(player) || !isFeedableFood(maid, stack)) {
            return false;
        }

        FoodProperties food = stack.getFoodProperties(maid);
        ItemStack particleFood = stack.copy();
        particleFood.setCount(1);

        StatusFeedbackFeature.INSTANCE.api().captureFoodNutrition(maid, stack);
        ItemStack foodToEat = player.isCreative() ? particleFood.copy() : stack;
        maid.eat(player.serverLevel(), foodToEat);
        ModNetwork.sendMaidEatingParticles(
                maid,
                particleFood,
                worldCenter,
                worldNormal
        );

        if (food == Foods.ENCHANTED_GOLDEN_APPLE) {
            InitTrigger.MAID_EVENT.trigger(player, TriggerType.EAT_ENCHANTED_GOLDEN_APPLE);
        }
        return true;
    }
}
