package com.laixia.maidintelligence.feature.status.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.status.domain.DefaultToolDurabilityPolicy;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

public final class ToolReplacementService {
    private static final InteractionHand[] HANDS = {
            InteractionHand.MAIN_HAND,
            InteractionHand.OFF_HAND
    };

    private final DefaultToolDurabilityPolicy policy;

    public ToolReplacementService(DefaultToolDurabilityPolicy policy) {
        this.policy = policy;
    }

    public ToolReplacementResult inspectAndReplace(EntityMaid maid) {
        for (InteractionHand hand : HANDS) {
            ItemStack wornTool = maid.getItemInHand(hand);
            if (!isLowDurability(wornTool)) {
                continue;
            }

            int remaining = remainingDurability(wornTool);
            int maximum = wornTool.getMaxDamage();
            int replacementSlot = findBestReplacementSlot(maid.getAvailableBackpackInv(), wornTool);
            if (replacementSlot < 0) {
                return new ToolReplacementResult(true, false, hand, remaining, maximum);
            }

            if (swapWithBackpack(maid, hand, replacementSlot)) {
                return new ToolReplacementResult(true, true, hand, remaining, maximum);
            }
            return new ToolReplacementResult(true, false, hand, remaining, maximum);
        }
        return ToolReplacementResult.none();
    }

    private boolean isLowDurability(ItemStack stack) {
        return !stack.isEmpty()
                && stack.isDamageableItem()
                && policy.isLowDurability(stack.getMaxDamage(), stack.getDamageValue());
    }

    private int remainingDurability(ItemStack stack) {
        return policy.remainingDurability(stack.getMaxDamage(), stack.getDamageValue());
    }

    private int findBestReplacementSlot(IItemHandlerModifiable backpack, ItemStack wornTool) {
        int bestSlot = -1;
        int bestRemaining = -1;
        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            ItemStack candidate = backpack.getStackInSlot(slot);
            if (candidate.isEmpty()
                    || !candidate.is(wornTool.getItem())
                    || !candidate.isDamageableItem()
                    || policy.isLowDurability(candidate.getMaxDamage(), candidate.getDamageValue())) {
                continue;
            }
            int candidateRemaining = remainingDurability(candidate);
            if (candidateRemaining > bestRemaining) {
                bestRemaining = candidateRemaining;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private boolean swapWithBackpack(EntityMaid maid, InteractionHand hand, int replacementSlot) {
        IItemHandlerModifiable backpack = maid.getAvailableBackpackInv();
        ItemStack replacement = backpack.getStackInSlot(replacementSlot);
        ItemStack wornTool = maid.getItemInHand(hand);
        if (replacement.isEmpty() || wornTool.isEmpty()) {
            return false;
        }

        ItemStack extracted = backpack.extractItem(replacementSlot, replacement.getCount(), false);
        if (extracted.isEmpty()) {
            return false;
        }

        backpack.setStackInSlot(replacementSlot, wornTool);
        maid.setItemInHand(hand, extracted);
        return true;
    }
}
