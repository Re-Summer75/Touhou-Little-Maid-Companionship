package com.laixia.maidintelligence.feature.behavior.domain.combat;

/**
 * When she puts the weapon away.
 *
 * <p>A maid permanently holding a drawn sword reads as a guard, not a
 * companion, and it also costs her the hand she would otherwise be carrying
 * things in. But stowing the moment a fight ends is worse than never stowing:
 * mobs arrive in waves, and a maid who packs her sword between two zombies has
 * to unpack it while being hit.
 *
 * <p>So this asks for quiet, not merely for the absence of a current target.
 */
public final class WeaponStowPolicy {
    /**
     * How long the world has to stay calm first.
     *
     * <p>Ten seconds is comfortably past a mob wave's spacing while still short
     * enough that she is not standing around armed long after the fight.
     */
    private static final int DEFAULT_CALM_TICKS = 200;

    public static final WeaponStowPolicy INSTANCE =
            new WeaponStowPolicy(DEFAULT_CALM_TICKS);

    private final int calmTicks;

    public WeaponStowPolicy(int calmTicks) {
        this.calmTicks = calmTicks;
    }

    /**
     * Whether the weapon in her hand should go back into the pack.
     *
     * @param ticksSinceCombat ticks since she last had a fight to be in
     * @param threatNearby     whether anything she would fight is still around
     * @param holdingWeapon    whether her main hand actually holds a weapon
     */
    public boolean shouldStow(
            int ticksSinceCombat,
            boolean threatNearby,
            boolean holdingWeapon
    ) {
        if (!holdingWeapon || threatNearby) {
            return false;
        }
        return ticksSinceCombat >= calmTicks;
    }

    /** Ticks of quiet required, so the adapter can size its own timers. */
    public int calmTicks() {
        return calmTicks;
    }
}
