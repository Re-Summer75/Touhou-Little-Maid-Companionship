package com.laixia.maidintelligence.feature.behavior.domain.combat;

/**
 * How much room she keeps, and when she takes it back.
 *
 * <p>Holding a range is not the same as standing at it. A target that walks in
 * one block and is answered with one block of retreat produces two creatures
 * marking time — she steps, it steps, she steps — because every correction is
 * exactly consumed by the approach that provoked it. Ground has to be taken in
 * useful amounts or not at all.
 *
 * <p>The melee half is the same idea seen from the other side. Her swing has a
 * recovery, and standing inside a target's reach during that recovery is a gift:
 * she cannot attack and it can. Whether stepping out is worth it is a question
 * about time, not about reach — she gains whatever the target spends walking
 * back in, and loses nothing as long as she can be back before her next swing
 * is ready.
 */
public final class SpacingPolicy {
    /**
     * Extra ground taken beyond the range being held.
     *
     * <p>Without it a retreat ends the moment it becomes unnecessary, which is
     * also the moment the target's next step makes it necessary again. Retaking
     * a few blocks at once buys several seconds of shooting instead of one tick
     * of relief.
     */
    private static final double DEFAULT_RETREAT_OVERSHOOT = 3.0D;

    /**
     * Clearance she wants beyond a target's reach while she cannot swing.
     *
     * <p>Wide enough that stepping out is a real step. Half a block reads as
     * clearance on paper and is swallowed by the tolerance that decides whether
     * to move at all, so she would compute a standoff and then never take it.
     */
    private static final double DEFAULT_SAFE_GAP = 1.0D;

    public static final SpacingPolicy INSTANCE = new SpacingPolicy(
            DEFAULT_RETREAT_OVERSHOOT, DEFAULT_SAFE_GAP
    );

    private final double retreatOvershoot;
    private final double safeGap;

    public SpacingPolicy(double retreatOvershoot, double safeGap) {
        this.retreatOvershoot = retreatOvershoot;
        this.safeGap = safeGap;
    }

    /**
     * How far back to go when a held range has been closed on.
     *
     * @param held     the range she is trying to keep
     * @param distance how far away the target is now
     * @return blocks to give up, never negative
     */
    public double groundToGive(double held, double distance) {
        return groundToGive(held, distance, retreatOvershoot);
    }

    /**
     * The same, with the margin stated.
     *
     * <p>Melee passes zero. Overshooting is what stops a held range from being
     * nibbled away, but in melee the ground she gives is ground she has to win
     * back before her next swing — taking three extra blocks there would spend
     * the whole recovery walking and arrive late anyway.
     */
    public double groundToGive(
            double held,
            double distance,
            double overshoot
    ) {
        return Math.max(0.0D, held + overshoot - distance);
    }

    /**
     * The distance to hold in melee, given whether she can swing right now.
     *
     * <p>Zero means close in, and that is the answer whenever her attack has
     * recovered — the blow is the entire point. The interesting case is the
     * recovery: during it she cannot hit, and standing inside the target's
     * reach for that whole time is a gift of free hits.
     *
     * <p>This first asked whether there was a standing spot outside its reach
     * but inside hers, and got nothing, because melee reach in Minecraft is
     * computed from body size — a maid and a zombie are the same size, so their
     * reaches are identical and no such spot exists. But the advantage in
     * hit-and-run was never reach, it is <em>time</em>: she steps out, it walks
     * back in, and that walk is time she is not being hit. All that requires is
     * that she can disengage faster than it closes, which is what
     * {@code canDisengage} answers.
     *
     * @param canSwingNow   whether her attack has recovered
     * @param threatReach   how far it can strike
     * @param canDisengage  whether she can actually get out and back — the legs
     *                      for it and the ground to do it on
     */
    public double meleeHold(
            boolean canSwingNow,
            double threatReach,
            boolean canDisengage
    ) {
        if (canSwingNow || !canDisengage) {
            return 0.0D;
        }
        return threatReach + safeGap;
    }

    /** How far outside a reach she wants to stand while recovering. */
    public double clearanceBeyond(double threatReach) {
        return threatReach + safeGap;
    }
}
