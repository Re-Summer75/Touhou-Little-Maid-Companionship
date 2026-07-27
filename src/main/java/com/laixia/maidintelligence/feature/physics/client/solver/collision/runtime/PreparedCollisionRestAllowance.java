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

    private boolean calibrated;
    private float restNormalizedPenetration;
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

    void calibrate(float clearance) {
        restNormalizedPenetration =
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
            poseNormalizedPenetration = target;
            return target != previous;
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
        float allowance = Math.max(
                restNormalizedPenetration,
                poseNormalizedPenetration
        );
        return unadjusted - allowance * runtimeScale;
    }

    void reset() {
        calibrated = false;
        restNormalizedPenetration = 0.0F;
        poseNormalizedPenetration = 0.0F;
        poseSampleTime = 0.0D;
        poseSampled = false;
        runtimeScale = 1.0F;
    }

    private static float finiteScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.0F, scale) : 1.0F;
    }
}
