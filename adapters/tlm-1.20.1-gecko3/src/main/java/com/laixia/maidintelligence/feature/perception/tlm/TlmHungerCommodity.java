package com.laixia.maidintelligence.feature.perception.tlm;

import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * How much hunger a thing can actually relieve, on the shared 0-to-1 scale.
 *
 * <p>Written once and asked by everything that advertises food — a cabinet, an
 * item on the ground, what the owner is holding. Nobody hand-authors a table of
 * which blocks are worth visiting: the game already records what every food is
 * worth, including foods added by mods this one has never heard of, so the
 * number is read rather than declared.
 *
 * <p>That is also why an empty cabinet answers zero instead of dropping out of
 * the index. Zero is a real answer — "nothing here" — and ranking subtracts a
 * distance penalty from it, so an empty cabinet sorts behind every stocked one
 * without needing a rule that says so.
 */
public final class TlmHungerCommodity {
    /**
     * A hearty cooked meal. Better food exists, but it earns its keep through
     * saturation rather than through this number, so treating anything at or
     * above this as full marks keeps the scale honest at the top.
     */
    private static final double GOOD_NUTRITION = 10.0D;

    /**
     * How much of a container's stock counts toward being worth a walk. A
     * cabinet with a few meals in it is nearly as good as a full one; what
     * matters is whether the trip will be wasted.
     */
    private static final double WORTHWHILE_STACKS = 4.0D;

    private TlmHungerCommodity() {
    }

    /**
     * @param eater whose diet decides this, since what counts as food can
     *              depend on who is asked
     * @return relief this one stack offers, or zero if it is not edible
     */
    public static double of(ItemStack stack, LivingEntity eater) {
        FoodProperties properties = properties(stack, eater);
        if (properties == null) {
            return 0.0D;
        }
        return clamp(properties.getNutrition() / GOOD_NUTRITION);
    }

    /**
     * Relief a container offers: how good its best food is, tempered by whether
     * there is enough of it to be worth the walk.
     *
     * @return zero for an empty or foodless container
     */
    public static double of(Container container, LivingEntity eater) {
        if (container == null) {
            return 0.0D;
        }
        double best = 0.0D;
        int stacks = 0;
        int size = container.getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            double value = of(container.getItem(slot), eater);
            if (value <= 0.0D) {
                continue;
            }
            stacks++;
            best = Math.max(best, value);
        }
        if (stacks == 0) {
            return 0.0D;
        }
        double stocked = Math.min(1.0D, stacks / WORTHWHILE_STACKS);
        // Half for how good it is, half for whether it will still be there.
        return clamp(best * (0.5D + 0.5D * stocked));
    }

    /** Whether this is edible at all, for callers that only need the flag. */
    public static boolean edible(ItemStack stack, LivingEntity eater) {
        return properties(stack, eater) != null;
    }

    private static FoodProperties properties(
            ItemStack stack,
            LivingEntity eater
    ) {
        return stack == null || stack.isEmpty()
                ? null
                : stack.getFoodProperties(eater);
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
