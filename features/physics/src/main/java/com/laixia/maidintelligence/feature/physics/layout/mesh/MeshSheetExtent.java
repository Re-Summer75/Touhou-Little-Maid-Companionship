package com.laixia.maidintelligence.feature.physics.layout.mesh;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import org.joml.Vector3f;

/**
 * Measures the thickness of a bone's mesh, for padding a contact by the amount
 * the sheet's surface leads the endpoint on its axis.
 *
 * <p>Only a thin direction is reported, and only ever one of them. A panel's
 * width and length never stand between the axis and a surface, and including
 * them was what made five successive attempts at this worse rather than better:
 * the padding then held the segment off colliders it was nowhere near, closed
 * the gaps it needed to sit between, and left it squeezed further inside
 * something else. A section that is thin all round, such as a cord or a pleat,
 * qualifies on either cross direction but is still padded on one, because
 * widening both makes neighbouring panels brace each other apart.
 */
public final class MeshSheetExtent {
    private static final float EPSILON = 1.0E-6F;
    /**
     * Widest a direction may be, relative to the bone's own length, and still
     * count as a thickness.
     *
     * <p>Judged against the bone as well as in absolute pixels, so that a stubby
     * bone carrying a broad panel does not have that width read as a thickness.
     */
    private static final float SHEET_RATIO = 0.5F;
    /**
     * Thickest a direction may be, in blocks, and still be padded for.
     *
     * <p>Set from what the cloth in these models actually measures rather than
     * from a round number. An apron is authored in panels of differing section:
     * winefox's FM series is flat at 1.0 to 1.8 px through, while the FFM2 and
     * FFM3 panels of the same garment run 2.2 to 2.8 px. A two-pixel limit
     * admitted the first group and rejected the second, leaving neighbouring
     * pieces of one apron treated differently — the rejected ones padded by
     * nothing and sinking 2.0 and 2.6 px into the legs. Three covers the whole
     * garment and cut this model's contact count from 1228 to 742, cloth stopping
     * earlier instead of pressing on through.
     *
     * <p>An absolute limit is needed in its own right, not just as a cap on the
     * ratio: a short bone carrying a broad panel has no long direction to be
     * judged against, and its width would otherwise pass as a thickness. Padding
     * by a whole panel width threw this model's skirt 1.18 rad off a swept leg
     * against the 1.0 the coverage allows.
     */
    private static final float MAX_THICKNESS = 3.0F / 16.0F;

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
         * Judged on the thinner cross direction against an absolute limit, with
         * no comparison between the two. Requiring one to be clearly flatter than
         * the other assumes cloth is always a broad panel, and much of it is not:
         * a cord, a sash or a pleat is roughly square in section, and on winefox
         * the FFM2_1, BL2, FR2 and FFM3_1 segments measure 2.4 by 2.9 px and 2.9
         * by 3.1 px through. Those failed a ratio test, were padded by nothing at
         * all, and sank about 2 px into the legs. The limit is what keeps a broad
         * panel's width from passing as a thickness.
         */
        float thin = Math.min(acrossRight, acrossOutward);
        if (thin > limit) {
            return half;
        }
        /*
         * One direction only, even for a section that is thin all round. Padding
         * both was tried and cannot work here: neighbouring panels of a skirt sit
         * within a thickness of each other, so widening each of them in every
         * cross direction makes them hold each other apart, and a leg sweeping
         * through then finds a mutually braced wall it cannot move — 0.0999 rad
         * against the 0.1 the test demands, from cloth that had been swinging
         * freely. The thinner direction is the one an axis most leads its surface
         * on, so it is the one worth having.
         */
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
