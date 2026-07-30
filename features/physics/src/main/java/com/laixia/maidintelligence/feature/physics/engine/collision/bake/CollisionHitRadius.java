package com.laixia.maidintelligence.feature.physics.engine.collision.bake;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
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
 *
 * <p>Five attempts to make this carry the mesh's thickness instead all failed
 * the same way, and the reason is structural rather than a matter of tuning.
 * The radius is isotropic: it holds the endpoint off every collider at once,
 * not just the surface the sheet is resting against. Padding it by a sheet's
 * half width therefore closes the gaps between neighbouring colliders that the
 * segment needs somewhere legal to sit in, and a squeezed segment ends up
 * further inside something else. Measured on winefox seated, axis clearance
 * went from -0.12 px to between -1.86 and -3.98 px and buzzing from 0.03 to
 * between 5.8 and 50, whichever way the thickness was derived — along the bone
 * axis, along the bearing to the collider, as the narrow cross-axis extent,
 * folded into the baked radius, or applied at runtime past a clamped rest
 * allowance so the allowance could not measure it back out.
 *
 * <p>What remains genuinely unsolved is that the solver tracks a sheet by one
 * point on its axis, so the surface leads the endpoint by half a thickness.
 * Fixing that means giving the projection the sheet's extent along the contact
 * normal it computes at solve time — an anisotropic support function inside
 * {@code projectBox}, not a scalar anywhere — so that padding applies only
 * along the one direction the contact is on. A scalar radius cannot express it.
 */
final class CollisionHitRadius {
    private static final float EPSILON = 1.0E-6F;
    /**
     * Kept in Gecko pixels because it is judged against the model: the point
     * is that it stays invisible next to a cube edge, and cubes are authored
     * in pixels.
     *
     * <p>Sized by what it has to hide rather than by what it can get away with.
     * The endpoint is a point on the axis while the mesh around it has real
     * width, so a face the axis clears by nothing at all is a face the mesh is
     * still halfway through. Three tenths of a pixel is a fiftieth of a block —
     * invisible beside geometry authored in whole pixels — and buys enough
     * margin that shallow contact reads as resting on a surface rather than
     * grazing it.
     *
     * <p>There is a ceiling on it, and it is close by. Every pixel of tolerance
     * also holds cloth that pixel further off the body it is cut to lie on, so
     * contact gets shallower: at half a pixel a leg swept through this model's
     * skirt drove it 0.197 rad against the 0.20 the sweep coverage asks for,
     * and the head fixture's strand stopped 0.04 px shy of the face it is
     * supposed to rest on. Deriving this from the collider's own thickness used
     * to put it as high as 0.675 px, which is where the visible hovering came
     * from.
     */
    private static final float TOLERANCE_PIXELS = 0.3F;
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
