package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Vector3f;

/**
 * Runs the allocation-free relaxation and overlap-suppression hot path.
 */
final class PreparedCollisionProjectionLoop {
    private static final int MAX_PASSES = 96;

    private PreparedCollisionProjectionLoop() {
    }

    static boolean project(
            PreparedCollisionProxySet set,
            Vector3f direction,
            CollisionScratch scratch,
            int maxPasses
    ) {
        int count = set.liveCount();
        if (count == 0) {
            return false;
        }
        set.applyMeshExtent(scratch);
        boolean corrected = false;
        int passes = count == 1
                ? 1
                : Math.max(1, Math.min(MAX_PASSES, maxPasses));
        boolean settled = true;
        releaseSuppressed(set, direction, count, scratch);
        for (int pass = 0; pass < passes; pass++) {
            set.passStart.set(direction);
            boolean passCorrected = false;
            for (int slot = 0; slot < count; slot++) {
                if (set.liveProxy(slot).project(direction, scratch)) {
                    passCorrected = true;
                    set.contactOwner.recordResponder(slot);
                }
            }
            if (passCorrected && count == 2) {
                boolean pairCorrected = PreparedPlanePairProjector.project(
                        set.liveProxy(0),
                        set.liveProxy(1),
                        direction,
                        scratch,
                        set.pairBase,
                        set.pairTangent
                );
                passCorrected |= pairCorrected;
                if (pairCorrected) {
                    // The coupled answer cannot be attributed to either plane.
                    set.contactOwner.clearResponder();
                }
            }
            corrected |= passCorrected;
            if (!passCorrected
                    || direction.distanceSquared(set.passStart) <= 1.0E-12F) {
                break;
            }
            /*
             * Still moving on the final pass, so relaxation never converged.
             * One collider is satisfied in a single pass and stays satisfied;
             * two that want opposite things take turns undoing each other for
             * as many passes as they are given.
             */
            settled = pass < passes - 1;
        }
        /*
         * Only once relaxation has failed to converge. Suppressing on any resolved
         * overlap was tried and is too eager: a collider that was merely holding
         * cloth up gets silenced along with the ones that were fighting, and wind
         * then pushed the cloth of winefox_little straight through — its worst axis
         * went from -1.10 to -1.56 and winefox_matured's buzz from 0.5 to 2.0.
         * Non-convergence is the one moment there is evidence the demands cannot
         * all be met.
         */
        if (!settled) {
            suppressLosers(set, direction, count, scratch);
        }
        /*
         * The pose the last pass reached is handed back as it stands, even when
         * the passes never agreed. Averaging the poses they visited was tried, on
         * the reasoning that which one the count stops on is arbitrary — single
         * passes here move the direction by up to 1.03 rad, and one slot reported
         * an identical 0.606 pass after pass. It is arbitrary, but the mean is
         * worse: it satisfies no bound exactly, so cloth settles further inside
         * every collider it is caught between. Contacts rose across the models
         * and the buzz it was aimed at did not move at all.
         */
        if (!settled) {
            scratch.setUnresolved(true);
        }
        /*
         * Whether the segment still lies against anything, asked separately from
         * whether anything pushed it. Contact support keys on the push, and a
         * segment held exactly on a surface is pushed by nothing at all, so the
         * push alone cannot distinguish resting contact from a collider that has
         * moved away. Measured in game on winefox's FR1: support sat between 0.67
         * and 1.00 for a second and a half with no projection whatsoever, and the
         * stale surface it kept cancelling the spring against let the segment
         * wander 38 px from its equilibrium.
         */
        scratch.setRestClearance(
                nearestClearance(set, direction, count, scratch)
        );
        scratch.clearMeshExtent();
        return corrected;
    }

