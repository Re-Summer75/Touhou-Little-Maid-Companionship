package com.laixia.maidintelligence.feature.physics.engine.collision.model.projection;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProjector;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Vector3f;

/**
 * Oriented-box clearance and fixed-length projection.
 *
 * <p>A six-bit hidden-face mask turns adjacent boxes on the same rigid
 * reference into a compound surface: covered faces no longer produce opposing
 * normals, while the neighbouring box still supplies the outer boundary.
 */
public final class BoxCollisionProjector {
    private static final int ALL_FACES = 0x3F;
    private static final float FACE_HYSTERESIS = 1.0F / 64.0F;

    private BoxCollisionProjector() {
    }

    public static boolean project(
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
            float meshReach,
            CollisionScratch scratch,
            Vector3f tip,
            Vector3f local,
            Vector3f closest,
            Vector3f normal,
            Vector3f tangent
    ) {
        tip.set(direction).mul(leverArm).add(pivot);
        localise(tip, center, axisX, axisY, axisZ, local);
        float padded = hitRadius + meshReach;
        float clearance = clearance(local, half, padded, openAxis, hiddenFaces);
        scratch.setMeasuredClearance(clearance);
        if (clearance >= 0.0F) {
            scratch.setExitFace(CollisionProjector.NO_FACE);
            return false;
        }
        if (!contactFrame(
                local, half, axisX, axisY, axisZ, center, openAxis,
                hiddenFaces, scratch, tip, closest, normal
        )) {
            scratch.setMeasuredClearance(Float.POSITIVE_INFINITY);
            scratch.setExitFace(CollisionProjector.NO_FACE);
            return false;
        }
        float pivotDistance = (pivot.x - closest.x) * normal.x
                + (pivot.y - closest.y) * normal.y
                + (pivot.z - closest.z) * normal.z;
        return CollisionProjectionPrimitives.projectMinimumDot(
                direction,
                normal,
                (padded - pivotDistance) / leverArm,
                tangent
        );
    }

    public static float clearance(
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
            float meshReach,
            Vector3f tip,
            Vector3f local
    ) {
        tip.set(direction).mul(leverArm).add(pivot);
        localise(tip, center, axisX, axisY, axisZ, local);
        return clearance(
                local,
                half,
                hitRadius + meshReach,
                openAxis,
                hiddenFaces
        );
    }

    private static float clearance(
            Vector3f local,
            Vector3f half,
            float hitRadius,
            int openAxis,
            int hiddenFaces
    ) {
        if (openAxis != CollisionProjector.CLOSED_BOX && hiddenFaces == 0) {
            return prismClearance(local, half, hitRadius, openAxis);
        }
        int visible = visibleFaces(openAxis, hiddenFaces);
        float qx = axisClearance(local.x, half.x, visible, 0);
        float qy = axisClearance(local.y, half.y, visible, 1);
        float qz = axisClearance(local.z, half.z, visible, 2);
        float deepest = Math.max(qx, Math.max(qy, qz));
        if (!Float.isFinite(deepest)) {
            return Float.POSITIVE_INFINITY;
        }
        if (deepest <= 0.0F) {
            return deepest - hitRadius;
        }
        float ox = Math.max(qx, 0.0F);
        float oy = Math.max(qy, 0.0F);
        float oz = Math.max(qz, 0.0F);
        return (float) Math.sqrt(ox * ox + oy * oy + oz * oz) - hitRadius;
    }

    private static float axisClearance(
            float value,
            float half,
            int visible,
            int axis
    ) {
        boolean positive = visible(visible, axis << 1);
        boolean negative = visible(visible, (axis << 1) | 1);
        if (positive && negative) {
            return Math.abs(value) - half;
        }
        if (positive) {
            return value - half;
        }
        if (negative) {
            return -value - half;
        }
        return Float.NEGATIVE_INFINITY;
    }

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

