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
 * @param reach     how far it can hurt something, in blocks; zero if unmeasured
 * @param arcDamage what one swing lands on <em>each</em> other body standing
 *                  with the target; zero for anything that does not sweep
 * @param usesPerSecond how fast <em>this</em> weapon may be used, zero if
 *                  unstated. Cadence belongs to the item, not to her: a sword
 *                  swings at 1.6 a second and an axe at 0.9, so pricing every
 *                  blade at the cadence of whichever one she happens to be
 *                  holding values an axe as a fast sword and a sword as a slow
 *                  one. Damage per hit and hits per second are separate facts
 *                  and the trade between them is most of what picks a weapon.
 * @param shots      how many more times it can be used before it runs dry,
 *                  {@link #UNLIMITED} for melee. Ten arrows against a hundred
 *                  and twenty blocks of zombie is not a ranged answer, it is a
 *                  delay with a melee fight at the end of it, and the choice
 *                  cannot see that without knowing the count.
 * @param durability what fraction of its life is left, one when unstated. A
 *                  weapon two hits from breaking is worth less than the same
 *                  weapon fresh, and something that breaks mid-fight leaves her
 *                  empty-handed in the middle of it.
 */
public record WeaponCandidate(
        WeaponKind kind,
        int slot,
        double power,
        boolean ammoReady,
        double reach,
        double arcDamage,
        double usesPerSecond,
        double durability,
        int shots
) {
    /** Stands for "as many as she needs": melee, or an unmeasured magazine. */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    /** What this weapon could still deliver in total, before it runs dry. */
    public double deliverableDamage() {
        return shots == UNLIMITED
                ? Double.POSITIVE_INFINITY
                : shots * damagePerHit();
    }

    /**
     * A candidate measured only as far as its reach.
     *
     * <p>Arc, cadence and condition come out unstated, and every one of them
     * then falls back on whatever the caller assumed. Only for callers that
     * genuinely cannot answer — the scanner always can.
     */
    public WeaponCandidate(
            WeaponKind kind,
            int slot,
            double power,
            boolean ammoReady,
            double reach
    ) {
        this(kind, slot, power, ammoReady, reach, 0.0D, 0.0D, 1.0D, UNLIMITED);
    }

    /** Slot value for the weapon she is already holding. */
    public static final int MAIN_HAND = -1;

    /**
     * A candidate whose reach nobody measured.
     *
     * <p>Only for callers that genuinely do not know — the scanner always
     * does. Zero rather than a guess, so a missing measurement shows up as
     * "she closes to melee" instead of silently inventing a range.
     */
    public WeaponCandidate(
            WeaponKind kind,
            int slot,
            double power,
            boolean ammoReady
    ) {
        this(kind, slot, power, ammoReady, 0.0D);
    }

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
