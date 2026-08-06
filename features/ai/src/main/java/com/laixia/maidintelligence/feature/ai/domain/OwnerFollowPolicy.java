package com.laixia.maidintelligence.feature.ai.domain;

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
     * <p>Sixteen rather than ten because an errand has to be able to finish.
     * The things she goes to of her own accord — a snack cabinet across the
     * room, an apple on the floor, the way home — sit comfortably beyond ten
     * blocks, and being snapped back partway through reads to anyone watching
     * as the maid glitching rather than as a rule being enforced.
     */
    public static final double TELEPORT_DISTANCE = 16.0D;

    public static final OwnerFollowPolicy INSTANCE = new OwnerFollowPolicy();

    private OwnerFollowPolicy() {
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
