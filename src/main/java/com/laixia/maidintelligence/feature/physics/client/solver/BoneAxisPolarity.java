package com.laixia.maidintelligence.feature.physics.client.solver;

import org.joml.Vector3f;

/**
 * Keeps terminal and single-bone tips on the visible-mass side of the pivot.
 */
final class BoneAxisPolarity {
    private static final float MIN_OFFSET = 0.5F / 16.0F;
    private static final float MIN_REVERSED_PROJECTION = 0.25F / 16.0F;

    private BoneAxisPolarity() {
    }

    static boolean alignWithVisibleMass(
            Vector3f axis,
            BoneMeshMetrics mesh,
            Vector3f effectivePivot
    ) {
        Vector3f towardMass = new Vector3f(mesh.centroid())
                .sub(effectivePivot);
        if (towardMass.lengthSquared() > MIN_OFFSET * MIN_OFFSET
                && axis.dot(towardMass) < -MIN_REVERSED_PROJECTION) {
            axis.negate();
            return true;
        }
        return false;
    }
}
