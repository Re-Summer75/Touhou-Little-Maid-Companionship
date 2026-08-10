package com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.TaskEquipUtil;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Getting the chosen weapon into her hand.
 *
 * <p>Separate from the choosing for the reason this area keeps proving: the
 * decision and the hand are different code, and a decision that is right while
 * the hand is wrong looks from outside exactly like a decision that is wrong.
 * Two of the three worst bugs in this fight lived here rather than in the
 * pricing, and both times the pricing was blamed first.
 */
public final class WeaponSwap {
    /**
     * Slack when matching a chosen weapon back to a stack in her pack.
     *
     * <p>Power is derived from the item, so the same sword scores the same
     * twice; the tolerance only absorbs floating point, not genuine difference.
     * Two different weapons that rate identically are interchangeable by the
     * only measure the choice used, so either satisfying it is correct.
     */
    private static final double POWER_MATCH_SLACK = 1.0E-6D;

    private final TlmWeaponScanner weapons;

    public WeaponSwap(TlmWeaponScanner weapons) {
        this.weapons = Objects.requireNonNull(weapons, "weapons");
    }

    /**
     * Put the chosen weapon in her hand.
     *
     * <p>The predicate has to describe the weapon well enough that no other
     * stack satisfies it. Matching on kind alone — which is all this used to
     * ask — meant the host's search took the first melee item in the pack, so a
     * decision to draw the netherite sword could equip a wooden hoe, and an
     * unusable weapon already in her hand short-circuited the search entirely
     * because an empty bow is still, by kind, a bow.
     */
    public void equip(
            EntityMaid maid,
            WeaponCandidate weapon,
            boolean heldUsable
    ) {
        if (weapon == null || weapon.inHand()) {
            return;
        }
        if (maid.isUsingItem()) {
            // A draw in progress is the only progress a ranged weapon ever
            // makes, so a usable one finishes its shot before anything is
            // swapped. This is the rule tool replacement already follows, and
            // combat was the one place missing it.
            //
            // Deferring the swap instead — let go now, swap next tick — reads
            // as the careful option and is the bug players reported as "she
            // charges and never fires": letting go clears the use state, the
            // shooting step further down the same tick sees no draw in
            // progress and starts a fresh one, and the next tick lets go of
            // that one too. She winds up once a tick forever, never reaches
            // the release, and stands at bow range being eaten while she does
            // it. Two steps that were each reasonable alone.
            if (heldUsable) {
                return;
            }
            // Nothing to protect: whatever is in her hand cannot be used, so
            // the draw was never going to produce a shot. Dropping it here
            // rather than returning is what lets the swap land on this tick
            // instead of never.
            maid.stopUsingItem();
        }
        TaskEquipUtil.tryEquipFromBackpack(
                maid, stack -> matches(maid, stack, weapon)
        );
    }

    /**
     * Whether this stack is the weapon that was chosen.
     *
     * <p>That one, not "that one or anything stronger of its kind". The looser
     * form reads as harmless — a better weapon is better — and it is what locked
     * her into an axe. The host's search consults her main hand first and stops
     * if it already satisfies the predicate; an axe rates higher than a sword,
     * so an axe in her hand satisfied every request for a sword and the search
     * never reached the pack. The ratchet only turns one way: holding the sword
     * she would swap to the axe, holding the axe she would never swap back, and
     * from outside that is a maid who owns two weapons and uses one.
     *
     * <p>Which is the wrong question anyway. Damage is not the ordering the
     * choice was made on — the whole point of pricing is that the heavier weapon
     * is sometimes the worse one, because it does not sweep, or swings too
     * slowly to fit between two incoming blows. Having the swap re-decide on
     * power throws that away at the last step.
     */
    private boolean matches(
            EntityMaid maid,
            ItemStack stack,
            WeaponCandidate weapon
    ) {
        return weapons.classifyFor(stack) == weapon.kind()
                && weapons.isUsable(maid, stack)
                && Math.abs(weapons.powerOf(stack) - weapon.power())
                        <= POWER_MATCH_SLACK;
    }
}
