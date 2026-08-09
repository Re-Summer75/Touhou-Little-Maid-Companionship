package com.laixia.maidintelligence.feature.behavior.domain;

/**
 * How far she may get from her owner before the world puts her back.
 *
 * <p>Walking to her owner is no longer the native task's job. Deciding to
 * follow is a decision like any other she makes, and it belongs with the rest
 * of them in the companion orchestrator, where it can be weighed against being
 * hungry, being nearly home, or being halfway to a cabinet. A separate follow
 * behaviour with its own idea of when to move was the reason those two had to
 * keep taking the walk target off each other.
 *
 * <p>What the native task is still needed for is the one case an intent cannot
 * answer: she is too far behind for walking to recover her at all, because the
 * owner is still moving away or because there is no path between them. That is
 * a backstop, not a leash, and it is the only thing left here.
 */
public final class OwnerFollowPolicy {
    /**
     * Blocks between maid and owner past which she is teleported.
     *
     * <p>TLM derived this from the restrict radius — {@code radius + 2}, so ten
     * by default. That coupling is why tuning how far she may wander also moved
     * how far she searches for work, how far the pickup sensor reaches and how
     * far she shoots; the teleport distance is stated outright here instead so
     * that changing it changes only itself.
     *
     * <p>Twenty-four rather than ten because an errand has to be able to
     * finish. The things she goes to of her own accord — a snack cabinet across
     * the room, an apple on the floor, a zombie between her and her owner — sit
     * comfortably beyond ten blocks, and being snapped back partway through
     * reads to anyone watching as the maid glitching rather than as a rule
     * being enforced.
     *
     * <p>Deliberately larger than perception, which is capped at sixteen. She
     * only ever walks toward something she has noticed, so the teleport leash
     * has to be longer than the noticing or it fires during perfectly ordinary
     * errands — a hostile spotted at the edge of perception is chased to
     * roughly there, and the leash must not tighten around that. This is the
     * one distance allowed past sixteen, and it grants no additional awareness.
     */
    public static final double TELEPORT_DISTANCE = 24.0D;

    /**
     * Slack she plans inside the leash rather than up against it.
     *
     * <p>One block. The backstop fires strictly past
     * {@link #TELEPORT_DISTANCE}, so a plan that ends exactly on it is one
     * rounding error from being undone — and the ground behind her is probed in
     * whole blocks, so it could not aim at the limit that precisely anyway.
     */
    private static final double PLANNING_MARGIN = 1.0D;

    public static final OwnerFollowPolicy INSTANCE = new OwnerFollowPolicy();

    private OwnerFollowPolicy() {
    }

    /**
     * The radius a plan of hers may reach out to.
     *
     * <p>Exists because the backstop was something that happened <em>to</em>
     * her rather than something she knew about. Retreating is the case that
     * exposed it: she is chased away from her owner, the retreat is judged
     * entirely on how much room it puts between her and the thing hitting her,
     * and the moment it succeeds past twenty-four blocks she is snapped back
     * into the middle of the fight she just escaped. Every part of that is
     * working as written and the result is a maid who cannot run away.
     *
     * <p>Never smaller than where she already stands. A maid who has somehow
     * ended up outside the leash must still be able to move — sideways, or back
     * toward her owner — and a radius that excluded her own position would
     * report every direction as blocked and pin her where she is.
     *
     * @param currentDistance blocks she currently stands from her owner
     */
    public double planningRadius(double currentDistance) {
        double planned = TELEPORT_DISTANCE - PLANNING_MARGIN;
        return Double.isFinite(currentDistance) && currentDistance > planned
                ? currentDistance
                : planned;
    }

    /**
     * How far she may walk along a heading before the backstop would fire.
     *
     * <p>The leash is a sphere around her owner, so this is where a ray leaves
     * one. Not a fixed allowance, because the same twenty-four blocks are most
     * of a retreat while she stands next to him and none of it once she is at
     * the edge; and not a case analysis either — walking toward him coming out
     * unlimited and walking away coming out shortest both fall out of the same
     * solve.
     *
     * <p>Positions arrive as an offset rather than as two points, and the
     * heading as components, so that this stays arithmetic: nothing down here
     * has vectors or a world to ask.
     *
     * @param offsetX  her position minus her owner's, east
     * @param offsetY  the same, up — counted because the backstop measures in
     *                 three dimensions, so ground that climbs still spends leash
     * @param offsetZ  the same, south
     * @param headingX east component of the heading she would walk; together
     *                 with {@code headingZ} this must be a unit vector
     * @param headingZ south component of the same
     * @return blocks she may travel along it, never negative
     */
    public double reachBeforeTeleport(
            double offsetX,
            double offsetY,
            double offsetZ,
            double headingX,
            double headingZ
    ) {
        double offsetSquared =
                offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
        double radius = planningRadius(Math.sqrt(offsetSquared));
        double along = offsetX * headingX + offsetZ * headingZ;
        // |offset + t * heading| = radius, taking the positive root. The
        // discriminant cannot go negative: the radius is never smaller than the
        // offset, so she is on or inside the sphere, and a ray from inside one
        // always leaves it exactly once ahead of itself.
        double outside = offsetSquared - radius * radius;
        double root = Math.sqrt(Math.max(0.0D, along * along - outside));
        return Math.max(0.0D, root - along);
    }

    /**
     * Whether she is far enough away that walking will not recover her.
     *
     * <p>Takes a squared distance because that is what callers already have and
     * squaring the threshold is cheaper than rooting the measurement.
     *
     * @param distanceSquared squared blocks between maid and owner
     * @return whether the backstop should fire
     */
    public boolean shouldTeleport(double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0D) {
            return false;
        }
        return distanceSquared > TELEPORT_DISTANCE * TELEPORT_DISTANCE;
    }
}
