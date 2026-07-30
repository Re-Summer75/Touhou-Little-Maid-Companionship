package com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySource;

/**
 * Makes an automatic proxy constrain secondary motion relative to the pose the
 * animation already asks for, rather than in absolute terms.
 *
 * <p>Whatever overlap the authored pose itself produces is unavoidable: the
 * collider and the cloth are two parts of one model, and an author is free to
 * move a leg through a skirt or seat a body inside its own hem. Enforcing the
 * surface in absolute terms would fight that every frame — the animation pulls
 * back in, the projection pushes back out, and the part either buzzes at frame
 * rate or is shoved far from where the model was drawn. Measuring the authored
 * depth first and only rejecting what physics adds on top keeps the silhouette
 * the author built while still stopping cloth from swinging through a body.
 *
 * <p>The allowance widens immediately and narrows over {@link #RELEASE_SECONDS}
 * so a departing pose hands the surface back gradually instead of snapping it
 * shut under a part that is still inside.
 */
final class PreparedCollisionRestAllowance {
    private static final float EPSILON = 1.0E-6F;
    /** Keeps the endpoint just clear of the surface it is allowed to touch. */
    private static final float POSE_MARGIN = 1.0F / 1024.0F;
    private static final float RELEASE_SECONDS = 0.20F;
    /**
     * How fast the allowance may widen, in model pixels per second.
     *
     * <p>Two very different things both look like "the animation overlaps its
     * own collider", and they want opposite treatment. A pose settling into an
     * overlap — sitting down, an embrace, a crouch folding a hem into a thigh —
     * is the author's silhouette and has to be allowed. A collider sweeping
     * through at speed — a leg kicking out while walking — is exactly what the
     * cloth is supposed to react to, and allowing it reads as the leg passing
     * straight through the skirt.
     *
     * <p>Rate is what separates them, so the allowance simply cannot keep up
     * with a fast approach: a pose that closes over a few tenths of a second is
     * absorbed as before, while a kick outruns the allowance and stays a real
     * collision. This needs no classifier and degrades sanely at both ends —
     * unbounded would absorb everything, zero would fight every authored pose.
     */
    private static final float GROWTH_PIXELS_PER_SECOND = 2.0F;
    /** Longest single step that may earn growth budget. */
    private static final double GROWTH_WINDOW_SECONDS = 0.05D;
    private boolean calibrated;
    private float poseNormalizedPenetration;
    private double poseSampleTime;
    private boolean poseSampled;
    private float runtimeScale = 1.0F;

    void prepare(
            float colliderScale,
            float endpointScale,
            float runtimeLeverArm,
            float fixedLeverArm
    ) {
        float leverScale = runtimeLeverArm / Math.max(EPSILON, fixedLeverArm);
        runtimeScale = Math.max(
                EPSILON,
                Math.max(
                        finiteScale(colliderScale),
                        Math.max(finiteScale(endpointScale), leverScale)
                )
        );
    }

    boolean needsCalibration(CollisionProxySource source) {
        return source != CollisionProxySource.EXPLICIT && !calibrated;
    }

    /**
     * Seeds the allowance from the depth the model is drawn at, so the very
     * first solved frame does not shove a segment out of an overlap it was
     * authored inside.
     *
     * <p>The seed is only a starting value, not a floor. Holding it forever
     * would fix the allowance at whatever a single arbitrary frame happened to
     * show, and for a collider that moves — a leg, which rest-poses inside the
     * skirt it will later kick — that frame says nothing about any other. The
     * leg would then be free to swing to that depth anywhere, forever, and the
     * skirt would sit still through it. Letting the seed decay like any other
     * sample keeps the first frame honest and still lets the surface come back
     * once the collider leaves.
     */
    void calibrate(float clearance) {
        poseNormalizedPenetration =
                Math.max(0.0F, -clearance) / runtimeScale;
        calibrated = true;
    }

    /**
     * Follows the depth the animation alone reaches. {@code poseTime} is the
     * owning set's monotonic clock, so a proxy that spent several frames out
     * of reach releases by exactly the time that passed rather than by one
     * frame's worth.
     */
    boolean trackAnimationPose(float clearance, double poseTime) {
        float penetration = Math.max(0.0F, -clearance);
        float target = penetration <= 0.0F
                ? 0.0F
                : (penetration + POSE_MARGIN) / runtimeScale;
        float previous = poseNormalizedPenetration;
        double elapsed = poseSampled
                ? Math.max(0.0D, poseTime - poseSampleTime)
                : 0.0D;
        poseSampleTime = poseTime;
        poseSampled = true;
        if (target >= previous) {
            /*
             * Budget is earned by time spent in contact, so it is capped per
             * step: elapsed also counts the frames this proxy was out of reach
             * — which release needs, to hand the surface back by real time —
             * and spending that on growth would let a returning collider claim
             * a whole second of widening in one frame, which is no limit at
             * all.
             */
            double step = Math.min(elapsed, GROWTH_WINDOW_SECONDS);
            poseNormalizedPenetration = Math.min(target, previous
                    + (float) (GROWTH_PIXELS_PER_SECOND * step)
                    / runtimeScale);
            return poseNormalizedPenetration != previous;
        }
        if (elapsed <= 0.0D) {
            return false;
        }
        poseNormalizedPenetration = target
                + (previous - target)
                * (float) Math.exp(-elapsed / RELEASE_SECONDS);
        if (poseNormalizedPenetration <= EPSILON) {
            poseNormalizedPenetration = 0.0F;
        }
        return poseNormalizedPenetration != previous;
    }

    float threshold(float unadjusted) {
        return unadjusted - poseNormalizedPenetration * runtimeScale;
    }

    void reset() {
        calibrated = false;
        poseNormalizedPenetration = 0.0F;
        poseSampleTime = 0.0D;
        poseSampled = false;
        runtimeScale = 1.0F;
    }

    private static float finiteScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.0F, scale) : 1.0F;
    }
}
