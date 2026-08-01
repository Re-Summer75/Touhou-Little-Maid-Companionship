package com.laixia.maidintelligence.feature.physics.engine.collision.model;


import com.laixia.maidintelligence.feature.physics.engine.collision.model.projection.BoxCollisionProjector;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.projection.CollisionProjectionPrimitives;
import org.joml.Vector3f;

/**
 * Shared fixed-length projection for baked and frame-prepared proxies.
 */
public final class CollisionProjector {
    private static final int MAX_CAPSULE_ITERATIONS = 8;
    /** An ordinary box, closed on all six faces. */
    public static final int CLOSED_BOX = -1;
    /** No face has been chosen yet, or the endpoint is outside. */
    public static final int NO_FACE = -1;
    private CollisionProjector() {
    }

    public static boolean projectPlane(
            Vector3f direction,
            Vector3f pivot,
            Vector3f point,
            Vector3f normal,
            float hitRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        float pivotDistance = (pivot.x - point.x) * normal.x
                + (pivot.y - point.y) * normal.y
                + (pivot.z - point.z) * normal.z;
        return CollisionProjectionPrimitives.projectMinimumDot(
                direction,
                normal,
                (hitRadius - pivotDistance) / leverArm,
                scratch.tangent
        );
    }

    public static float planeClearance(
            Vector3f direction,
            Vector3f pivot,
            Vector3f point,
            Vector3f normal,
            float hitRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        scratch.tip.set(direction).mul(leverArm).add(pivot);
        return (scratch.tip.x - point.x) * normal.x
                + (scratch.tip.y - point.y) * normal.y
                + (scratch.tip.z - point.z) * normal.z
                - hitRadius;
    }

    /**
     * Treats the contact face of an oriented box as a local half-space. The
     * outer projection loop re-evaluates it, so edges and corners converge
     * without ever building a full box-vs-arc solution.
     *
     * <p>{@code openAxis} names the one local axis whose positive face is the
     * only way out; see {@link #boxClearance}. An endpoint already inside such
     * a box is pushed out through that face however deep it sits, since there
     * is no back to be squeezed through instead.
     */
    public static boolean projectBox(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ,
            Vector3f half,
            float hitRadius,
            int openAxis,
            float leverArm,
            CollisionScratch scratch
    ) {
        return projectBox(
                direction, pivot, center, axisX, axisY, axisZ, half,
                hitRadius, openAxis, 0, leverArm, scratch
        );
    }

    public static boolean projectBox(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ,
            Vector3f half,
            float hitRadius,
            int openAxis,
            int hiddenFaces,
            float leverArm,
            CollisionScratch scratch
    ) {
        return BoxCollisionProjector.project(
                direction, pivot, center, axisX, axisY, axisZ, half,
                hitRadius, openAxis, hiddenFaces, leverArm,
                meshReachInto(axisX, axisY, axisZ, scratch),
                scratch, scratch.tip, scratch.local, scratch.closest,
                scratch.normal, scratch.tangent
        );
    }

    /**
     * Signed distance from the endpoint to the box, negative inside.
     *
     * <p>With {@code openAxis} set the solid is a half-open prism instead: it
     * keeps its four side faces and the positive face of that axis, and runs
     * inward without end. A sheet a pixel thick cannot stop an endpoint that
     * travels further than that in one frame — the endpoint simply arrives on
     * the far side and reports no contact — whereas a prism has no far side to
     * arrive on.
     */
    public static float boxClearance(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ,
            Vector3f half,
            float hitRadius,
            int openAxis,
            float leverArm,
            CollisionScratch scratch
    ) {
        return boxClearance(
                direction, pivot, center, axisX, axisY, axisZ, half,
                hitRadius, openAxis, 0, leverArm, scratch
        );
    }