    /**
     * Suppresses colliders the latest responder pushed the segment into. The
     * responder remains authoritative so opposing colliders do not swap roles.
     */
    static boolean resolveRecurringContact(
            PreparedCollisionProxySet set,
            boolean recurringContact,
            Vector3f projectedDirection,
            CollisionScratch scratch
    ) {
        if (!recurringContact) {
            set.contactOwner.clearResponder();
            return false;
        }
        int count = set.liveCount();
        int responder = set.contactOwner.seriesResponderSlot();
        if (responder < 0 || responder >= count) {
            return false;
        }
        set.applyMeshExtent(scratch);
        boolean suppressed = false;
        for (int slot = 0; slot < count; slot++) {
            if (slot == responder) {
                continue;
            }
            PreparedCollisionProxy competitor = set.liveProxy(slot);
            if (!competitor.isSuppressed()
                    && competitor.clearance(
                            projectedDirection, scratch
                    ) < 0.0F) {
                competitor.suppress();
                suppressed = true;
            }
        }
        if (suppressed) {
            set.contactOwner.retain(set.spatial.proxyIndex(responder));
            scratch.setRestClearance(nearestClearance(
                    set, projectedDirection, count, scratch
            ));
        } else {
            set.contactOwner.clearResponder();
        }
        scratch.clearMeshExtent();
        return suppressed;
    }

    /** Clearance to whichever live collider the segment sits nearest to. */
    private static float nearestClearance(
            PreparedCollisionProxySet set,
            Vector3f direction,
            int count,
            CollisionScratch scratch
    ) {
        float nearest = Float.MAX_VALUE;
        for (int slot = 0; slot < count; slot++) {
            PreparedCollisionProxy proxy = set.liveProxy(slot);
            if (proxy.isSuppressed()) {
                continue;
            }
            nearest = Math.min(
                    nearest,
                    proxy.clearance(direction, scratch)
            );
        }
        return nearest;
    }

    /** Lets go of any collider the segment has since left. */
    private static void releaseSuppressed(
            PreparedCollisionProxySet set,
            Vector3f direction,
            int count,
            CollisionScratch scratch
    ) {
        boolean held = false;
        for (int slot = 0; slot < count; slot++) {
            held |= set.liveProxy(slot).holdSuppression(direction, scratch);
        }
        if (!held) {
            set.contactOwner.release();
        }
    }

    /**
     * Silences every overlapped collider but the deepest.
     *
     * <p>Called only when relaxation failed to converge, which is the signature of
     * colliders that cannot all be satisfied: one is answered in a single pass and
     * stays answered, while two wanting opposite things take turns undoing each
     * other for as many passes as they are given, and the segment shakes between
     * them. Resolving one and letting the segment sit inside the rest trades a
     * shake for an overlap. That is the better trade here — a seated pose overlaps
     * by design, and an overlap that holds still reads as cloth resting on a body
     * while the same depth alternating reads as a buzz.
     *
     * <p>The deepest keeps its voice because it is the one whose overlap would be
     * most visible, and because a limb arriving into a settled skirt arrives deep.
     */
    private static void suppressLosers(
            PreparedCollisionProxySet set,
            Vector3f direction,
            int count,
            CollisionScratch scratch
    ) {
        int deepest = -1;
        float worst = 0.0F;
        for (int slot = 0; slot < count; slot++) {
            PreparedCollisionProxy proxy = set.liveProxy(slot);
            if (proxy.isSuppressed()) {
                continue;
            }
            float gap = proxy.clearance(direction, scratch);
            if (gap < worst) {
                worst = gap;
                deepest = slot;
            }
        }
        if (deepest < 0) {
            return;
        }
        boolean suppressed = false;
        for (int slot = 0; slot < count; slot++) {
            PreparedCollisionProxy proxy = set.liveProxy(slot);
            if (slot == deepest || proxy.isSuppressed()) {
                continue;
            }
            if (proxy.clearance(direction, scratch) < 0.0F) {
                proxy.suppress();
                suppressed = true;
            }
        }
        if (suppressed) {
            set.contactOwner.retain(set.spatial.proxyIndex(deepest));
        }
    }
}
