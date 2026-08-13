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
 * @param fieldHealth         health of everything she can see, unweighted — what
 *                            finishing the fight would cost in total, which is
 *                            what a magazine has to be measured against
 * @param crowding            how many arrive inside the window being planned
 *                            over — a weighted count, because a sweeping weapon
 *                            is paid once per body it reaches
 * @param hostilesPressing    how many could strike her after one step. The
 *                            micro fact the aggregate otherwise erases: every
 *                            other figure here describes the crowd as a whole
 *                            or the one hostile she picked, so "two of them are
 *                            about to be able to hit me" had nowhere to be
 *                            stated, and a decision made about the one in front
 *                            is exactly how she gets hit from the side.
 * @param underAttack         whether anything can strike her where she stands
 * @param canOpenGround       whether she has the room and the legs to back off
 * @param meleeUsesPerSecond  her swings a second
 * @param rangedUsesPerSecond her shots a second, the reciprocal of a draw
 * @param holdingMelee        whether she is already committed to swinging
 * @param guardedShare        the share of incoming blows a raised shield denies,
 *                            already discounted for what would disable it. Only
 *                            melee can collect it: the off hand holds either a
 *                            shield or a drawn bow, never both, so this is the
 *                            term that makes steel cheaper than range for a maid
 *                            who is carrying a guard — and makes it exactly as
 *                            expensive as before for one who is not
 */
public record EngagementContext(
        double distance,
        double targetReach,
        double targetHealth,
        boolean targetAirborne,
        double incomingDps,
        double secondsBetweenHits,
        double secondsToContact,
        double fieldHealth,
        double crowding,
        int hostilesPressing,
        boolean underAttack,
        boolean canOpenGround,
        double meleeUsesPerSecond,
        double rangedUsesPerSecond,
        boolean holdingMelee,
        double guardedShare
) {
    public EngagementContext {
        if (targetHealth < 0.0D || meleeUsesPerSecond < 0.0D
                || rangedUsesPerSecond < 0.0D) {
            throw new IllegalArgumentException(
                    "Engagement context must be non-negative"
            );
        }
        if (guardedShare < 0.0D || guardedShare > 1.0D) {
            throw new IllegalArgumentException(
                    "Guarded share must be a share in [0, 1], was " + guardedShare
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
        return of(
                target, field, meleeUsesPerSecond, rangedUsesPerSecond,
                canOpenGround, holdingMelee, 0.0D
        );
    }

    /**
     * The same fight, for a maid carrying a guard.
     *
     * @param guardedShare the share of blows a raised shield would deny here
     */
    public static EngagementContext of(
            ThreatSample target,
            ThreatField field,
            double meleeUsesPerSecond,
            double rangedUsesPerSecond,
            boolean canOpenGround,
            boolean holdingMelee,
            double guardedShare
    ) {
        return new EngagementContext(
                target.distance(),
                target.reach(),
                target.health(),
                target.airborne(),
                field.incomingDps(),
                field.secondsBetweenHits(),
                field.soonestContact(),
                // Everything she can see, not only what arrives inside the
                // planning window. This feeds one question — whether her
                // ammunition covers the fight — and that question does not
                // shrink because the crowd is still walking. Fed the weighted
                // figure it read zero the moment she backed off far enough,
                // which is precisely when a bow looks free: six arrows against
                // an empty field cost nothing, so she kited a hundred and fifty
                // ticks and arrived at the melee with the same six zombies and
                // no arrows.
                field.standingHealth(),
                field.crowding(),
                field.pressing(),
                // Anything at all being able to reach her, not merely the one
                // she is aiming at. A second zombie on her flank interrupts a
                // draw just as thoroughly as the one in front does.
                field.converging() > 0,
                canOpenGround,
                meleeUsesPerSecond,
                rangedUsesPerSecond,
                holdingMelee,
                guardedShare
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
