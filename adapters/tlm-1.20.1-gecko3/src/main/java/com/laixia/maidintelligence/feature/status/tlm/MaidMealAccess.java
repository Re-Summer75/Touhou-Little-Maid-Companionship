package com.laixia.maidintelligence.feature.status.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.MaidMealType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.DefaultMaidWorkMeal;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
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

    /**
     * 谁来处理这一口，没有则为 null。
     *
     * <p>先问 Forge 那个**按实体**的食物属性，再交给本体的处理器。两者不等价，
     * 而差别正是玩家报的"她不吃模组食物"：本体的 {@code isWorkMeal} 用的是
     * {@code ItemStack.isEdible()}，读的是物品的**静态**食物属性；而 Forge 允许
     * 模组只实现 {@code getFoodProperties(stack, entity)}——按吃的人给属性。
     * 那类食物 {@code isEdible()} 恒为假，于是本体的处理器一个都不接，她眼里
     * 那就不是食物。
     *
     * <p>这里不改本体的判据，也不注册自己的 {@code IMaidMeal}（那是全局注册表，
     * 会连带改变其它工作模式）。做法是：本体处理器都不接、而 Forge 说它确实是
     * 食物时，用本体的默认工作餐处理器把它吃掉——处理流程仍是本体的那一套，
     * 只有"这算不算食物"这一问改由更全的那个 API 回答。
     */
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
        // 没有一个处理器认它，但 Forge 说它是食物——模组给的按实体食物属性。
        // 黑名单仍然要问：玩家把某样东西列进去，是不希望她碰它，而不是不希望
        // 本体认出它。
        if (!IMaidMeal.isBlockList(
                stack, MaidConfig.MAID_WORK_MEALS_BLOCK_LIST.get())) {
            return FORGE_AWARE_MEAL;
        }
        return null;
    }

    /**
     * 兜底处理器：吃法完全是本体的，只是判据换成 Forge 那一问。
     *
     * <p>不注册进 {@code MaidMealManager}——那是全局的，会改变其它工作模式下
     * 本体自己的判断。这一个只在本模组的进食路径上被使用。
     */
    private static final IMaidMeal FORGE_AWARE_MEAL = new DefaultMaidWorkMeal() {
        @Override
        public boolean canMaidEat(
                EntityMaid maid, ItemStack stack, InteractionHand hand
        ) {
            return stack.getFoodProperties(maid) != null;
        }
    };

    private InteractionHand findEatingHand(EntityMaid maid) {
        for (InteractionHand hand : HandUtils.NATIVE_HANDS) {
            if (maid.getItemInHand(hand).isEmpty()) {
                return hand;
            }
        }
        return InteractionHand.OFF_HAND;
    }
}
