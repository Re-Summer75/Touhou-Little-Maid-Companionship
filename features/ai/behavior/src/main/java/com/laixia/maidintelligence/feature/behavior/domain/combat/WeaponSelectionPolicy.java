package com.laixia.maidintelligence.feature.behavior.domain.combat;

import java.util.Collection;
import java.util.Objects;

/**
 * Picks what she fights with, from everything she is carrying.
 *
 * <p>The rule players actually asked for is "use the bow unless something is
 * already on top of me", so range preference is the default and melee is what
 * being pressed falls back to — not the other way round.
 *
 * <p>Nothing here touches the world. It receives candidates the adapter has
 * already vetted for ammunition and returns a stance; swapping, walking and
 * striking all happen elsewhere.
 */
public final class WeaponSelectionPolicy {
    /**
     * Inside this distance a bow is a liability, so melee wins if she has any.
     * Roughly the point where a zombie is already swinging.
     */
    private static final double DEFAULT_PRESSED_DISTANCE = 4.0D;

    /** Where she tries to stand when shooting: outside a charge, inside aim drift. */
    private static final double DEFAULT_PREFERRED_RANGE = 8.0D;

    /**
     * Extra distance she must win back before a melee stance releases her.
     *
     * <p>Without it the same threshold decides both directions, so a target
     * hovering near it flips her stance every tick: she backs off as an archer,
     * the target closes, she turns to swing, and the step she already took has
     * put the target out of reach. Backing away and swinging at nothing at the
     * same time is exactly what a single shared threshold produces.
     */
    private static final double DEFAULT_MELEE_HYSTERESIS = 3.0D;

    /**
     * How much better a different weapon must score before she swaps to it.
     *
     * <p>Without it she re-picks every tick and two comparable swords make her
     * shuffle her pack instead of fighting. The margin is applied to whatever is
     * already in her hand, so it also stops a swap that gains nothing.
     */
    private static final double DEFAULT_SWITCH_MARGIN = 0.15D;

    public static final WeaponSelectionPolicy INSTANCE =
            new WeaponSelectionPolicy(
                    DEFAULT_PRESSED_DISTANCE,
                    DEFAULT_PREFERRED_RANGE,
                    DEFAULT_SWITCH_MARGIN,
                    DEFAULT_MELEE_HYSTERESIS
            );

    private final double pressedDistance;
    private final double preferredRange;
    private final double switchMargin;
    private final double meleeHysteresis;

    public WeaponSelectionPolicy(
            double pressedDistance,
            double preferredRange,
            double switchMargin,
            double meleeHysteresis
    ) {
        this.pressedDistance = pressedDistance;
        this.preferredRange = preferredRange;
        this.switchMargin = switchMargin;
        this.meleeHysteresis = meleeHysteresis;
    }

    /**
     * Choose a stance for a target at {@code distance} blocks.
     *
     * @param candidates   everything she could use, ammunition already resolved
     * @param distance     current distance to the target, in blocks
     * @param holdingMelee whether she is already in a melee stance, which
     *                     widens the distance needed to leave it
     */
    public CombatStance choose(
            Collection<WeaponCandidate> candidates,
            double distance,
            boolean holdingMelee
    ) {
        Objects.requireNonNull(candidates, "candidates");
        WeaponCandidate melee = best(candidates, false);
        WeaponCandidate ranged = best(candidates, true);
        boolean pressed = distance <= enterMeleeAt(holdingMelee);

        // Being pressed means drawing a blade. This once tried to be cleverer —
        // keep shooting and give ground instead, whenever there was room and
        // the legs for it — but a maid moves at 0.7 base speed against a
        // zombie's 0.23, so "can she outpace it" is yes against nearly
        // everything, and she ended up never drawing one at all. Retreating
        // while something is already swinging is a manoeuvre; the plain answer
        // is what players expect and what was asked for.
        //
        // Preference first, availability second: falling through to the other
        // kind is what lets a bow-only maid still fight when cornered, and a
        // melee-only maid still fight at a distance she cannot control.
        if (pressed && melee != null) {
            return new CombatStance(CombatStance.Posture.MELEE, melee, 0.0D);
        }
        if (!pressed && ranged != null) {
            return new CombatStance(
                    CombatStance.Posture.RANGED,
                    ranged,
                    preferredRange
            );
        }
        if (melee != null) {
            return new CombatStance(CombatStance.Posture.MELEE, melee, 0.0D);
        }
        if (ranged != null) {
            return new CombatStance(
                    CombatStance.Posture.RANGED,
                    ranged,
                    preferredRange
            );
        }
        return CombatStance.disengage();
    }

    /** The distance that counts as pressed, wider once she is already swinging. */
    private double enterMeleeAt(boolean holdingMelee) {
        return holdingMelee
                ? pressedDistance + meleeHysteresis
                : pressedDistance;
    }

    /** Best usable candidate of one reach, or {@code null} if she has none. */
    private WeaponCandidate best(
            Collection<WeaponCandidate> candidates,
            boolean ranged
    ) {
        WeaponCandidate chosen = null;
        double chosenScore = Double.NEGATIVE_INFINITY;
        for (WeaponCandidate candidate : candidates) {
            if (candidate == null
                    || candidate.kind().isRanged() != ranged
                    || !candidate.usable()) {
                continue;
            }
            double score = score(candidate);
            if (score > chosenScore) {
                chosen = candidate;
                chosenScore = score;
            }
        }
        return chosen;
    }

    private double score(WeaponCandidate candidate) {
        return candidate.power() + (candidate.inHand() ? switchMargin : 0.0D);
    }
}