    public static float boxClearance(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ,
            Vector3f half,
            float hitRadius,
            int openAxis,
            int hiddenFaces,
            float leverArm,
            CollisionScratch scratch
    ) {
        return BoxCollisionProjector.clearance(
                direction, pivot, center, axisX, axisY, axisZ, half,
                hitRadius, openAxis, hiddenFaces, leverArm,
                meshReachInto(axisX, axisY, axisZ, scratch),
                scratch.tip, scratch.local
        );
    }

    /**
     * Sheet reach against this box, taken as the largest of its three face
     * normals.
     *
     * <p>Deliberately not the reach along whichever face the endpoint happens to
     * be nearest. Measuring and enforcing are separate calls — the rest
     * allowance calibrates from a clearance, the projection acts on one — and the
     * face they would each pick is not the same: the projection holds the face it
     * entered through under hysteresis, while a clearance query has only the
     * position to go on. Disagreeing by so much as part of a thickness makes a
     * settled pose move on its first solved frame, which is what this cost
     * before. One figure per box keeps them consistent, and it stays anisotropic
     * where it matters: it follows the box's own orientation, so a sheet is only
     * padded by its thickness against colliders that face it broadside.
     */
    private static float meshReachInto(
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ,
            CollisionScratch scratch
    ) {
        return Math.max(
                scratch.meshReach(axisX),
                Math.max(scratch.meshReach(axisY), scratch.meshReach(axisZ))
        );
    }

    public static boolean projectSphere(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            float combinedRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        return CollisionProjectionPrimitives.projectOutsideSphere(
                direction,
                pivot,
                center,
                combinedRadius,
                leverArm,
                scratch.normal,
                scratch.tangent
        );
    }

    public static float sphereClearance(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            float combinedRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        return CollisionProjectionPrimitives.sphereClearance(
                direction,
                pivot,
                center,
                combinedRadius,
                leverArm,
                scratch.tip
        );
    }

    public static boolean projectCapsule(
            Vector3f direction,
            Vector3f pivot,
            Vector3f start,
            Vector3f end,
            float combinedRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        scratch.segment.set(end).sub(start);
        if (scratch.segment.lengthSquared()
                <= CollisionProjectionPrimitives.EPSILON) {
            return projectSphere(
                    direction,
                    pivot,
                    start,
                    combinedRadius,
                    leverArm,
                    scratch
            );
        }
        boolean corrected = false;
        for (int iteration = 0;
             iteration < MAX_CAPSULE_ITERATIONS;
             iteration++) {
            scratch.tip.set(direction).mul(leverArm).add(pivot);
            CollisionProjectionPrimitives.closestPointOnSegment(
                    scratch.tip,
                    start,
                    end,
                    scratch.segment,
                    scratch.closest
            );
            scratch.normal.set(scratch.tip).sub(scratch.closest);
            if (scratch.normal.length() + CollisionProjectionPrimitives.EPSILON
                    >= combinedRadius) {
                break;
            }
            boolean changed = projectSphere(
                    direction,
                    pivot,
                    scratch.closest,
                    combinedRadius,
                    leverArm,
                    scratch
            );
            if (!changed
                    && pivot.distanceSquared(scratch.closest)
                    <= CollisionProjectionPrimitives.EPSILON) {
                CollisionProjectionPrimitives.fallbackNormal(
                        scratch.segment,
                        scratch.normal
                );
                direction.set(scratch.normal);
                changed = true;
            }
            corrected |= changed;
            if (!changed) {
                break;
            }
        }
        return corrected;
    }

    public static float capsuleClearance(
            Vector3f direction,
            Vector3f pivot,
            Vector3f start,
            Vector3f end,
            float combinedRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        scratch.tip.set(direction).mul(leverArm).add(pivot);
        CollisionProjectionPrimitives.closestPointOnSegment(
                scratch.tip,
                start,
                end,
                scratch.segment,
                scratch.closest
        );
        return scratch.tip.distance(scratch.closest) - combinedRadius;
    }
}
