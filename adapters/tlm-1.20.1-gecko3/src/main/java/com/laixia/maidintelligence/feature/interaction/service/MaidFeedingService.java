package com.laixia.maidintelligence.feature.interaction.service;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.DefaultMaidWorkMeal;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.laixia.maidintelligence.feature.advancement.server.MaidCriteria;
import com.laixia.maidintelligence.feature.status.StatusFeedbackFeature;
import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;
import com.laixia.maidintelligence.platform.network.ModNetwork;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

public final class MaidFeedingService {
    private static final int CAKE_SLICE_NUTRITION = 2;
    private static final int CAKE_SERVINGS = CakeBlock.MAX_BITES + 1;
    private static final float CAKE_SATURATION_MODIFIER = 0.1F;
    private static final long REFUSAL_COOLDOWN_TICKS = 20L;
    private static final String SATURATION_FULL_BUBBLE =
            ModResources.translationKey("chat_bubble", "status.saturation_full");
    private static final Map<EntityMaid, RefusalFeedback> REFUSAL_FEEDBACK =
            new WeakHashMap<>();

    private MaidFeedingService() {
    }

    public static boolean isFeedableFood(EntityMaid maid, ItemStack stack) {
        FoodProperties food = stack.getFoodProperties(maid);
        return stack.is(Items.CAKE)
                || food == Foods.GOLDEN_APPLE
                || food == Foods.ENCHANTED_GOLDEN_APPLE
                || DefaultMaidWorkMeal.isWorkMeal(stack);
    }

    public static boolean isSaturationFull(EntityMaid maid) {
        return DefaultHungerPolicy.INSTANCE.isSaturationFull(
                StatusFeedbackFeature.INSTANCE.api().getState(maid)
        );
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
        if (isSaturationFull(maid)) {
            rejectFood(player, maid);
            return false;
        }

        ItemStack particleFood = stack.copy();
        particleFood.setCount(1);

        FoodProperties food = stack.getFoodProperties(maid);
        if (stack.is(Items.CAKE)) {
            feedCake(player, maid, stack);
        } else {
            StatusFeedbackFeature.INSTANCE.api().captureFoodNutrition(maid, stack);
            ItemStack foodToEat = player.isCreative() ? particleFood.copy() : stack;
            maid.eat(player.serverLevel(), foodToEat);
        }
        ModNetwork.sendMaidEatingParticles(
                maid,
                particleFood,
                worldCenter,
                worldNormal
        );

        if (food == Foods.ENCHANTED_GOLDEN_APPLE) {
            InitTrigger.MAID_EVENT.trigger(player, TriggerType.EAT_ENCHANTED_GOLDEN_APPLE);
        }
        MaidCriteria.fed(maid, particleFood);
        return true;
    }

    private static void rejectFood(ServerPlayer player, EntityMaid maid) {
        long gameTime = maid.level().getGameTime();
        RefusalFeedback feedback = REFUSAL_FEEDBACK.computeIfAbsent(
                maid,
                ignored -> new RefusalFeedback()
        );
        if (gameTime < feedback.nextAllowedGameTime) {
            return;
        }

        feedback.nextAllowedGameTime = gameTime + REFUSAL_COOLDOWN_TICKS;
        maid.getLookControl().setLookAt(player, 30.0F, 30.0F);
        maid.swing(InteractionHand.OFF_HAND);
        maid.playSound(
                InitSounds.MAID_HURT.get(),
                1.0F,
                1.0F
        );
        feedback.bubbleKey = maid.getChatBubbleManager().addTextChatBubbleIfTimeout(
                SATURATION_FULL_BUBBLE,
                feedback.bubbleKey
        );
    }

    private static void feedCake(
            ServerPlayer player,
            EntityMaid maid,
            ItemStack cake
    ) {
        StatusFeedbackFeature.INSTANCE.api().restoreFromFood(
                maid,
                CAKE_SLICE_NUTRITION * CAKE_SERVINGS,
                CAKE_SATURATION_MODIFIER
        );
        if (!player.isCreative()) {
            cake.shrink(1);
        }
        maid.playSound(
                SoundEvents.GENERIC_EAT,
                1.0F,
                0.9F + maid.getRandom().nextFloat() * 0.2F
        );
        maid.gameEvent(GameEvent.EAT);
    }

    private static final class RefusalFeedback {
        private long nextAllowedGameTime;
        private long bubbleKey = -1L;
    }
}
