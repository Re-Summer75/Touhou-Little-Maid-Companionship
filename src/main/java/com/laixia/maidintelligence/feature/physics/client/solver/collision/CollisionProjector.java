package com.laixia.maidintelligence.feature.physics.client.solver.collision;

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
    /**
     * How much shallower a rival face must be before a buried endpoint
     * switches to it. Picking the least-penetrating face fresh every frame is
     * the one discrete choice in the solver, and two faces of a cube are
     * equally deep along its diagonal: an idle pose trembling across that tie
     * turns the escape 90 degrees from one frame to the next, which is a
     * visible buzz rather than a settled contact. Sticking with the face the
     * endpoint entered through also keeps it from being pushed out the far
     * side once it drifts past the centre.
     */
    private static final float FACE_HYSTERESIS = 1.0F / 64.0F;

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
        return CollisionProjectionMath.projectMinimumDot(
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
        scratch.tip.set(direction).mul(leverArm).add(pivot);
        localise(scratch.tip, center, axisX, axisY, axisZ, scratch.local);
        if (clearance(scratch.local, half, hitRadius, openAxis) >= 0.0F) {
            scratch.exitFace = NO_FACE;
            return false;
        }
        contactFrame(
                scratch.local, half, axisX, axisY, axisZ, center, openAxis,
                scratch
        );
        float pivotDistance = (pivot.x - scratch.closest.x) * scratch.normal.x
                + (pivot.y - scratch.closest.y) * scratch.normal.y
                + (pivot.z - scratch.closest.z) * scratch.normal.z;
        return CollisionProjectionMath.projectMinimumDot(
                direction,
                scratch.normal,
                (hitRadius - pivotDistance) / leverArm,
                scratch.tangent
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
        scratch.tip.set(direction).mul(leverArm).add(pivot);
        localise(scratch.tip, center, axisX, axisY, axisZ, scratch.local);
        return clearance(scratch.local, half, hitRadius, openAxis);
    }

    private static void localise(
            Vector3f point,
            Vector3f center,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ,
            Vector3f output
    ) {
        float dx = point.x - center.x;
        float dy = point.y - center.y;
        float dz = point.z - center.z;
        output.set(
                dx * axisX.x + dy * axisX.y + dz * axisX.z,
                dx * axisY.x + dy * axisY.y + dz * axisY.z,
                dx * axisZ.x + dy * axisZ.y + dz * axisZ.z
        );
    }

    private static float clearance(
            Vector3f local,
            Vector3f half,
            float hitRadius,
            int openAxis
    ) {
        return openAxis == CLOSED_BOX
                ? boxClearance(local, half, hitRadius)
                : prismClearance(local, half, hitRadius, openAxis);
    }

    private static float boxClearance(
            Vector3f local,
            Vector3f half,
            float hitRadius
    ) {
        float qx = Math.abs(local.x) - half.x;
        float qy = Math.abs(local.y) - half.y;
        float qz = Math.abs(local.z) - half.z;
        float deepest = Math.max(qx, Math.max(qy, qz));
        if (deepest <= 0.0F) {
            return deepest - hitRadius;
        }
        float ox = Math.max(qx, 0.0F);
        float oy = Math.max(qy, 0.0F);
        float oz = Math.max(qz, 0.0F);
        return (float) Math.sqrt(ox * ox + oy * oy + oz * oz) - hitRadius;
    }

    /**
     * Depth below the open face once the endpoint is over the footprint, and
     * the ordinary distance to the prism once it is not. Depth is reported in
     * full rather than as the nearest way out, so an endpoint that is already
     * deep inside is pushed forward instead of squeezed out of a side.
     */
    private static float prismClearance(
            Vector3f local,
            Vector3f half,
            float hitRadius,
            int openAxis
    ) {
        float front = component(local, openAxis) - component(half, openAxis);
        float outside = 0.0F;
        for (int axis = 0; axis < 3; axis++) {
            if (axis == openAxis) {
                continue;
            }
            float over = Math.abs(component(local, axis))
                    - component(half, axis);
            if (over > 0.0F) {
                outside += over * over;
            }
        }
        if (outside <= 0.0F) {
            return front - hitRadius;
        }
        float ahead = Math.max(front, 0.0F);
        return (float) Math.sqrt(outside + ahead * ahead) - hitRadius;
    }

    private static float component(Vector3f vector, int index) {
        return switch (index) {
            case 0 -> vector.x;
            case 1 -> vector.y;
            default -> vector.z;
        };
    }

    /**
     * Writes the surface contact point into {@code closest} and its outward
     * normal into {@code normal}. The frame has to agree with
     * {@link #clearance}: the correction moves the endpoint onto this point,
     * so measuring against one feature and pushing towards another would turn
     * a hairline overlap into a large swing.
     */
    private static void contactFrame(
            Vector3f local,
            Vector3f half,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ,
            Vector3f center,
            int openAxis,
            CollisionScratch scratch
    ) {
        float cx = openAxis == 0
                ? Math.min(local.x, half.x)
                : clamp(local.x, half.x);
        float cy = openAxis == 1
                ? Math.min(local.y, half.y)
                : clamp(local.y, half.y);
        float cz = openAxis == 2
                ? Math.min(local.z, half.z)
                : clamp(local.z, half.z);
        boolean inside = cx == local.x && cy == local.y && cz == local.z;
        if (inside && openAxis == CLOSED_BOX) {
            int face = chooseExitFace(local, half, scratch.exitFace);
            scratch.exitFace = face;
            float sign = (face & 1) == 0 ? 1.0F : -1.0F;
            switch (face >> 1) {
                case 0 -> cx = sign * half.x;
                case 1 -> cy = sign * half.y;
                default -> cz = sign * half.z;
            }
        } else if (inside) {
            // The one closed face is the only way out of a prism.
            cx = openAxis == 0 ? half.x : cx;
            cy = openAxis == 1 ? half.y : cy;
            cz = openAxis == 2 ? half.z : cz;
        }
        scratch.closest.set(center)
                .fma(cx, axisX)
                .fma(cy, axisY)
                .fma(cz, axisZ);
        // Outside the box the escape runs tip -> surface; inside it reverses.
        if (inside) {
            scratch.normal.set(scratch.closest).sub(scratch.tip);
        } else {
            scratch.normal.set(scratch.tip).sub(scratch.closest);
        }
        float lengthSquared = scratch.normal.lengthSquared();
        if (lengthSquared > CollisionProjectionMath.EPSILON) {
            scratch.normal.div((float) Math.sqrt(lengthSquared));
            return;
        }
        // Tip sits exactly on the surface: escape along the contact face.
        float ax = Math.abs(cx) >= half.x ? 1.0F : 0.0F;
        float ay = Math.abs(cy) >= half.y ? 1.0F : 0.0F;
        float az = Math.abs(cz) >= half.z ? 1.0F : 0.0F;
        if (ax + ay + az <= 0.0F) {
            ay = 1.0F;
        }
        scratch.normal.zero()
                .fma(ax * Math.copySign(1.0F, cx), axisX)
                .fma(ay * Math.copySign(1.0F, cy), axisY)
                .fma(az * Math.copySign(1.0F, cz), axisZ)
                .normalize();
    }

    /**
     * Names the face a buried endpoint should leave through, as
     * {@code axis * 2} plus one when that face is on the negative side.
     * {@code previous} wins any near-tie so the escape direction stays put
     * across frames.
     */
    private static int chooseExitFace(
            Vector3f local,
            Vector3f half,
            int previous
    ) {
        float qx = Math.abs(local.x) - half.x;
        float qy = Math.abs(local.y) - half.y;
        float qz = Math.abs(local.z) - half.z;
        int axis = qx >= qy && qx >= qz ? 0 : (qy >= qz ? 1 : 2);
        float shallowest = axis == 0 ? qx : (axis == 1 ? qy : qz);
        int nearest = (axis << 1)
                | (component(local, axis) < 0.0F ? 1 : 0);
        if (previous == NO_FACE) {
            return nearest;
        }
        int heldAxis = previous >> 1;
        float held = heldAxis == 0 ? qx : (heldAxis == 1 ? qy : qz);
        return held >= shallowest - FACE_HYSTERESIS ? previous : nearest;
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    public static boolean projectSphere(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            float combinedRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        return CollisionProjectionMath.projectOutsideSphere(
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
        return CollisionProjectionMath.sphereClearance(
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
                <= CollisionProjectionMath.EPSILON) {
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
            CollisionProjectionMath.closestPointOnSegment(
                    scratch.tip,
                    start,
                    end,
                    scratch.segment,
                    scratch.closest
            );
            scratch.normal.set(scratch.tip).sub(scratch.closest);
            if (scratch.normal.length() + CollisionProjectionMath.EPSILON
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
                    <= CollisionProjectionMath.EPSILON) {
                CollisionProjectionMath.fallbackNormal(
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
        CollisionProjectionMath.closestPointOnSegment(
                scratch.tip,
                start,
                end,
                scratch.segment,
                scratch.closest
        );
        return scratch.tip.distance(scratch.closest) - combinedRadius;
    }
}
