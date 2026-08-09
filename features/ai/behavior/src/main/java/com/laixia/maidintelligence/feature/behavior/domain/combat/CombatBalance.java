package com.laixia.maidintelligence.feature.behavior.domain.combat;

/**
 * The numbers a fight is judged by, stated once and in one place.
 *
 * <p>Each policy already took its values through a constructor, and every one
 * of them was then built exactly once from a private default — so the values
 * were injectable in form and fixed in fact. A server owner who wanted her to
 * hold six blocks instead of eight, or to bail out at half health rather than a
 * third, had to recompile the mod.
 *
 * <p>That is the part worth not hardcoding. What she does with these is a
 * decision and belongs in code; what counts as "too close" or "too risky" is a
 * dial, and dials belong to whoever is running the server. The separation also
 * makes the whole balance readable in one screen instead of scattered over four
 * files as {@code DEFAULT_} constants.
 *
 * <p>Structural constants stay where they are on purpose — the number of
 * bearing sectors, the block-resolution of the terrain probe, the priority
 * bands. Those are not balance; changing them changes what the code means, and
 * a config value that can break the model is a bug with a UI.
 *
 * <p>The defaults here are the values every one of these policies shipped with,
 * so an untouched server behaves exactly as before.
 */
public record CombatBalance(
        double preferredRange,
        double retreatOvershoot,
        double safeGap,
        double meleeSuppression,
        double safetyMargin,
        double bailOutHealth,
        double survivableBlowShare,
        double blowCaution
) {
    public CombatBalance {
        positive(preferredRange, "preferredRange");
        positive(retreatOvershoot, "retreatOvershoot");
        positive(safeGap, "safeGap");
        positive(meleeSuppression, "meleeSuppression");
        positive(safetyMargin, "safetyMargin");
        fraction(bailOutHealth, "bailOutHealth");
        fraction(survivableBlowShare, "survivableBlowShare");
        positive(blowCaution, "blowCaution");
    }

    /** What she has always used. */
    public static CombatBalance defaults() {
        return new CombatBalance(
                8.0D, 3.0D, 1.0D, 0.8D, 1.2D, 0.3D, 0.25D, 2.0D
        );
    }

    private static void positive(double value, String name) {
        if (!(value > 0.0D) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    name + " must be a positive number, was " + value
            );
        }
    }

    private static void fraction(double value, String name) {
        if (!(value > 0.0D) || !(value <= 1.0D)) {
            throw new IllegalArgumentException(
                    name + " must be a share in (0, 1], was " + value
            );
        }
    }
}
