package com.laixia.maidintelligence.feature.physics.layout;


import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces a small deterministic pivot candidate set around the contact patch.
 */
final class VirtualPivotCandidates {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private VirtualPivotCandidates() {
    }

    static Vector3f[] generate(
            Vector3f authoredPivot,
            Vector3f fallbackPivot,
            BoneMeshMetrics child,
            AttachmentContactPatch patch
    ) {
        List<Vector3f> output = new ArrayList<>(6);
        add(output, patch.center());
        add(output, patch.dominantCenter());
        add(output, projectedAuthored(authoredPivot, child, patch));
        add(output, patch.nearestMidpoint());
        add(output, fallbackPivot);
        add(output, authoredPivot);
        return output.toArray(Vector3f[]::new);
    }

    private static Vector3f projectedAuthored(
            Vector3f authoredPivot,
            BoneMeshMetrics child,
            AttachmentContactPatch patch
    ) {
        Vector3f projected = new Vector3f(authoredPivot);
        Vector3f offset = projected.sub(patch.center());
        offset.fma(-offset.dot(patch.normal()), patch.normal());
        float maximum = Math.max(
                1.0F / PIXELS_PER_BLOCK,
                child.diagonal() * 0.35F
        );
        if (offset.lengthSquared() > maximum * maximum) {
            offset.normalize(maximum);
        }
        return offset.add(patch.center());
    }

    private static void add(List<Vector3f> output, Vector3f candidate) {
        float tolerance = 0.25F / PIXELS_PER_BLOCK;
        for (Vector3f existing : output) {
            if (existing.distanceSquared(candidate)
                    <= tolerance * tolerance) {
                return;
            }
        }
        output.add(new Vector3f(candidate));
    }
}
