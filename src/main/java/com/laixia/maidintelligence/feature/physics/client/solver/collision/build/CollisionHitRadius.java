package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import org.joml.Vector3f;

/**
 * Derives the endpoint radius a driven segment is held away from a collider.
 *
 * <p>This is a numerical tolerance, not a thickness. The endpoint is a point
 * on the bone axis, and where the mesh sits relative to that axis is unknown:
 * a panel may straddle it or hang off one side, so no measurement of the bone
 * can say how far the axis must stop from a surface for the mesh to look
 * flush. Deriving a radius from the bone's own bounds therefore only pushed
 * cloth off the body by however thick the bone happened to be. What the radius
 * has to buy is the margin that keeps an endpoint resting exactly on a face
 * from flickering across it, and that is a small absolute distance.
 */
final class CollisionHitRadius {
    private static final float EPSILON = 1.0E-6F;
    /**
     * Kept in Gecko pixels because it is judged against the model: the point
     * is that it stays invisible next to a cube edge, and cubes are authored
     * in pixels. Roughly a tenth of the thinnest geometry anyone draws.
     */
    private static final float TOLERANCE_PIXELS = 0.1F;
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private CollisionHitRadius() {
    }

    /**
     * @param colliderRadius collider thickness the radius is held below, so a
     *                       tolerance sized for a torso cannot swallow a thin
     *                       decorative plate
     */
    static float derive(
            PhysicsBoneGeometry.Node node,
            float colliderRadius
    ) {
        if (node == null || !node.hasGeometry()) {
            return 0.0F;
        }
        Vector3f size = node.size();
        if (smallestPositive(size.x, size.y, size.z) <= EPSILON) {
            return 0.0F;
        }
        float tolerance = TOLERANCE_PIXELS / PIXELS_PER_BLOCK;
        return colliderRadius > EPSILON
                ? Math.min(tolerance, colliderRadius * 0.15F)
                : tolerance;
    }

    private static float smallestPositive(float x, float y, float z) {
        float result = Float.POSITIVE_INFINITY;
        if (x > EPSILON) {
            result = x;
        }
        if (y > EPSILON) {
            result = Math.min(result, y);
        }
        if (z > EPSILON) {
            result = Math.min(result, z);
        }
        return Float.isFinite(result) ? result : 0.0F;
    }
}
