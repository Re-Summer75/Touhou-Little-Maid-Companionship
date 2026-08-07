package com.laixia.maidintelligence.feature.behavior.domain.combat;

import java.util.Objects;

/**
 * One weapon she could be using, as the decision needs to see it.
 *
 * <p>Carries a slot rather than an item: choosing a weapon and swapping to it
 * are separate steps, and only the adapter can perform the swap. {@code power}
 * is already normalised, so the stable layer never has to know that a diamond
 * sword deals seven damage.
 *
 * @param kind      how it is used
 * @param slot      backpack slot it sits in, or {@link #MAIN_HAND} if already held
 * @param power     normalised effectiveness in {@code [0,1]}
 * @param ammoReady whether it can actually be fired right now
 */
public record WeaponCandidate(
        WeaponKind kind,
        int slot,
        double power,
        boolean ammoReady
) {
    /** Slot value for the weapon she is already holding. */
    public static final int MAIN_HAND = -1;

    public WeaponCandidate {
        Objects.requireNonNull(kind, "kind");
        if (slot < MAIN_HAND) {
            throw new IllegalArgumentException(
                    "Slot must be a backpack index or MAIN_HAND: " + slot
            );
        }
        if (!(power >= 0.0D) || power > 1.0D) {
            // Rejects NaN as well: a candidate that cannot be ordered would
            // make the choice depend on comparison order.
            throw new IllegalArgumentException(
                    "Power must be within [0,1]: " + power
            );
        }
    }

    /** Whether she is already holding it, so choosing it costs no swap. */
    public boolean inHand() {
        return slot == MAIN_HAND;
    }

    /**
     * Whether this weapon can be used at all right now.
     *
     * <p>A bow with no arrows is not a weapon, it is an item she is carrying.
     * Treating it as usable is how a maid ends up aiming an empty bow at a
     * creeper while a sword sits in her pack.
     */
    public boolean usable() {
        return !kind.consumesAmmunition() || ammoReady;
    }
}
