package com.laixia.maidintelligence.mixin.tlm.status;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.status.api.MaidStatusFeedbackApi;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityMaid.class, remap = false)
@SuppressWarnings("null")
public abstract class MaidPickupInventoryFeedbackMixin {
    @Inject(method = "pickupItem", at = @At("RETURN"), remap = false)
    private void tlmCompanionship$reportFullInventory(
            ItemEntity item,
            boolean simulate,
            CallbackInfoReturnable<Boolean> callback
    ) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (!simulate
                || callback.getReturnValueZ()
                || !(maid.level() instanceof ServerLevel)
                || !maid.isTame()
                || !maid.isPickup()
                || !maid.getConfigManager().getPickupType().canPickItem()
                || !isVisiblePickupCandidate(maid, item)) {
            return;
        }

        ItemStack stack = item.getItem();
        if (stack.isEmpty() || !EntityMaid.canInsertItem(stack)) {
            return;
        }

        // Repeat only the simulated insertion so blacklist, delay and event
        // vetoes are not misreported as a full inventory.
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(
                maid.getAvailableInv(false),
                stack.copy(),
                true
        );
        if (remainder.getCount() == stack.getCount()) {
            feedbackApi().reportInventoryFull(maid);
        }
    }

    @Unique
    private static boolean isVisiblePickupCandidate(
            EntityMaid maid,
            ItemEntity item
    ) {
        float radius = maid.getRestrictRadius();
        return item.isAlive()
                && !item.hasPickUpDelay()
                && !item.isInWater()
                && item.closerThan(maid, radius + 1.0F)
                && maid.isWithinRestriction(item.blockPosition())
                && maid.hasLineOfSight(item);
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static MaidStatusFeedbackApi<EntityMaid> feedbackApi() {
        return (MaidStatusFeedbackApi<EntityMaid>) AdapterRuntime.require(
                MaidStatusFeedbackApi.class
        );
    }
}
