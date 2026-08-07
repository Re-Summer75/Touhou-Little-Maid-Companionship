package com.laixia.maidintelligence.feature.behavior.domain.combat;

import java.util.Objects;

/**
 * How she intends to fight this target, once.
 *
 * <p>Names the posture separately from the weapon because the two can disagree:
 * a maid cornered with only a bow still has to fight at range, badly, and the
 * adapter needs to know she is doing so on purpose rather than assume a bow
 * implies room to use it.
 *
 * @param posture        what she is trying to do
 * @param weapon         weapon to use, absent only when disengaging
 * @param preferredRange distance she tries to hold, in blocks
 */
public record CombatStance(
        Posture posture,
        WeaponCandidate weapon,
        double preferredRange
) {
    public CombatStance {
        Objects.requireNonNull(posture, "posture");
        if ((posture == Posture.DISENGAGE) != (weapon == null)) {
            throw new IllegalArgumentException(
                    "Disengaging carries no weapon, and fighting requires one"
            );
        }
    }

    public enum Posture {
        /** Close to arm's length and strike. */
        MELEE,
        /** Hold the preferred range and shoot. */
        RANGED,
        /** Nothing usable to fight with; leave it to panic and to the owner. */
        DISENGAGE
    }

    /**
     * Nothing she can fight with.
     *
     * <p>Deliberately distinct from "melee with bare hands": punching a creeper
     * is worse than backing off, and the maid has no armour budget to spend on
     * proving otherwise.
     */
    public static CombatStance disengage() {
        return new CombatStance(Posture.DISENGAGE, null, 0.0D);
    }

    /** Whether she should be trying to hurt the target at all. */
    public boolean engaged() {
        return posture != Posture.DISENGAGE;
    }
}
