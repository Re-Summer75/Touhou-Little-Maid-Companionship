package com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * What she brings to the fight, measured off her actual state.
 *
 * <p>This is the other half of {@link ThreatProfile}: one side reads the enemy,
 * this side reads her. Both exist because the risk policy is only as good as
 * the two numbers it compares, and a decision made from a stand-in for either
 * one is a decision about a fight that is not happening.
 */
public final class CombatReadiness {
    /** Floor on swing rate, so a missing attribute cannot zero out her damage. */
    private static final double MINIMUM_SWINGS_PER_SECOND = 0.5D;

    /**
     * Shots a second for anything ranged.
     *
     * <p>A drawn bow is about a second; a crossbow is slower and a thrown
     * trident faster, and that spread does not change any decision this number
     * feeds. Worth revisiting if it ever decides more than "is shooting an
     * option at all".
     */
    public static final double SHOTS_PER_SECOND = 1.0D;

    /** Armour points that double what she can absorb, matching vanilla's scale. */
    private static final double ARMOUR_FOR_DOUBLE_HEALTH = 20.0D;

    /**
     * How long an axe takes her shield away for.
     *
     * <p>The host's own number, from {@code EntityMaid.blockUsingShield}. It
     * lives here rather than in {@link CombatBalance} for that reason: it is not
     * a dial, it is a fact about the game this adapter is talking to.
     */
    private static final int SHIELD_DISABLE_TICKS = 100;

    private CombatReadiness() {
    }

    /** Her current fighting capability, with nothing known about the enemy. */
    public static CombatCapability of(
            EntityMaid maid,
            List<WeaponCandidate> arsenal
    ) {
        return of(maid, arsenal, List.of());
    }

    /**
     * Her current fighting capability, from her gear and her body.
     *
     * @param threats what she is facing, needed only to value her shield: a
     *                guard is worth nothing against something that takes it away
     */
    public static CombatCapability of(
            EntityMaid maid,
            List<WeaponCandidate> arsenal,
            List<ScannedThreat> threats
    ) {
        double swings = swingsPerSecond(maid);
        double melee = 0.0D;
        double ranged = 0.0D;
        for (WeaponCandidate candidate : arsenal) {
            if (!candidate.usable()) {
                continue;
            }
            if (candidate.kind().isRanged()) {
                ranged = Math.max(
                        ranged, candidate.damagePerSecond(SHOTS_PER_SECOND)
                );
            } else {
                melee = Math.max(
                        melee, candidate.damagePerSecond(swings)
                );
            }
        }
        return new CombatCapability(
                effectiveHealth(maid), melee, ranged, meleeReach(maid),
                guardedShare(maid, threats)
        );
    }

    /**
     * How much of what is coming at her a raised shield would actually deny.
     *
     * <p>Two honest reductions, no tuning constant between them.
     *
     * <p>A shield covers the direction she is facing, and she faces the one she
     * is fighting. So against a crowd it denies roughly the share of the blows
     * that arrive from in front — one attacker's worth out of however many can
     * currently reach her. This is what keeps a shield from reading as immunity
     * in the middle of a pack, which is the shape of the mistake the host's own
     * shield task makes: it raises the guard whenever anything is within eight
     * blocks and never asks what the guard is worth.
     *
     * <p>And a guard is worth nothing against something that removes it. An axe
     * disables a shield for a hundred ticks, so the damage share arriving from
     * axe-carriers is subtracted outright rather than discounted — she will be
     * holding no shield at all for most of that fight. Asked of the item through
     * {@code canDisableShield} rather than of the species, so a modded weapon
     * that disables shields is priced without this code knowing it exists.
     */
    private static double guardedShare(
            EntityMaid maid,
            List<ScannedThreat> threats
    ) {
        if (!maid.canUseShield()) {
            return 0.0D;
        }
        ItemStack shield = maid.getOffhandItem();
        double total = 0.0D;
        double denied = 0.0D;
        int reaching = 0;
        for (ScannedThreat threat : threats) {
            double dps = threat.sample().damagePerSecond();
            total += dps;
            if (threat.sample().threatensNow()) {
                reaching++;
            }
            denied += dps * deniableShare(maid, shield, threat);
        }
        double survives = total <= 0.0D ? 1.0D : denied / total;
        double coverage = 1.0D / Math.max(1, reaching);
        return Math.max(0.0D, Math.min(1.0D, coverage * survives));
    }

    /**
     * What share of this attacker's blows a guard actually stops.
     *
     * <p>All of them for anything ordinary. For something carrying a shield
     * disabler it is neither all nor none, and "none" is what this said first —
     * which reads as caution and is simply wrong. The disabling blow <em>is
     * blocked</em>; what it then costs her is the hundred ticks the host puts
     * the shield on cooldown for. So against a vindicator swinging about once a
     * second she stops one axe in five, and one axe in five is thirteen damage
     * she does not take.
     *
     * <p>Derived, not chosen: it is the attacker's own swing period over the
     * host's own disable cooldown. Nothing here is tunable, and a modded weapon
     * with a different cadence prices itself.
     */
    private static double deniableShare(
            EntityMaid maid,
            ItemStack shield,
            ScannedThreat threat
    ) {
        if (!threat.entity().getMainHandItem()
                .canDisableShield(shield, maid, threat.entity())) {
            return 1.0D;
        }
        int period = threat.sample().attackPeriod();
        if (period <= 0) {
            return 0.0D;
        }
        return Math.min(1.0D, period / (double) SHIELD_DISABLE_TICKS);
    }

    /**
     * Her real cadence, not one swing a second.
     *
     * <p>A sword reports 1.6 swings per second, so assuming a flat one
     * understated her output by more than half — and that understated maid is
     * who the risk policy was deciding for, which is a large part of why "can I
     * win this" kept coming out wrong in both directions.
     */
    public static double swingsPerSecond(EntityMaid maid) {
        return Math.max(
                MINIMUM_SWINGS_PER_SECOND,
                maid.getAttributeValue(Attributes.ATTACK_SPEED)
        );
    }

    /**
     * How much raw damage she can absorb, armour and absorption folded in.
     *
     * <p>Absorption belongs here and was missing. It is damage she gets to
     * take, which is the entire meaning of this number, and it lives on a
     * separate field from her health so reading only the health field silently
     * left it out. The visible cost was a maid who ate a golden apple, gained
     * eight points of it, saw a situation identical to the one before the
     * mouthful, and ate the next apple — down the whole stack.
     *
     * <p>Not multiplied by armour, unlike her health: absorption is consumed
     * before armour applies, so scaling it would count the plating twice.
     */
    public static double effectiveHealth(EntityMaid maid) {
        return maid.getHealth()
                * (1.0D + maid.getArmorValue() / ARMOUR_FOR_DOUBLE_HEALTH)
                + maid.getAbsorptionAmount();
    }

    /** How badly hurt she is, as a fraction of full. */
    public static double healthFraction(EntityMaid maid) {
        float max = maid.getMaxHealth();
        return max <= 0.0F ? 0.0D : maid.getHealth() / max;
    }

    private static double meleeReach(EntityMaid maid) {
        return Math.sqrt(maid.getMeleeAttackRangeSqr(maid));
    }
}
