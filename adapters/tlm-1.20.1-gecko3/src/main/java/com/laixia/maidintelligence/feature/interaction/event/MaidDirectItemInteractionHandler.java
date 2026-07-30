package com.laixia.maidintelligence.feature.interaction.event;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.laixia.maidintelligence.feature.interaction.client.ClientMouthFeedController;
import com.laixia.maidintelligence.feature.interaction.service.MaidFeedingService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Objects;

public final class MaidDirectItemInteractionHandler {
    private final MaidFeedingService feeding;

    public MaidDirectItemInteractionHandler(MaidFeedingService feeding) {
        this.feeding = Objects.requireNonNull(feeding, "feeding");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDirectItemInteraction(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();
        if (!maid.isOwnedBy(player) || player.isShiftKeyDown()) {
            return;
        }

        ItemStack stack = event.getStack();
        if (feeding.isFeedableFood(maid, stack)) {
            if (event.getWorld().isClientSide) {
                DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT,
                        () -> () -> ClientMouthFeedController.tryFeed(maid)
                );
            }
            event.setCanceled(true);
            return;
        }
        if (stack.is(Items.POTION)) {
            applyPotion(event, player, maid, stack);
            return;
        }
        if (stack.is(Items.MILK_BUCKET)) {
            applyMilk(event, player, maid, stack);
        }
    }

    private void applyPotion(
            InteractMaidEvent event,
            Player player,
            EntityMaid maid,
            ItemStack stack
    ) {
        Level level = event.getWorld();
        stack.getItem().finishUsingItem(stack.copy(), level, maid);
        if (!player.isCreative()) {
            stack.shrink(1);
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(Items.GLASS_BOTTLE));
        }
        playDrinkSound(maid, level);
        event.setCanceled(true);
    }

    private void applyMilk(
            InteractMaidEvent event,
            Player player,
            EntityMaid maid,
            ItemStack stack
    ) {
        Level level = event.getWorld();
        maid.curePotionEffects(stack);
        if (!player.isCreative()) {
            stack.shrink(1);
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(Items.BUCKET));
        }
        playDrinkSound(maid, level);
        if (player instanceof ServerPlayer serverPlayer) {
            InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.CLEAR_MAID_EFFECTS);
        }
        event.setCanceled(true);
    }

    private void playDrinkSound(EntityMaid maid, Level level) {
        maid.playSound(
                SoundEvents.GENERIC_DRINK,
                0.6F,
                0.8F + level.random.nextFloat() * 0.4F
        );
    }
}
