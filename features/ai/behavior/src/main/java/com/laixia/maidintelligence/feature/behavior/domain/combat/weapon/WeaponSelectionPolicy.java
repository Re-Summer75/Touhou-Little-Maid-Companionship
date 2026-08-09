package com.laixia.maidintelligence.feature.behavior.domain.combat.weapon;

import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatBalance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatStance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementContext;

import java.util.Collection;
import java.util.Objects;

/**
 * Picks what she fights with, from everything she is carrying.
 *
 * <p>One comparison over every weapon she has, priced by {@link TradeCost} in
 * damage she expects to take. There is no preference order and no threshold:
 * the posture is simply whatever the winning weapon happens to be.
 *
 * <p>It used to be a ladder — range unless pressed, pressed meaning four blocks,
 * melee otherwise — and the ladder is what made her look stupid rather than
 * merely wrong. A ladder answers the same way to a phantom and a zombie because
 * it only ever reads one input. Pricing the options instead lets facts that were
 * always being measured reach the decision, and the special cases people kept
 * asking for turn out to be consequences: steel against something on top of her,
 * a bow against something in the air, closing on an archer, sweeping a swarm.
 *
 * <p>Nothing here touches the world. It receives candidates the adapter has
 * already vetted for ammunition and returns a stance; swapping, walking and
 * striking all happen elsewhere.
 */
public final class WeaponSelectionPolicy {
    /**
     * The one in force.
     *
     * <p>Its range is where she tries to stand when shooting: outside a charge,
     * inside aim drift. Stated in {@link CombatBalance}.
     */
    private static volatile WeaponSelectionPolicy instance =
            of(CombatBalance.defaults());

    private final double preferredRange;
    private final TradeCost cost;

    public WeaponSelectionPolicy(double preferredRange) {
        this.preferredRange = preferredRange;
        this.cost = new TradeCost(preferredRange);
    }

    /** Build one from the stated balance. */
    public static WeaponSelectionPolicy of(CombatBalance balance) {
        return new WeaponSelectionPolicy(balance.preferredRange());
    }

    /** The policy every caller should be asking. */
    public static WeaponSelectionPolicy instance() {
        return instance;
    }

    /**
     * Adopt a new balance.
     *
     * <p>Prefer {@link CombatPolicies#install}: a balance applied to one policy
     * and not the others is a combination nobody chose.
     */
    public static void install(CombatBalance balance) {
        instance = of(balance);
    }

    /**
     * Choose a stance for the situation she is standing in.
     *
     * @param candidates everything she could use, ammunition already resolved
     * @param context    the target and the crowd, as measured this tick
     */
    public CombatStance choose(
            Collection<WeaponCandidate> candidates,
            EngagementContext context
    ) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(context, "context");

        WeaponCandidate chosen = null;
        double chosenCost = Double.POSITIVE_INFINITY;
        WeaponCandidate fallback = null;
        double fallbackPower = Double.NEGATIVE_INFINITY;

        for (WeaponCandidate candidate : candidates) {
            if (candidate == null || !candidate.usable()) {
                continue;
            }
            // Kept separately from the priced choice on purpose. Every option
            // can be impossible at once — a bow-only maid with a zombie in her
            // face has no finite answer — and standing there holding nothing is
            // worse than the least bad weapon she owns.
            if (candidate.power() > fallbackPower) {
                fallback = candidate;
                fallbackPower = candidate.power();
            }
            double price = cost.of(candidate, context);
            if (price < chosenCost) {
                chosen = candidate;
                chosenCost = price;
            }
        }

        WeaponCandidate weapon = chosen != null ? chosen : fallback;
        if (weapon == null) {
            return CombatStance.disengage();
        }
        if (weapon.kind().isRanged()) {
            return new CombatStance(
                    CombatStance.Posture.RANGED, weapon, preferredRange
            );
        }
        return new CombatStance(CombatStance.Posture.MELEE, weapon, 0.0D);
    }

    /** The distance a ranged stance asks her to hold. */
    public double preferredRange() {
        return preferredRange;
    }
}
