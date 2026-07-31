package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProjector;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Vector3f;

/**
 * Sampling, projection and clearance operations for a prepared proxy.
 */
final class PreparedCollisionProxyProjection {
    /** Stands in for a measured clearance that a reject proved positive. */
    static final float CLEAR = 1.0F;
    /** Closest to the root a body sample may be resolved, as a fraction. */
    private static final float BODY_MIN = 0.35F;
    /**
     * Past this the body sample has merged with the tip; skip it.
     *
     * <p>Held close to the tip rather than at it. A collider centred just short
     * of the endpoint is the tip sample's to answer, and resolving the same
     * contact twice in one pass doubles the correction: the two samples sit
     * within a hit radius of each other, so both find it and both push.
     */
    private static final float BODY_MAX = 0.90F;

    private PreparedCollisionProxyProjection() {
    }

    /**
     * Resolves the collider against the tip, and then against whatever part of
     * the segment's own length the collider is actually in front of.
     *
     * <p>A segment is a strip of cloth, not a bead on the end of a stick.
     * Sampling the length fixes middle-of-panel contacts without new geometry:
     * the shortened lever arm places the second sample and converts its push
     * into the smaller turn that arm needs.
     */
    static boolean project(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            CollisionScratch scratch
    ) {
        if (proxy.suppressed) {
            return false;
        }
        float bodyArm = bodyLeverArm(proxy, direction);
        PreparedCollisionProxyMotion.spend(proxy, direction, bodyArm);
        boolean moved = proxy.tipReserve > 0.0F
                ? false
                : sample(
                        proxy,
                        direction,
                        proxy.preparedLeverArm,
                        proxy.projectionRadius,
                        proxy.projectionHitRadius,
                        true,
                        scratch
                );
        if (bodyArm <= 0.0F) {
            proxy.bodyExitFace = CollisionProjector.NO_FACE;
            /*
             * No sample was taken, so nothing was learned about this end of
             * the segment and there is no distance to spend next pass.
             */
            proxy.bodyReserve = 0.0F;
            return moved;
        }
        if (proxy.bodyReserve > 0.0F) {
            return moved;
        }
        return sample(
                proxy,
                direction,
                bodyArm,
                proxy.bodyProjectionRadius,
                proxy.bodyProjectionHitRadius,
                false,
                scratch
        ) || moved;
    }

    /**
     * Resolves one sample and records how much room it found, so later passes
     * can skip it while the measured room lasts.
     */
    private static boolean sample(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            float arm,
            float radius,
            float hitRadius,
            boolean tip,
            CollisionScratch scratch
    ) {
        float separation = separation(proxy, direction, arm, hitRadius);
        if (separation > 0.0F) {
            if (tip) {
                proxy.exitFace = CollisionProjector.NO_FACE;
                proxy.tipReserve = separation;
            } else {
                proxy.bodyExitFace = CollisionProjector.NO_FACE;
                proxy.bodyReserve = separation;
            }
            return false;
        }
        scratch.clearMeasuredClearance();
        boolean moved = projectSample(
                proxy, direction, arm, radius, hitRadius, tip, scratch
        );
        /*
         * A push invalidates the gap it was measured from, and a shape that
         * reports no gap leaves the sentinel behind; both land on zero, which
         * simply means measure again.
         */
        float measured = moved
                ? 0.0F
                : Math.max(0.0F, scratch.measuredClearance());
        if (tip) {
            proxy.tipReserve = measured;
        } else {
            proxy.bodyReserve = measured;
        }
        return moved;
    }

    private static boolean projectSample(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            float arm,
            float radius,
            float hitRadius,
            boolean tip,
            CollisionScratch scratch
    ) {
        return switch (proxy.shape.kind()) {
            case PLANE -> CollisionProjector.projectPlane(
                    direction, proxy.pivot, proxy.shape.pointA(),
                    proxy.shape.normal(), hitRadius, arm, scratch
            );
            case SPHERE -> CollisionProjector.projectSphere(
                    direction, proxy.pivot, proxy.shape.pointA(), radius, arm,
                    scratch
            );
            case CAPSULE -> CollisionProjector.projectCapsule(
                    direction, proxy.pivot, proxy.shape.pointA(),
                    proxy.shape.pointB(), radius, arm, scratch
            );
            case BOX -> projectBox(
                    proxy, direction, arm, hitRadius, tip, scratch
            );
        };
    }

