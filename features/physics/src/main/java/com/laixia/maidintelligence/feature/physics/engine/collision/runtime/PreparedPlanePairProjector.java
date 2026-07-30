package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;


import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Vector3f;

/**
 * Solves the unit-sphere intersection of two active Plane boundaries.
 */
final class PreparedPlanePairProjector {
    private static final float EPSILON = 1.0E-6F;
    private static final double PARALLEL_EPSILON = 1.0E-14D;

    private PreparedPlanePairProjector() {
    }

    static boolean project(
            PreparedCollisionProxy first,
            PreparedCollisionProxy second,
            Vector3f direction,
            CollisionScratch scratch,
            Vector3f base,
            Vector3f tangent
    ) {
        if (first.kind() != CollisionProxyKind.PLANE
                || second.kind() != CollisionProxyKind.PLANE
                || first.clearance(direction, scratch) >= -EPSILON) {
            return false;
        }
        Vector3f firstNormal = first.normal();
        Vector3f secondNormal = second.normal();
        float firstDot = minimumDot(first);
        float secondDot = minimumDot(second);
        double normalDot = (double) firstNormal.x * secondNormal.x
                + (double) firstNormal.y * secondNormal.y
                + (double) firstNormal.z * secondNormal.z;
        double crossX = (double) firstNormal.y * secondNormal.z
                - (double) firstNormal.z * secondNormal.y;
        double crossY = (double) firstNormal.z * secondNormal.x
                - (double) firstNormal.x * secondNormal.z;
        double crossZ = (double) firstNormal.x * secondNormal.y
                - (double) firstNormal.y * secondNormal.x;
        double denominator = crossX * crossX
                + crossY * crossY
                + crossZ * crossZ;
        if (denominator <= PARALLEL_EPSILON
                || Math.abs(firstDot) > 1.0F
                || Math.abs(secondDot) > 1.0F) {
            return false;
        }
        double firstWeight =
                (firstDot - secondDot * normalDot) / denominator;
        double secondWeight =
                (secondDot - firstDot * normalDot) / denominator;
        double baseX = firstNormal.x * firstWeight
                + secondNormal.x * secondWeight;
        double baseY = firstNormal.y * firstWeight
                + secondNormal.y * secondWeight;
        double baseZ = firstNormal.z * firstWeight
                + secondNormal.z * secondWeight;
        double remaining = 1.0D
                - baseX * baseX - baseY * baseY - baseZ * baseZ;
        if (remaining < -EPSILON) {
            return false;
        }
        base.set((float) baseX, (float) baseY, (float) baseZ);
        double scale = Math.sqrt(
                Math.max(0.0D, remaining) / denominator
        );
        tangent.set(
                (float) (crossX * scale),
                (float) (crossY * scale),
                (float) (crossZ * scale)
        );
        float side = direction.dot(tangent);
        direction.set(base);
        if (side >= 0.0F) {
            direction.add(tangent);
        } else {
            direction.sub(tangent);
        }
        return true;
    }

    private static float minimumDot(PreparedCollisionProxy proxy) {
        Vector3f pivot = proxy.pivot();
        Vector3f point = proxy.pointA();
        Vector3f normal = proxy.normal();
        float distance = (pivot.x - point.x) * normal.x
                + (pivot.y - point.y) * normal.y
                + (pivot.z - point.z) * normal.z;
        return (proxy.projectionHitRadius() - distance)
                / proxy.preparedLeverArm();
    }
}
