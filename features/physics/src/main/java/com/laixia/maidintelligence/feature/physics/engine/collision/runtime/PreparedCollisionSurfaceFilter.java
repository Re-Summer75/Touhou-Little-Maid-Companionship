package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProjector;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import org.joml.Vector3f;

import java.util.List;

/**
 * Removes fully covered faces from rigid compound box surfaces at load time.
 */
final class PreparedCollisionSurfaceFilter {
    private static final float PROBE = 1.0E-4F;
    private static final float CONTAINMENT_EPSILON = 2.5E-5F;
    private static final float BOUNDARY_EPSILON = 2.0E-4F;
    private static final float NORMAL_ALIGNMENT = 0.9999F;

    private final Vector3f point = new Vector3f();

    private PreparedCollisionSurfaceFilter() {
    }

    static void markHiddenBoxFaces(List<PreparedCollisionShape> shapes) {
        PreparedCollisionSurfaceFilter filter =
                new PreparedCollisionSurfaceFilter();
        for (int first = 0; first < shapes.size(); first++) {
            PreparedCollisionShape candidate = shapes.get(first);
            if (!closedBox(candidate)) {
                continue;
            }
            for (int face = 0; face < 6; face++) {
                for (int second = 0; second < shapes.size(); second++) {
                    PreparedCollisionShape cover = shapes.get(second);
                    if (first != second
                            && closedBox(cover)
                            && candidate.referenceNodeIndex()
                            == cover.referenceNodeIndex()
                            && filter.coversFace(candidate, cover, face)) {
                        candidate.hideFace(face);
                        break;
                    }
                }
            }
        }
    }

    private boolean coversFace(
            PreparedCollisionShape candidate,
            PreparedCollisionShape cover,
            int face
    ) {
        int fixedAxis = face >> 1;
        float sign = (face & 1) == 0 ? 1.0F : -1.0F;
        Vector3f half = candidate.restHalfExtents();
        float fixed = component(half, fixedAxis) * sign + PROBE * sign;
        int firstFree = (fixedAxis + 1) % 3;
        int secondFree = (fixedAxis + 2) % 3;
        if (!touchesOpposingBoundary(
                candidate, cover, fixedAxis, sign
        )) {
            return false;
        }
        float firstExtent = Math.max(
                0.0F, component(half, firstFree) - PROBE
        );
        float secondExtent = Math.max(
                0.0F, component(half, secondFree) - PROBE
        );
        if (!containsSample(
                candidate, cover, fixedAxis, fixed,
                firstFree, 0.0F, secondFree, 0.0F
        )) {
            return false;
        }
        for (int firstSign = -1; firstSign <= 1; firstSign += 2) {
            for (int secondSign = -1; secondSign <= 1; secondSign += 2) {
                if (!containsSample(
                        candidate, cover, fixedAxis, fixed,
                        firstFree, firstExtent * firstSign,
                        secondFree, secondExtent * secondSign
                )) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Only exact seams are removed. Deeply overlapping boxes retain their
     * nearest escape faces; opening those would turn a shallow overlap into a
     * correction all the way to the compound's far side.
     */
    private boolean touchesOpposingBoundary(
            PreparedCollisionShape candidate,
            PreparedCollisionShape cover,
            int fixedAxis,
            float sign
    ) {
        Vector3f normal = axis(candidate, fixedAxis);
        float nx = normal.x * sign;
        float ny = normal.y * sign;
        float nz = normal.z * sign;
        point.set(candidate.restCenter()).fma(
                component(candidate.restHalfExtents(), fixedAxis) * sign,
                normal
        );
        float dx = point.x - cover.restCenter().x;
        float dy = point.y - cover.restCenter().y;
        float dz = point.z - cover.restCenter().z;
        for (int axis = 0; axis < 3; axis++) {
            Vector3f coverAxis = axis(cover, axis);
            float alignment = nx * coverAxis.x
                    + ny * coverAxis.y
                    + nz * coverAxis.z;
            if (Math.abs(alignment) < NORMAL_ALIGNMENT) {
                continue;
            }
            float local = dx * coverAxis.x
                    + dy * coverAxis.y
                    + dz * coverAxis.z;
            float expected = -Math.copySign(
                    component(cover.restHalfExtents(), axis),
                    alignment
            );
            return Math.abs(local - expected) <= BOUNDARY_EPSILON;
        }
        return false;
    }

    private boolean containsSample(
            PreparedCollisionShape candidate,
            PreparedCollisionShape cover,
            int fixedAxis,
            float fixed,
            int firstFree,
            float first,
            int secondFree,
            float second
    ) {
        point.set(candidate.restCenter())
                .fma(fixed, axis(candidate, fixedAxis))
                .fma(first, axis(candidate, firstFree))
                .fma(second, axis(candidate, secondFree));
        float dx = point.x - cover.restCenter().x;
        float dy = point.y - cover.restCenter().y;
        float dz = point.z - cover.restCenter().z;
        Vector3f half = cover.restHalfExtents();
        return within(dx, dy, dz, cover.restAxisX(), half.x)
                && within(dx, dy, dz, cover.restAxisY(), half.y)
                && within(dx, dy, dz, cover.restAxisZ(), half.z);
    }

    private static boolean within(
            float dx,
            float dy,
            float dz,
            Vector3f axis,
            float half
    ) {
        float local = dx * axis.x + dy * axis.y + dz * axis.z;
        return Math.abs(local) <= half + CONTAINMENT_EPSILON;
    }

    private static Vector3f axis(
            PreparedCollisionShape shape,
            int index
    ) {
        return switch (index) {
            case 0 -> shape.restAxisX();
            case 1 -> shape.restAxisY();
            default -> shape.restAxisZ();
        };
    }

    private static float component(Vector3f vector, int index) {
        return switch (index) {
            case 0 -> vector.x;
            case 1 -> vector.y;
            default -> vector.z;
        };
    }

    private static boolean closedBox(PreparedCollisionShape shape) {
        return shape.kind() == CollisionProxyKind.BOX
                && shape.openAxis() == CollisionProjector.CLOSED_BOX;
    }
}
