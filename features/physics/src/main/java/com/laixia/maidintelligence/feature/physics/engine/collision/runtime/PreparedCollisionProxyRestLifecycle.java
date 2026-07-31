package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProjector;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Vector3f;

/**
 * Owns rest-pose allowance updates and persistent overlap suppression.
 */
final class PreparedCollisionProxyRestLifecycle {
    private PreparedCollisionProxyRestLifecycle() {
    }

    static void prepareFrame(
            PreparedCollisionProxy proxy,
            float colliderScale,
            float hitScale
    ) {
        proxy.restAllowance.prepare(
                finiteScale(colliderScale),
                hitScale,
                proxy.preparedLeverArm,
                proxy.leverArm
        );
        proxy.bodyAllowance.prepare(
                finiteScale(colliderScale),
                hitScale,
                proxy.preparedLeverArm,
                proxy.leverArm
        );
        applyAllowance(proxy);
    }

    static void allowInitialRestPose(
            PreparedCollisionProxy proxy,
            Vector3f restDirection,
            CollisionScratch scratch
    ) {
        if (!needsCalibration(proxy)) {
            return;
        }
        proxy.restAllowance.calibrate(
                PreparedCollisionProxyProjection.clearanceAt(
                        proxy,
                        restDirection,
                        proxy.preparedLeverArm,
                        proxy.preparedRadius,
                        proxy.preparedHitRadius,
                        scratch
                )
        );
        float bodyArm = PreparedCollisionProxyProjection.bodyLeverArm(
                proxy, restDirection
        );
        proxy.bodyAllowance.calibrate(bodyArm <= 0.0F
                ? PreparedCollisionProxyProjection.CLEAR
                : PreparedCollisionProxyProjection.clearanceAt(
                        proxy,
                        restDirection,
                        bodyArm,
                        proxy.preparedRadius,
                        proxy.preparedHitRadius,
                        scratch
                ));
        applyAllowance(proxy);
    }

    static boolean needsCalibration(PreparedCollisionProxy proxy) {
        return proxy.restAllowance.needsCalibration(proxy.source);
    }

    /**
     * Re-measures how deep the animation alone reaches into this collider and
     * moves the allowance to match. Projection then rejects only the depth
     * secondary motion adds on top of the authored pose.
     */
    static void trackAnimationPose(
            PreparedCollisionProxy proxy,
            Vector3f restDirection,
            double poseTime,
            CollisionScratch scratch
    ) {
        if (!proxy.animationPoseAllowanceEligible
                || proxy.source == CollisionProxySource.EXPLICIT) {
            return;
        }
        /*
         * The authored depth only needs measuring where the authored pose can
         * actually reach the collider, and usually it cannot: ranking keeps
         * the colliders nearest the deflected endpoint, which is not where the
         * rest pose points. One bounding-sphere test answers those, and it has
         * to, because this runs for every surviving pairing on every driven
         * segment on every frame while a full clearance does not.
         */
        boolean changed = proxy.restAllowance.trackAnimationPose(
                PreparedCollisionProxyProjection.separated(
                        proxy,
                        restDirection,
                        proxy.preparedLeverArm,
                        proxy.preparedHitRadius
                )
                        ? PreparedCollisionProxyProjection.CLEAR
                        : PreparedCollisionProxyProjection.clearanceAt(
                                proxy,
                                restDirection,
                                proxy.preparedLeverArm,
                                proxy.preparedRadius,
                                proxy.preparedHitRadius,
                                scratch
                        ),
                poseTime
        );
        float bodyArm = PreparedCollisionProxyProjection.bodyLeverArm(
                proxy, restDirection
        );
        changed |= proxy.bodyAllowance.trackAnimationPose(
                bodyArm <= 0.0F
                        || PreparedCollisionProxyProjection.separated(
                                proxy,
                                restDirection,
                                bodyArm,
                                proxy.preparedHitRadius
                        )
                        ? PreparedCollisionProxyProjection.CLEAR
                        : PreparedCollisionProxyProjection.clearanceAt(
                                proxy,
                                restDirection,
                                bodyArm,
                                proxy.preparedRadius,
                                proxy.preparedHitRadius,
                                scratch
                        ),
                poseTime
        );
        if (changed) {
            applyAllowance(proxy);
        }
    }

    /** Stops this collider asking for anything until the overlap is gone. */
    static void suppress(PreparedCollisionProxy proxy) {
        proxy.suppressed = true;
    }

    static boolean isSuppressed(PreparedCollisionProxy proxy) {
        return proxy.suppressed;
    }

    /**
     * Lifts suppression once the segment is clear of the raw geometry rather
     * than waiting for a deadline that could expire while the cause remains.
     */
    static boolean holdSuppression(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            CollisionScratch scratch
    ) {
        if (!proxy.suppressed) {
            return false;
        }
        if (PreparedCollisionProxyProjection.clearance(
                proxy, direction, scratch
        ) >= 0.0F) {
            proxy.suppressed = false;
            /*
             * The reserves were measured before the suppression began and the
             * segment has moved freely since, so what they hold is stale by an
             * unknown amount. Invalidating them makes the next pass measure.
             */
            proxy.reserveValid = false;
            return false;
        }
        return true;
    }

    static void reset(PreparedCollisionProxy proxy) {
        resetAllowances(proxy);
        proxy.suppressed = false;
        proxy.exitFace = CollisionProjector.NO_FACE;
        proxy.bodyExitFace = CollisionProjector.NO_FACE;
        PreparedCollisionProxyMotion.invalidate(proxy);
        applyAllowance(proxy);
    }

    static void resetAllowances(PreparedCollisionProxy proxy) {
        proxy.restAllowance.reset();
        proxy.bodyAllowance.reset();
    }

    /**
     * The allowance shrinks the radii a sample has to clear, and it releases
     * over time, so those radii grow back. Growth eats into a gap measured
     * before it, which is why the reserves are charged for it here rather
     * than left to notice a contact they were told could not happen yet.
     */
    static void applyAllowance(PreparedCollisionProxy proxy) {
        float tipBefore = Math.max(
                proxy.projectionRadius,
                proxy.projectionHitRadius
        );
        float bodyBefore = Math.max(
                proxy.bodyProjectionRadius,
                proxy.bodyProjectionHitRadius
        );
        proxy.projectionHitRadius =
                proxy.restAllowance.threshold(proxy.preparedHitRadius);
        proxy.projectionRadius = Math.max(
                0.0F,
                proxy.restAllowance.threshold(proxy.preparedRadius)
        );
        proxy.bodyProjectionHitRadius =
                proxy.bodyAllowance.threshold(proxy.preparedHitRadius);
        proxy.bodyProjectionRadius = Math.max(
                0.0F,
                proxy.bodyAllowance.threshold(proxy.preparedRadius)
        );
        proxy.tipReserve -= Math.max(
                0.0F,
                Math.max(
                        proxy.projectionRadius,
                        proxy.projectionHitRadius
                ) - tipBefore
        );
        proxy.bodyReserve -= Math.max(
                0.0F,
                Math.max(
                        proxy.bodyProjectionRadius,
                        proxy.bodyProjectionHitRadius
                ) - bodyBefore
        );
    }

    private static float finiteScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.0F, scale) : 1.0F;
    }
}
