package com.laixia.maidintelligence.feature.behavior.domain.combat.weapon;

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

    /**
     * Damage a weapon rated {@code 1.0} lands in one hit.
     *
     * <p>This is what {@code power} <em>means</em>, so it belongs beside the
     * component it defines rather than in whichever adapter happens to convert
     * it. A netherite sword sits near 8 and no vanilla weapon reaches 12, which
     * keeps ordinary gear well inside the range and leaves headroom for modded
     * weapons instead of clipping them all to 1.0.
     *
     * <p>It lived in two adapter classes under two names, and both had to move
     * together or the scan and the readiness check would silently disagree
     * about how strong the same sword is.
     */
    public static final double POWER_SCALE = 12.0D;

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

    /** What one hit with it lands, in raw damage. */
    public double damagePerHit() {
        return power * POWER_SCALE;
    }

    /**
     * Damage a second, given how fast she can use this kind of weapon.
     *
     * <p>Cadence is hers, not the weapon's, so it arrives as an argument. The
     * same bow in the hands of something that shoots twice a second is twice
     * the weapon, and nothing about the bow says so.
     */
    public double damagePerSecond(double usesPerSecond) {
        return damagePerHit() * Math.max(0.0D, usesPerSecond);
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
