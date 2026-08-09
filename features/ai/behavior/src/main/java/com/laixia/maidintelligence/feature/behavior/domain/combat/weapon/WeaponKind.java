package com.laixia.maidintelligence.feature.behavior.domain.combat.weapon;

/**
 * What a weapon asks of the maid holding it.
 *
 * <p>Grouped by how it is used rather than by what it is, because the decision
 * this feeds only ever asks two things: may she hurt something at range with
 * it, and does firing it consume something she has to be carrying. A netherite
 * sword and a wooden axe differ in damage, not in kind.
 *
 * <p>{@link #EXTERNAL_RANGED} exists so a gun from another mod can be answered
 * by an adapter-side recogniser without this vocabulary having to name it.
 * Ammunition for such a weapon may live in its NBT, in a magazine item or in a
 * loaded state — the stable layer only receives the verdict.
 */
public enum WeaponKind {
    /** Hurts what it can reach. Never needs ammunition. */
    MELEE(false, false),
    /** Drawn, held, released. Consumes a projectile from the inventory. */
    BOW(true, true),
    /** Loaded once, then fires. Consumes a projectile from the inventory. */
    CROSSBOW(true, true),
    /** Leaves her hand when used, so the weapon itself is the ammunition. */
    THROWN(true, false),
    /** Ranged weapon contributed by another mod; ammunition rules are its own. */
    EXTERNAL_RANGED(true, true);

    private final boolean ranged;
    private final boolean consumesAmmunition;

    WeaponKind(boolean ranged, boolean consumesAmmunition) {
        this.ranged = ranged;
        this.consumesAmmunition = consumesAmmunition;
    }

    /** Whether it can hurt a target she is not standing next to. */
    public boolean isRanged() {
        return ranged;
    }

    /**
     * Whether using it draws on a separate stock she must be carrying.
     *
     * <p>Thrown weapons answer {@code false} despite being ranged: the stack in
     * her hand is the stock, so "has ammunition" is the same question as "has
     * the weapon" and asking it twice would reject a perfectly good trident.
     */
    public boolean consumesAmmunition() {
        return consumesAmmunition;
    }
}
