package com.laixia.maidintelligence.feature.status.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.MaidMealType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.DefaultMaidWorkMeal;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.MaidMealManager;
import com.github.tartaricacid.touhoulittlemaid.util.HandUtils;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

@SuppressWarnings("null")
public final class MaidMealAccess {
    public boolean tryStartHungerMeal(EntityMaid maid) {
        List<IMaidMeal> mealHandlers = MaidMealManager.getMaidMeals(MaidMealType.WORK_MEAL);

        for (InteractionHand hand : HandUtils.NATIVE_HANDS) {
            ItemStack held = maid.getItemInHand(hand);
            IMaidMeal handler = findHandler(maid, held, hand, mealHandlers);
            if (handler != null) {
                handler.onMaidEat(maid, held, hand);
                return true;
            }
        }

        InteractionHand eatingHand = findEatingHand(maid);
        ItemStack previousHandStack = maid.getItemInHand(eatingHand).copy();
        var backpack = maid.getAvailableBackpackInv();

        // Keep TLM's external-inventory request hook available before the local scan.
        ItemsUtil.findStackSlot(backpack, DefaultMaidWorkMeal::isWorkMeal);

        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            ItemStack candidate = backpack.getStackInSlot(slot);
            IMaidMeal handler = findHandler(maid, candidate, eatingHand, mealHandlers);
            if (handler == null) {
                continue;
            }

            ItemStack food = backpack.extractItem(slot, candidate.getCount(), false);
            if (food.isEmpty()) {
                continue;
            }
            maid.setItemInHand(eatingHand, food);
            maid.memoryHandItemStack(previousHandStack);
            handler.onMaidEat(maid, maid.getItemInHand(eatingHand), eatingHand);
            return true;
        }
        return false;
    }

    public boolean hasLocalHungerMeal(EntityMaid maid) {
        List<IMaidMeal> mealHandlers = MaidMealManager.getMaidMeals(
                MaidMealType.WORK_MEAL
        );
        for (InteractionHand hand : HandUtils.NATIVE_HANDS) {
            if (findHandler(
                    maid,
                    maid.getItemInHand(hand),
                    hand,
                    mealHandlers
            ) != null) {
                return true;
            }
        }

        InteractionHand eatingHand = findEatingHand(maid);
        var backpack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            if (findHandler(
                    maid,
                    backpack.getStackInSlot(slot),
                    eatingHand,
                    mealHandlers
            ) != null) {
                return true;
            }
        }
        return false;
    }

    public boolean canStartExternalHungerMeal(
            EntityMaid maid,
            ItemStack food
    ) {
        return findExternalHandler(maid, food) != null;
    }

    public boolean tryStartExternalHungerMeal(
            EntityMaid maid,
            ItemStack food
    ) {
        if (food.isEmpty()) {
            return false;
        }
        InteractionHand eatingHand = findEatingHand(maid);
        IMaidMeal handler = findHandler(
                maid,
                food,
                eatingHand,
                MaidMealManager.getMaidMeals(MaidMealType.WORK_MEAL)
        );
        if (handler == null) {
            return false;
        }

        ItemStack previousHandStack = maid.getItemInHand(eatingHand).copy();
        maid.setItemInHand(eatingHand, food);
        maid.memoryHandItemStack(previousHandStack);
        handler.onMaidEat(maid, maid.getItemInHand(eatingHand), eatingHand);
        return true;
    }

    private IMaidMeal findExternalHandler(
            EntityMaid maid,
            ItemStack food
    ) {
        return findHandler(
                maid,
                food,
                findEatingHand(maid),
                MaidMealManager.getMaidMeals(MaidMealType.WORK_MEAL)
        );
    }

    private IMaidMeal findHandler(
            EntityMaid maid,
            ItemStack stack,
            InteractionHand hand,
            List<IMaidMeal> mealHandlers
    ) {
        if (stack.isEmpty() || stack.getFoodProperties(maid) == null) {
            return null;
        }
        for (IMaidMeal handler : mealHandlers) {
            if (handler.canMaidEat(maid, stack, hand)) {
                return handler;
            }
        }
        return null;
    }

    private InteractionHand findEatingHand(EntityMaid maid) {
        for (InteractionHand hand : HandUtils.NATIVE_HANDS) {
            if (maid.getItemInHand(hand).isEmpty()) {
                return hand;
            }
        }
        return InteractionHand.OFF_HAND;
    }
}
