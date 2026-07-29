package com.laixia.maidintelligence.feature.physics.client.solver;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import org.joml.Vector3f;

/**
 * Measures the thickness of a bone's mesh, for padding a contact by the amount
 * the sheet's surface leads the endpoint on its axis.
 *
 * <p>Only the thin direction is reported. A panel's width and length never
 * stand between the axis and a surface, and including them was what made five
 * successive attempts at this worse rather than better: the padding then held
 * the segment off colliders it was nowhere near, closed the gaps it needed to
 * sit between, and left it squeezed further inside something else.
 */
public final class MeshSheetExtent {
    private static final float EPSILON = 1.0E-6F;
    /**
     * Widest a direction may be, relative to the bone's own length, and still
     * count as a thickness.
     *
     * <p>A sheet is thin in one direction and broad in the others, and only the
     * thin one is worth padding. Judged against the bone rather than in absolute
     * pixels so that a large accessory and a hem are treated alike.
     */
    private static final float SHEET_RATIO = 0.5F;
    /**
     * Thickest a direction may be, in blocks, and still be padded for.
     *
     * <p>Cloth in these models is authored one or two pixels through, so four is
     * already generous. The ratio test alone is not enough: a short bone carrying
     * a broad panel has no long direction to be judged against, and its width
     * then passes as a thickness. Padding by a whole panel width is not a
     * near-miss either — it threw this model's skirt 1.18 rad off a swept leg
     * against the 1.0 the coverage allows.
     */
    private static final float MAX_THICKNESS = 4.0F / 16.0F;

    private MeshSheetExtent() {
    }

    /**
     * Half extents in the bone frame, zero on every direction that is not a
     * thickness. A bone with no geometry, or one too chunky to be a sheet,
     * reports zero throughout and leaves projection point-based as before.
     */
    public static Vector3f halfExtents(
            PhysicsBoneGeometry.Node node,
            Vector3f axis,
            Vector3f right,
            Vector3f outward
    ) {
        Vector3f half = new Vector3f();
        if (node == null || !node.hasGeometry()) {
            return half;
        }
        float along = span(node, axis);
        if (along <= EPSILON) {
            return half;
        }
        float limit = Math.min(along * SHEET_RATIO, MAX_THICKNESS);
        float acrossRight = span(node, right);
        float acrossOutward = span(node, outward);
        /*
         * Only the thinner cross direction is padded, and only when it is clearly
         * the thinner of the two. A sheet has one thin direction by definition;
         * treating both as thicknesses pads a panel by its own width, which is
         * not a near miss — it threw this model's skirt 1.18 rad off a swept leg
         * against the 0.95 the coverage allows. The panels here measure 0.5 to
         * 0.8 px through against widths of 3 to 7 px, so the two are far apart.
         */
        float thin = Math.min(acrossRight, acrossOutward);
        float broad = Math.max(acrossRight, acrossOutward);
        if (thin > limit || thin > broad * SHEET_RATIO) {
            return half;
        }
        if (acrossRight <= acrossOutward) {
            half.x = 0.5F * acrossRight;
        } else {
            half.z = 0.5F * acrossOutward;
        }
        return half;
    }

    /**
     * Extent of every cube corner projected onto one direction. Taken about the
     * axis line rather than a pivot: an accessory's authored pivot can sit well
     * away from its own mesh, and anchoring there would report that offset
     * instead of a thickness.
     */
    private static float span(PhysicsBoneGeometry.Node node, Vector3f axis) {
        if (axis == null || axis.lengthSquared() <= EPSILON) {
            return 0.0F;
        }
        Vector3f direction = new Vector3f(axis).normalize();
        Vector3f corner = new Vector3f();
        float nearest = Float.MAX_VALUE;
        float furthest = -Float.MAX_VALUE;
        for (PhysicsBoneGeometry.CubeBox box : node.cubeBoxes()) {
            for (int index = 0; index < 8; index++) {
                box.corner(index, corner);
                float projected = corner.dot(direction);
                nearest = Math.min(nearest, projected);
                furthest = Math.max(furthest, projected);
            }
        }
        return nearest > furthest ? 0.0F : furthest - nearest;
    }
}
