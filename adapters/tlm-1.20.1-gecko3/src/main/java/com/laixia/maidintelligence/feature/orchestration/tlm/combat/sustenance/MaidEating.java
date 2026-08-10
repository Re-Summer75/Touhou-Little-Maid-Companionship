package com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.sustenance.FoodValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * Getting the mouthful from the pack into her.
 *
 * <p>Split from the decision for the same reason the weapon swap is: choosing
 * and doing are different code, and a choice that is right while the hand is
 * wrong looks exactly like a choice that is wrong. This half owns the item
 * shuffling and the use state, and nothing else.
 *
 * <p>Eating happens through the vanilla use cycle rather than by applying the
 * effects directly. That is what makes her visibly raise it to her mouth, what
 * makes the particles and the sound happen, and what makes a modded food's own
 * {@code finishUsingItem} run — a mod that gives its fruit a side effect gets
 * that side effect, and none of it is written down here.
 */
public final class MaidEating {
    private MaidEating() {
    }

    /**
     * Whether she is currently in the middle of a mouthful.
     *
     * <p>Asked of the item in her hand rather than of a flag, because the flag
     * would be a second copy of the truth. She is eating exactly when the thing
     * she is using is edible.
     */
    public static boolean chewing(EntityMaid maid) {
        return maid.isUsingItem()
                && maid.getUseItem().getFoodProperties(maid) != null;
    }

    /**
     * Begin the mouthful, moving it into her hand first.
     *
     * <p>Whatever she was holding goes into the slot the food came out of, so a
     * sword is not dropped on the floor to eat an apple and is still there
     * afterwards. The pack is the only place either of them can be.
     *
     * @return whether she actually started eating
     */
    public static boolean begin(EntityMaid maid, FoodValue food) {
        if (food == null || chewing(maid)) {
            return false;
        }
        IItemHandler backpack = maid.getAvailableBackpackInv();
        if (food.slot() >= backpack.getSlots()) {
            return false;
        }
        ItemStack meal = backpack.getStackInSlot(food.slot());
        if (meal.isEmpty() || meal.getFoodProperties(maid) == null) {
            // The scan is a tick old and the pack can be rearranged under it.
            // Refusing here is the honest answer; the next tick rescans.
            return false;
        }
        if (maid.isUsingItem()) {
            // A drawn bow is not something to swallow around. Letting go here
            // rather than deferring is what makes the swap land on this tick;
            // deferring produces a maid who begins to eat once a tick forever.
            maid.stopUsingItem();
        }
        ItemStack held = maid.getMainHandItem();
        maid.setItemInHand(InteractionHand.MAIN_HAND, meal.copy());
        backpack.extractItem(food.slot(), meal.getCount(), false);
        backpack.insertItem(food.slot(), held, false);
        maid.startUsingItem(InteractionHand.MAIN_HAND);
        return true;
    }
}
