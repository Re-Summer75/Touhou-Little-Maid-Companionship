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

        InteractionHand intended = findEatingHand(maid);
        var backpack = maid.getAvailableBackpackInv();

        // Keep TLM's external-inventory request hook available before the local scan.
        ItemsUtil.findStackSlot(backpack, DefaultMaidWorkMeal::isWorkMeal);

        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            ItemStack candidate = backpack.getStackInSlot(slot);
            IMaidMeal handler = findHandler(maid, candidate, intended, mealHandlers);
            if (handler == null) {
                continue;
            }

            // 先把饭取出来，再腾手。反过来写会让**背包塞满的工作女仆饿死**：腾
            // 手要往背包里放东西，而这一口饭占着的那一格正是唯一的空位——取出来
            // 就有了，没取出来就永远没有。格子越多的背包越会长期处在这个状态。
            ItemStack food = backpack.extractItem(slot, candidate.getCount(), false);
            if (food.isEmpty()) {
                continue;
            }
            // 腾手也放在**找到食物之后**：先腾再找，会为了一顿不存在的饭把她副手
            // 那面盾收进背包。
            InteractionHand eatingHand = prepareEatingHand(maid);
            if (eatingHand == null) {
                // 原样放回。刚从这里拿走的，一定放得回去。
                backpack.insertItem(slot, food, false);
                return false;
            }
            maid.setItemInHand(eatingHand, food);
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

        InteractionHand freed = prepareEatingHand(maid);
        if (freed == null) {
            return false;
        }
        maid.setItemInHand(freed, food);
        handler.onMaidEat(maid, maid.getItemInHand(freed), freed);
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

    /**
     * 她准备用哪只手吃，这一问只用来预判、不动任何东西。
     *
     * <p>两只手都占着时答"副手"，因为那是真正吃饭的时候会腾的那一只——
     * {@link #prepareEatingHand} 腾的也是它，两处必须给出同一个答案，否则
     * {@code canMaidEat} 是按一只手问的、饭却吃在另一只手上。
     */
    private InteractionHand findEatingHand(EntityMaid maid) {
        for (InteractionHand hand : HandUtils.NATIVE_HANDS) {
            if (maid.getItemInHand(hand).isEmpty()) {
                return hand;
            }
        }
        return InteractionHand.OFF_HAND;
    }

    /**
     * 真的把一只手腾出来，腾不出来就返回 null。
     *
     * <p>原先这里不腾手：直接把食物写进副手，把原本那件东西交给本体的
     * {@code memoryHandItemStack} 代管。那个方法**在隐藏槽已经有东西时会把它扔
     * 在地上**，而隐藏槽有东西恰恰是常态——它只在 {@code completeUsingItem} 里
     * 归还，战斗打断进食就永远走不到那一步。于是上一顿饭扣下的盾，会在下一顿饭
     * 开始的瞬间掉在地上。
     *
     * <p>现在东西进背包（{@link MaidOffhand} 保证整份放得下才放），进食路径不再
     * 碰那个隐藏槽。装不下就这一 tick 不吃——饿一会儿是可以恢复的，武器掉在
     * 尸潮里不是。
     */
    private InteractionHand prepareEatingHand(EntityMaid maid) {
        MaidOffhand.recoverStranded(maid);
        for (InteractionHand hand : HandUtils.NATIVE_HANDS) {
            if (maid.getItemInHand(hand).isEmpty()) {
                return hand;
            }
        }
        return MaidOffhand.vacate(maid) ? InteractionHand.OFF_HAND : null;
    }
}
