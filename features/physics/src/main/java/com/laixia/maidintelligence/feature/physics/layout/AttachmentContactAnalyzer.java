package com.laixia.maidintelligence.feature.physics.layout;


import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts a distance-weighted contact patch between child and support OBBs.
 */
final class AttachmentContactAnalyzer {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float EPSILON = 1.0E-6F;

    private AttachmentContactAnalyzer() {
    }

    static AttachmentContactPatch analyze(
            BoneMeshMetrics child,
            BoneMeshMetrics support
    ) {
        MeshSurfaceSamples surface = MeshSurfaceSamples.sample(child);
        if (surface.points().length == 0 || support.empty()) {
            return AttachmentContactPatch.none(surface);
        }
        MeshDistanceField.Nearest[] nearest =
                new MeshDistanceField.Nearest[surface.points().length];
        float minimum = Float.POSITIVE_INFINITY;
        int nearestIndex = 0;
        for (int index = 0; index < surface.points().length; index++) {
            nearest[index] = MeshDistanceField.nearestSurface(
                    support,
                    surface.points()[index]
            );
            if (nearest[index].surfaceDistance() < minimum) {
                minimum = nearest[index].surfaceDistance();
                nearestIndex = index;
            }
        }
        float extra = clamp(
                child.diagonal() * 0.08F,
                0.5F / PIXELS_PER_BLOCK,
                2.0F / PIXELS_PER_BLOCK
        );
        float bandwidth = minimum + extra;
        float sigma = Math.max(0.5F / PIXELS_PER_BLOCK, extra * 0.65F);
        List<AttachmentContactPatch.Sample> selected = new ArrayList<>();
        for (int index = 0; index < nearest.length; index++) {
            float distance = nearest[index].surfaceDistance();
            if (distance > bandwidth) {
                continue;
            }
            float normalized = (distance - minimum) / sigma;
            float weight = surface.weights()[index]
                    * (float) Math.exp(-0.5F * normalized * normalized);
            Vector3f childPoint = surface.points()[index];
            Vector3f supportPoint = nearest[index].point();
            selected.add(new AttachmentContactPatch.Sample(
                    childPoint,
                    supportPoint,
                    AttachmentContactStatistics.midpoint(
                            childPoint,
                            supportPoint
                    ),
                    nearest[index].signedDistance(),
                    weight,
                    surface.boxIndices()[index]
            ));
        }
        selected = AttachmentContactStatistics.rejectOutliers(
                selected,
                child.diagonal()
        );
        if (selected.isEmpty()) {
            return AttachmentContactPatch.none(surface);
        }
        Vector3f center = AttachmentContactStatistics.weightedCenter(
                selected
        );
        AttachmentContactStatistics.Cluster cluster =
                AttachmentContactStatistics.dominantCluster(
                        selected,
                        child.diagonal()
                );
        Vector3f normal = AttachmentContactStatistics.contactNormal(
                selected,
                child.centroid(),
                support.centroid()
        );
        float selectedWeight =
                AttachmentContactStatistics.totalWeight(selected);
        float mass = clamp(
                selectedWeight / Math.max(
                        EPSILON,
                        surface.totalWeight() * 0.08F
                ),
                0.0F,
                1.0F
        );
        float gapScale = Math.max(
                2.0F / PIXELS_PER_BLOCK,
                child.diagonal() * 0.08F
        );
        float gap = (float) Math.exp(
                -Math.max(0.0F, minimum - 0.5F / PIXELS_PER_BLOCK)
                        / gapScale
        );
        float count = Math.min(1.0F, selected.size() / 4.0F);
        float concentration =
                AttachmentContactStatistics.axialConcentration(
                        selected,
                        child.principalAxis()
                );
        float confidence = clamp(
                gap * (
                        mass * 0.45F
                                + cluster.dominance() * 0.35F
                                + count * 0.20F
                ) * (0.25F + concentration * 0.75F),
                0.0F,
                1.0F
        );
        Vector3f nearestMidpoint = AttachmentContactStatistics.midpoint(
                surface.points()[nearestIndex],
                nearest[nearestIndex].point()
        );
        return new AttachmentContactPatch(
                center,
                cluster.center(),
                normal,
                nearestMidpoint,
                selected.toArray(AttachmentContactPatch.Sample[]::new),
                surface,
                confidence,
                minimum,
                bandwidth
        );
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
