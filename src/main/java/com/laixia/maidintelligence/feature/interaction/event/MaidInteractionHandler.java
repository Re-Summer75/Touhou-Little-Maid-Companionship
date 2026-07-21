package com.laixia.maidintelligence.feature.interaction.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 调整主人与女仆的默认交互：
 * <ul>
 *     <li>Shift + 右键：打开女仆界面</li>
 *     <li>空手右键：切换坐下/起立</li>
 * </ul>
 *
 * <p>手持物品的普通右键仍交给车万女仆和物品自身处理，避免破坏喂食、
 * 穿戴背包、好感道具等原有交互。</p>
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
    public void onEmptyHandInteract(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();
        if (!maid.isOwnedBy(player)
                || player.isShiftKeyDown()
                || !player.getMainHandItem().isEmpty()) {
            return;
        }

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
        event.setCanceled(true);
    }
}
