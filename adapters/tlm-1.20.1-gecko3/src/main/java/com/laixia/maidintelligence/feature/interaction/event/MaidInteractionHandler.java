package com.laixia.maidintelligence.feature.interaction.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 调整主人与女仆的默认交互：
 * <ul>
 *     <li>Shift + 右键：打开女仆界面</li>
 *     <li>空手右键：切换坐下/起立</li>
 *     <li>手持物品右键：只执行物品交互，不再回退打开界面</li>
 * </ul>
 */
public final class MaidInteractionHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onShiftInteract(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();
        if (!maid.isOwnedBy(player)
                || !player.isShiftKeyDown()
                || player.getMainHandItem().is(InitItems.KAPPA_COMPASS.get())) {
            return;
        }

        maid.openMaidGui(player);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onNormalInteract(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();
        if (!maid.isOwnedBy(player) || player.isShiftKeyDown()) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!stack.isEmpty()) {
            InteractionResult entityInteraction = stack.interactLivingEntity(
                    player,
                    maid,
                    InteractionHand.MAIN_HAND
            );
            if (!entityInteraction.consumesAction()) {
                var useResult = stack.use(
                        event.getWorld(),
                        player,
                        InteractionHand.MAIN_HAND
                );
                if (useResult.getResult().consumesAction()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, useResult.getObject());
                }
            }
            event.setCanceled(true);
            return;
        }

        boolean hadMountOrPassengers = maid.getVehicle() != null || !maid.getPassengers().isEmpty();
        if (maid.getVehicle() != null) {
            maid.stopRiding();
        }
        if (!maid.getPassengers().isEmpty()) {
            maid.ejectPassengers();
        }
        if (!hadMountOrPassengers) {
            toggleSitting(event, maid);
        }
        event.setCanceled(true);
    }

    private void toggleSitting(InteractMaidEvent event, EntityMaid maid) {
        maid.setInSittingPose(!maid.isMaidInSittingPose());
        if (maid.isMaidInSittingPose()) {
            maid.getNavigation().stop();
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            maid.setTarget(null);
        }
        if (maid.hasRestriction() && maid.canBrainMoving()) {
            maid.getSchedulePos().restrictTo(maid);
            BehaviorUtils.setWalkAndLookTargetMemories(maid, maid.getRestrictCenter(), 0.7F, 3);
        }
        maid.playSound(
                SoundEvents.ITEM_PICKUP,
                0.2F,
                ((event.getWorld().random.nextFloat() - event.getWorld().random.nextFloat()) * 0.7F + 1.0F) * 2.0F
        );
    }
}
