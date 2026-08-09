package com.laixia.maidintelligence.feature.behavior.domain.combat;

import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponKind;

import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;

/**
 * Everything a weapon choice depends on, measured rather than named.
 *
 * <p>This exists because the choice used to depend on one number. "Closer than
 * four blocks" decided melee against everything — against a phantom she cannot
 * reach, against a skeleton she cannot out-shoot, against nine zombies and
 * against one. A rule with a single input cannot give different answers to
 * different situations, and that is the whole complaint: she was not choosing,
 * she was looking up.
 *
 * <p>Every component here is already measured somewhere else in the fight.
 * {@link ThreatField} knew the crowd was airborne and the weapon choice never
 * asked; {@code RetreatSpace} knew she was against a wall and the weapon choice
 * never asked. Gathering them into one value is most of the work — once the
 * facts reach the decision, the decision stops needing special cases.
 *
 * @param distance            blocks to the target she has picked
 * @param targetReach         how far that target can hurt her from
 * @param targetHealth        what remains to be removed from it
 * @param targetAirborne      whether a swung weapon would miss it entirely
 * @param incomingDps         damage a second arriving from the whole field
 * @param secondsBetweenHits  gap between incoming blows, infinite when quiet
 * @param secondsToContact    until the first of them can reach her, at the
 *                            speed they are actually travelling
 * @param underAttack         whether anything can strike her where she stands
 * @param canOpenGround       whether she has the room and the legs to back off
 * @param meleeUsesPerSecond  her swings a second
 * @param rangedUsesPerSecond her shots a second, the reciprocal of a draw
 * @param holdingMelee        whether she is already committed to swinging
 */
public record EngagementContext(
        double distance,
        double targetReach,
        double targetHealth,
        boolean targetAirborne,
        double incomingDps,
        double secondsBetweenHits,
        double secondsToContact,
        boolean underAttack,
        boolean canOpenGround,
        double meleeUsesPerSecond,
        double rangedUsesPerSecond,
        boolean holdingMelee
) {
    public EngagementContext {
        if (targetHealth < 0.0D || meleeUsesPerSecond < 0.0D
                || rangedUsesPerSecond < 0.0D) {
            throw new IllegalArgumentException(
                    "Engagement context must be non-negative"
            );
        }
    }

    /**
     * Build one from the pieces the fight already holds.
     *
     * @param target        the hostile she has decided to hit
     * @param field         the crowd she is standing in
     * @param canOpenGround whether backing off is something she can carry out
     * @param holdingMelee  whether she is already swinging
     */
    public static EngagementContext of(
            ThreatSample target,
            ThreatField field,
            double meleeUsesPerSecond,
            double rangedUsesPerSecond,
            boolean canOpenGround,
            boolean holdingMelee
    ) {
        return new EngagementContext(
                target.distance(),
                target.reach(),
                target.health(),
                target.airborne(),
                field.incomingDps(),
                field.secondsBetweenHits(),
                field.soonestContact(),
                // Anything at all being able to reach her, not merely the one
                // she is aiming at. A second zombie on her flank interrupts a
                // draw just as thoroughly as the one in front does.
                field.converging() > 0,
                canOpenGround,
                meleeUsesPerSecond,
                rangedUsesPerSecond,
                holdingMelee
        );
    }

    /** How long one use of a ranged weapon ties up her hands. */
    public double drawSeconds() {
        if (rangedUsesPerSecond <= 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        return 1.0D / rangedUsesPerSecond;
    }

    /** Uses a second for a weapon of this kind, in her hands. */
    public double usesPerSecond(WeaponKind kind) {
        return kind.isRanged() ? rangedUsesPerSecond : meleeUsesPerSecond;
    }
}
