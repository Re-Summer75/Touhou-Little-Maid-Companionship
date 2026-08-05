package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Eat something off the floor, including whatever was just thrown at her.
 *
 * <p>Nothing here knows about being thrown. The item is simply the nearest good
 * thing to eat, and it competes with a cabinet on the same ranking — which is
 * why "she stops going to the cupboard when there is food on the ground" is
 * behaviour nobody wrote.
 */
public final class LooseFoodErrand implements Errand {
    private static final int CANDIDATES = 4;

    private final TlmAffordancePerceptionService perception;
    private final MaidMealAccess mealAccess;

    public LooseFoodErrand(
            TlmAffordancePerceptionService perception,
            MaidMealAccess mealAccess
    ) {
        this.perception = Objects.requireNonNull(perception, "perception");
        this.mealAccess = Objects.requireNonNull(mealAccess, "mealAccess");
    }

    @Override
    public String name() {
        return "loose_food";
    }

    @Override
    public ApproachTarget find(EntityMaid maid, long gameTime) {
        List<ItemEntity> candidates = perception.queryLooseFood(
                maid,
                CANDIDATES,
                gameTime
        );
        for (ItemEntity candidate : candidates) {
            if (edible(maid, candidate)) {
                return new EntityApproachTarget(candidate);
            }
        }
        return null;
    }

    /**
     * An item can be picked up by anyone at any moment, so it going missing
     * part-way is the ordinary case rather than a fault.
     */
    @Override
    public boolean stillWorthwhile(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        return target instanceof EntityApproachTarget entityTarget
                && entityTarget.entity() instanceof ItemEntity item
                && edible(maid, item);
    }

    /**
     * Takes one and starts the meal, putting it back if the meal will not
     * start. The check runs before the item leaves the world, so the ordinary
     * outcome needs no restoring at all.
     */
    @Override
    public boolean commit(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        if (!(target instanceof EntityApproachTarget entityTarget)
                || !(entityTarget.entity() instanceof ItemEntity item)) {
            return false;
        }
        ItemStack stack = item.getItem();
        if (!edible(maid, item)
                || !maid.getHideInv().getStackInSlot(0).isEmpty()
                || mealAccess.hasLocalHungerMeal(maid)) {
            return false;
        }
        ItemStack meal = stack.copy();
        meal.setCount(1);
        ItemStack remainder = stack.copy();
        remainder.shrink(1);
        if (remainder.isEmpty()) {
            item.discard();
        } else {
            item.setItem(remainder);
        }
        if (mealAccess.tryStartExternalHungerMeal(maid, meal)) {
            return true;
        }
        restore(maid, item, meal, remainder);
        return false;
    }

    private boolean edible(EntityMaid maid, ItemEntity item) {
        return item.isAlive()
                && !item.hasPickUpDelay()
                && !maid.isUsingItem()
                && maid.getTask().enableEating(maid)
                && mealAccess.canStartExternalHungerMeal(maid, item.getItem());
    }

    private static void restore(
            EntityMaid maid,
            ItemEntity item,
            ItemStack meal,
            ItemStack remainder
    ) {
        if (!remainder.isEmpty() && item.isAlive()) {
            ItemStack merged = remainder.copy();
            merged.grow(1);
            item.setItem(merged);
            return;
        }
        // The entity is gone, so it comes back as a fresh drop rather than
        // vanishing because a meal declined to start.
        ItemEntity replacement = new ItemEntity(
                maid.level(),
                item.getX(),
                item.getY(),
                item.getZ(),
                meal
        );
        replacement.setNoPickUpDelay();
        maid.level().addFreshEntity(replacement);
    }
}
