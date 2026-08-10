package com.laixia.maidintelligence.feature.behavior.domain.combat.sustenance;

/**
 * One thing she could eat, as the decision needs to see it.
 *
 * <p>Not "a golden apple". What matters about a golden apple is that it puts
 * eight damage of absorption and four health on her for the price of a second
 * and a half of her hands, and a modded fruit that does the same is the same
 * thing. Stated that way, "is this worth eating" is arithmetic rather than a
 * list of item names — and a pack full of bread answers it differently from a
 * pack with one apple in it without anyone writing down either case.
 *
 * <p>Everything here is measured in the currencies the fight already settles in:
 * health she gets to keep, damage she gets to ignore, and seconds her hands are
 * not holding a weapon. That last one is the whole cost of eating and it is why
 * eating is a tactical act rather than a free one — a second and a half with
 * three zombies on her is a second and a half of being hit for nothing.
 *
 * @param slot            backpack slot it sits in
 * @param hungerPoints    what it restores on her hunger scale, which is what
 *                        buys regeneration later rather than health now
 * @param healthRestored  health it puts back over the life of whatever effects
 *                        it carries — a regeneration effect is health, paid
 *                        slowly, and slowly still counts inside a fight that
 *                        lasts fifteen seconds
 * @param damageAbsorbed  damage it lets her ignore outright, from absorption.
 *                        Kept apart from health because it does not heal her:
 *                        it is a shield that expires, worth exactly as much as
 *                        the fight can spend against it and no more
 * @param secondsToEat    how long her hands are busy, which is the entire cost
 */
public record FoodValue(
        int slot,
        double hungerPoints,
        double healthRestored,
        double damageAbsorbed,
        double secondsToEat
) {
    public FoodValue {
        if (slot < 0 || hungerPoints < 0.0D || healthRestored < 0.0D
                || damageAbsorbed < 0.0D || secondsToEat < 0.0D) {
            throw new IllegalArgumentException(
                    "Food value must be non-negative with a real slot"
            );
        }
    }

    /**
     * What eating it adds to the damage she can take before falling.
     *
     * <p>The two halves are added because the fight cannot tell them apart:
     * absorption is spent first and healing arrives after, and either way the
     * question "how much more can she survive" has one answer. They are stored
     * separately because only one of them survives the fight ending.
     */
    public double effectiveHealthGain() {
        return healthRestored + damageAbsorbed;
    }

    /** Whether it does anything for hunger at all. */
    public boolean nourishing() {
        return hungerPoints > 0.0D;
    }

    /**
     * Whether it is worth eating for the fight rather than for the day.
     *
     * <p>Bread is nourishing and buys nothing in the next ten seconds; an
     * apple's regeneration is worth several health inside them. Only the second
     * kind has any business being eaten while something is swinging at her.
     */
    public boolean restorative() {
        return effectiveHealthGain() > 0.0D;
    }
}