    private static boolean contactFrame(
            Vector3f local,
            Vector3f half,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ,
            Vector3f center,
            int openAxis,
            int hiddenFaces,
            CollisionScratch scratch,
            Vector3f tip,
            Vector3f closest,
            Vector3f normal
    ) {
        int visible = visibleFaces(openAxis, hiddenFaces);
        if (visible == 0) {
            return false;
        }
        float cx = closestCoordinate(local.x, half.x, visible, 0);
        float cy = closestCoordinate(local.y, half.y, visible, 1);
        float cz = closestCoordinate(local.z, half.z, visible, 2);
        boolean inside = cx == local.x && cy == local.y && cz == local.z;
        int face = CollisionProjector.NO_FACE;
        if (inside && openAxis != CollisionProjector.CLOSED_BOX
                && visible(visible, openAxis << 1)) {
            face = openAxis << 1;
        } else if (inside) {
            face = chooseExitFace(
                    local, half, scratch.exitFace(), visible
            );
        }
        if (inside) {
            if (face == CollisionProjector.NO_FACE) {
                return false;
            }
            scratch.setExitFace(face);
            float sign = (face & 1) == 0 ? 1.0F : -1.0F;
            switch (face >> 1) {
                case 0 -> cx = sign * half.x;
                case 1 -> cy = sign * half.y;
                default -> cz = sign * half.z;
            }
        }
        closest.set(center)
                .fma(cx, axisX)
                .fma(cy, axisY)
                .fma(cz, axisZ);
        if (inside) {
            normal.set(closest).sub(tip);
        } else {
            normal.set(tip).sub(closest);
        }
        float lengthSquared = normal.lengthSquared();
        if (lengthSquared > CollisionProjectionPrimitives.EPSILON) {
            normal.div((float) Math.sqrt(lengthSquared));
            return true;
        }
        if (face == CollisionProjector.NO_FACE) {
            face = chooseExitFace(local, half, scratch.exitFace(), visible);
        }
        if (face == CollisionProjector.NO_FACE) {
            return false;
        }
        setFaceNormal(face, axisX, axisY, axisZ, normal);
        return true;
    }

    private static float closestCoordinate(
            float value,
            float half,
            int visible,
            int axis
    ) {
        boolean positive = visible(visible, axis << 1);
        boolean negative = visible(visible, (axis << 1) | 1);
        if (positive && value > half) {
            return half;
        }
        if (negative && value < -half) {
            return -half;
        }
        return value;
    }

    private static int chooseExitFace(
            Vector3f local,
            Vector3f half,
            int previous,
            int visible
    ) {
        int nearest = CollisionProjector.NO_FACE;
        float shallowest = Float.NEGATIVE_INFINITY;
        for (int face = 0; face < 6; face++) {
            if (!visible(visible, face)) {
                continue;
            }
            int axis = face >> 1;
            float coordinate = component(local, axis);
            float depth = ((face & 1) == 0 ? coordinate : -coordinate)
                    - component(half, axis);
            if (depth > shallowest) {
                shallowest = depth;
                nearest = face;
            }
        }
        if (previous == CollisionProjector.NO_FACE
                || !visible(visible, previous)) {
            return nearest;
        }
        int heldAxis = previous >> 1;
        float held = Math.abs(component(local, heldAxis))
                - component(half, heldAxis);
        return held >= shallowest - FACE_HYSTERESIS ? previous : nearest;
    }

    private static void setFaceNormal(
            int face,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ,
            Vector3f output
    ) {
        Vector3f axis = switch (face >> 1) {
            case 0 -> axisX;
            case 1 -> axisY;
            default -> axisZ;
        };
        output.set(axis).mul((face & 1) == 0 ? 1.0F : -1.0F);
    }

    private static int visibleFaces(int openAxis, int hiddenFaces) {
        int visible = ALL_FACES & ~hiddenFaces;
        if (openAxis != CollisionProjector.CLOSED_BOX) {
            visible &= ~(1 << ((openAxis << 1) | 1));
        }
        return visible;
    }

    private static boolean visible(int mask, int face) {
        return (mask & (1 << face)) != 0;
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

    private static float component(Vector3f vector, int index) {
        return switch (index) {
            case 0 -> vector.x;
            case 1 -> vector.y;
            default -> vector.z;
        };
    }
}
