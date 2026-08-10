package com.laixia.maidintelligence.feature.behavior.domain.combat;

import com.laixia.maidintelligence.feature.behavior.domain.combat.sustenance.EatingPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponSelectionPolicy;

/**
 * Where the stated balance becomes the policies that read it.
 *
 * <p>One entry point rather than three, so a balance can never be half applied:
 * a maid holding the old preferred range while judging risk by the new margins
 * is a combination nobody chose and nobody could reproduce.
 *
 * <p>Installed once, when the server has finished reading its configuration,
 * and never per tick. The policies are immutable; only which one is current can
 * change, which is why the holders are {@code volatile} and why callers ask for
 * the instance at the point of use instead of caching it in a field.
 */
public final class CombatPolicies {
    private static volatile CombatBalance active = CombatBalance.defaults();

    private CombatPolicies() {
    }

    /** Adopt a balance across every policy that reads one. */
    public static void install(CombatBalance balance) {
        if (balance == null) {
            throw new IllegalArgumentException("balance");
        }
        SpacingPolicy.install(balance);
        WeaponSelectionPolicy.install(balance);
        EngagementRiskPolicy.install(balance);
        EatingPolicy.install(balance);
        active = balance;
    }

    /** The balance currently in force. */
    public static CombatBalance active() {
        return active;
    }
}
