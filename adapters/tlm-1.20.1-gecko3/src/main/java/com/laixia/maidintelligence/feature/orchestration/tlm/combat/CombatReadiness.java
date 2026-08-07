package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.WeaponCandidate;
import net.minecraft.world.entity.ai.attributes.Attributes;

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
    /** Damage a weapon rated 1.0 deals; the scale weapon power is normalised on. */
    private static final double WEAPON_POWER_SCALE = 12.0D;

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
    private static final double SHOTS_PER_SECOND = 1.0D;

    /** Armour points that double what she can absorb, matching vanilla's scale. */
    private static final double ARMOUR_FOR_DOUBLE_HEALTH = 20.0D;

    private CombatReadiness() {
    }

    /** Her current fighting capability, from her gear and her body. */
    public static CombatCapability of(
            EntityMaid maid,
            List<WeaponCandidate> arsenal
    ) {
        // Her real cadence, not one swing a second. A sword reports 1.6 swings
        // per second, so assuming a flat one understated her output by more
        // than half — and that understated maid is who the risk policy was
        // deciding for, which is a large part of why "can I win this" kept
        // coming out wrong in both directions.
        double swingsPerSecond = Math.max(
                MINIMUM_SWINGS_PER_SECOND,
                maid.getAttributeValue(Attributes.ATTACK_SPEED)
        );
        double melee = 0.0D;
        double ranged = 0.0D;
        for (WeaponCandidate candidate : arsenal) {
            if (!candidate.usable()) {
                continue;
            }
            // Power is normalised against a scale where 1.0 is a very strong
            // weapon; turning it back into damage needs only the same scale.
            double damage = candidate.power() * WEAPON_POWER_SCALE;
            if (candidate.kind().isRanged()) {
                ranged = Math.max(ranged, damage * SHOTS_PER_SECOND);
            } else {
                melee = Math.max(melee, damage * swingsPerSecond);
            }
        }
        return new CombatCapability(
                effectiveHealth(maid), melee, ranged, meleeReach(maid)
        );
    }

    /** How much raw damage she can absorb, armour folded in. */
    public static double effectiveHealth(EntityMaid maid) {
        return maid.getHealth()
                * (1.0D + maid.getArmorValue() / ARMOUR_FOR_DOUBLE_HEALTH);
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
