package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.WeaponKind;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * When to let go, asked of the weapon rather than counted out in ticks.
 *
 * <p>Every ranged weapon answers "am I ready" differently, and the numbers are
 * not ours to know: a modded bow may reach full draw in five ticks, a modded
 * crossbow may load in forty, and enchantments move both. Hard-coding any of
 * them produces the same failure — she holds the weapon at full draw forever,
 * waiting for a moment defined by a different weapon than the one she is
 * holding.
 *
 * <p>So the order here is: state first ({@code isCharged} on anything that
 * tracks it), then the item's own declared use duration, and only then a curve.
 * The last resort is a timeout, which exists solely so an item that never
 * reports ready still fires instead of freezing her.
 */
public final class RangedDrawCycle {
    /**
     * The point past which she fires regardless.
     *
     * <p>Not a charge time — a deadlock breaker. Any weapon whose readiness
     * this class cannot read would otherwise take her out of the fight
     * permanently, which is strictly worse than one weak shot.
     */
    private static final int MAX_DRAW_TICKS = 60;

    /**
     * Above this, a declared use duration means "hold as long as you like".
     *
     * <p>Vanilla bows and tridents say 72000 ticks — an hour — which is the
     * idiom for unbounded rather than a real duration. Anything genuinely
     * bounded (a crossbow's load time) sits far below this, so the split
     * separates "use it up" from "charge it until it is worth releasing"
     * without naming a single item.
     */
    private static final int HELD_INDEFINITELY_TICKS = 200;

    private RangedDrawCycle() {
    }

    /** Whether the weapon in her hands has finished whatever it was doing. */
    public static boolean readyToRelease(
            EntityMaid maid,
            ItemStack weapon,
            WeaponKind kind,
            RangedWeaponRecognizer external
    ) {
        int drawn = maid.getTicksUsingItem();
        if (drawn >= MAX_DRAW_TICKS) {
            return true;
        }
        if (kind == WeaponKind.EXTERNAL_RANGED) {
            return drawn >= Math.max(0, external.chargeTicks(weapon));
        }
        // Loading and firing are two separate actions on a crossbow. Treating
        // them as one long charge leaves her cranking a weapon that is already
        // loaded — which is exactly the bolt that never comes out.
        if (weapon.getItem() instanceof CrossbowItem
                && CrossbowItem.isCharged(weapon)) {
            return true;
        }
        int duration = weapon.getUseDuration();
        if (duration <= 0) {
            return true;
        }
        if (duration < HELD_INDEFINITELY_TICKS) {
            // The item stated how long it takes; spending that is the whole
            // answer. Asking it beats recomputing a vanilla crossbow's timing
            // and applying it to something that is not one.
            return maid.getUseItemRemainingTicks() <= 0;
        }
        return BowItem.getPowerForTime(drawn) >= 1.0F;
    }

    /**
     * How hard the shot goes off.
     *
     * <p>Only weapons that can be held indefinitely earn their power from the
     * draw. Anything with a stated duration was either ready or it was not, so
     * a fraction of it would be inventing a half-loaded crossbow.
     */
    public static float releasePower(EntityMaid maid, ItemStack weapon) {
        return weapon.getUseDuration() >= HELD_INDEFINITELY_TICKS
                ? BowItem.getPowerForTime(maid.getTicksUsingItem())
                : 1.0F;
    }
}
