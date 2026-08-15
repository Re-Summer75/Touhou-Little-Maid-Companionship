package com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.TaskEquipUtil;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard.ShieldGuard;
import com.laixia.maidintelligence.feature.status.tlm.MaidOffhand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;

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
     * Move a weapon parked in the off hand back into the pack.
     *
     * <p>军械表只看主手和背包，副手从来不是它的一个选项——而本体那边**也没有任
     * 何一条把副手东西拿出来的路**。两件事合起来的效果是：插在副手的那把剑对她
     * 而言不存在，她拿不出来、换不过去、也不会因为它而放弃更差的主手武器，而那
     * 一格同时还堵着盾。
     *
     * <p>与其给军械表加一个"副手"位置，不如让这条不变量成立：**副手只放盾，或
     * 她这一刻正在用的东西。**别的武器回背包，回去之后现成的换手就能把它拿到主
     * 手，盾也重新有地方放。一个动作解开三处。
     *
     * <p>只管军械表认得的东西。火把、图腾、地图不是武器，动它们既没有理由，也
     * 会真的害她——副手那个图腾是她少挨一次死的全部原因。
     */
    public void unpark(EntityMaid maid) {
        ItemStack held = maid.getOffhandItem();
        if (held.isEmpty()
                || held.canPerformAction(ToolActions.SHIELD_BLOCK)
                || !weapons.isWeapon(held)) {
            return;
        }
        MaidOffhand.vacate(maid);
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
        // A raised shield is not a draw. Both answer {@code isUsingItem}, and
        // conflating them wedges her: the guard goes up on one tick, and from
        // the next tick on this method sees "she is using something usable" and
        // returns before ever reaching the swap. Measured in the vindicator
        // benchmark as a stance that had already flipped to MELEE while her hand
        // still held the bow, for the rest of the fight — she chose right and
        // could not act on it.
        //
        // Asked of the off hand rather than of a flag, the same way
        // {@code Engagement.strike} asks it. There is no third thing that
        // occupies the use slot without being one of these two.
        if (maid.isUsingItem() && !ShieldGuard.raised(maid)) {
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