    /**
     * Where along the segment to take the second sample, as a lever arm, or a
     * non-positive value when the tip sample already covers it.
     *
     * <p>The point chosen is the one on the segment the collider sits squarest
     * in front of, which is where it will bite first and deepest. Sampling a
     * fixed ladder of positions instead would cost a projection per rung to
     * find the same place. Contacts nearer the root than {@link #BODY_MIN} are
     * pulled out to it rather than resolved where they are: the segment turns
     * about its root, so a point that close barely moves however far it turns,
     * and asking for the turn that would clear it throws the rest of the
     * segment across the model.
     */
    static float bodyLeverArm(
            PreparedCollisionProxy proxy,
            Vector3f direction
    ) {
        /*
         * A plane has no centre to sit in front of, and being unbounded it
         * already constrains the whole segment through the tip: a straight
         * segment whose tip is clear of a half-space is clear of it along its
         * entire length.
         */
        if (proxy.shape.kind() == CollisionProxyKind.PLANE) {
            return -1.0F;
        }
        Vector3f center = proxy.shape.pointA();
        float cx = center.x;
        float cy = center.y;
        float cz = center.z;
        if (proxy.shape.kind() == CollisionProxyKind.CAPSULE) {
            Vector3f end = proxy.shape.pointB();
            cx = 0.5F * (cx + end.x);
            cy = 0.5F * (cy + end.y);
            cz = 0.5F * (cz + end.z);
        }
        float along = (cx - proxy.pivot.x) * direction.x
                + (cy - proxy.pivot.y) * direction.y
                + (cz - proxy.pivot.z) * direction.z;
        float fraction = along / proxy.preparedLeverArm;
        if (fraction <= 0.0F || fraction >= BODY_MAX) {
            return -1.0F;
        }
        return Math.max(BODY_MIN, fraction) * proxy.preparedLeverArm;
    }

    /**
     * Carries the escape face across frames. The choice is this proxy's, not
     * the scratch's: one endpoint meets many boxes per frame and each has to
     * remember the face it entered through separately. Tip and body samples
     * keep separate faces, since they can be inside a box on opposite sides.
     */
    private static boolean projectBox(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            float arm,
            float hitRadius,
            boolean tip,
            CollisionScratch scratch
    ) {
        scratch.setExitFace(tip ? proxy.exitFace : proxy.bodyExitFace);
        boolean moved = CollisionProjector.projectBox(
                direction, proxy.pivot, proxy.shape.pointA(),
                proxy.shape.axisX(), proxy.shape.axisY(), proxy.shape.axisZ(),
                proxy.shape.halfExtents(), hitRadius, proxy.shape.openAxis(),
                arm, scratch
        );
        if (tip) {
            proxy.exitFace = scratch.exitFace();
        } else {
            proxy.bodyExitFace = scratch.exitFace();
        }
        return moved;
    }

    /**
     * Bounding-sphere reject for the point {@code arm} places on the segment.
     * Most colliders a segment carries are near but not under that point on
     * any given pass, and this answers those without entering the box's local
     * frame. An unbounded shape reports a non-finite radius and is never
     * separated.
     */
    static boolean separated(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            float arm,
            float hitRadius
    ) {
        return separation(proxy, direction, arm, hitRadius) > 0.0F;
    }

    /**
     * Distance from the sample point to the collider's bounding sphere, which
     * is a lower bound on the distance to the collider itself. Positive means
     * out of contact, and by at least this much — enough to stand in for a
     * measured gap when the reject fires.
     */
    private static float separation(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            float arm,
            float hitRadius
    ) {
        Vector3f center = proxy.shape.pointA();
        float dx = proxy.pivot.x + direction.x * arm - center.x;
        float dy = proxy.pivot.y + direction.y * arm - center.y;
        float dz = proxy.pivot.z + direction.z * arm - center.z;
        /*
         * The sheet's widest reach is added to the cull radius. This test only
         * has to be conservative, and it has no contact normal to ask along, so
         * it uses the largest the sheet could reach in any direction — a sphere
         * test that under-reached would skip a contact the projection would have
         * found and let the surface pass through.
         */
        float reach = proxy.shape.cullRadius()
                + hitRadius + proxy.meshCullReach;
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared <= reach * reach) {
            return -1.0F;
        }
        return (float) Math.sqrt(distanceSquared) - reach;
    }

    static float clearance(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            CollisionScratch scratch
    ) {
        float tip = clearanceAt(
                proxy,
                direction,
                proxy.preparedLeverArm,
                proxy.projectionRadius,
                proxy.projectionHitRadius,
                scratch
        );
        float bodyArm = bodyLeverArm(proxy, direction);
        if (bodyArm <= 0.0F
                || separated(
                        proxy,
                        direction,
                        bodyArm,
                        proxy.bodyProjectionHitRadius
                )) {
            return tip;
        }
        return Math.min(tip, clearanceAt(
                proxy,
                direction,
                bodyArm,
                proxy.bodyProjectionRadius,
                proxy.bodyProjectionHitRadius,
                scratch
        ));
    }

    static float clearanceAt(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            float arm,
            float radius,
            float hitRadius,
            CollisionScratch scratch
    ) {
        return switch (proxy.shape.kind()) {
            case PLANE -> CollisionProjector.planeClearance(
                    direction, proxy.pivot, proxy.shape.pointA(),
                    proxy.shape.normal(), hitRadius, arm, scratch
            );
            case SPHERE -> CollisionProjector.sphereClearance(
                    direction, proxy.pivot, proxy.shape.pointA(), radius, arm,
                    scratch
            );
            case CAPSULE -> CollisionProjector.capsuleClearance(
                    direction, proxy.pivot, proxy.shape.pointA(),
                    proxy.shape.pointB(), radius, arm, scratch
            );
            case BOX -> CollisionProjector.boxClearance(
                    direction, proxy.pivot, proxy.shape.pointA(),
                    proxy.shape.axisX(), proxy.shape.axisY(),
                    proxy.shape.axisZ(), proxy.shape.halfExtents(), hitRadius,
                    proxy.shape.openAxis(), arm, scratch
            );
        };
    }
}
