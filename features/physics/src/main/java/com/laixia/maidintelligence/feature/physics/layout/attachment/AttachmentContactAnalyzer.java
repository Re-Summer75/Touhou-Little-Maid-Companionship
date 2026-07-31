package com.laixia.maidintelligence.feature.physics.layout.attachment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.joml.Vector3f;

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

/**
 * Robust child/support contact evidence baked during model discovery.
 */
record AttachmentContactPatch(
        Vector3f center,
        Vector3f dominantCenter,
        Vector3f normal,
        Vector3f nearestMidpoint,
        Sample[] samples,
        MeshSurfaceSamples childSurface,
        float confidence,
        float minimumDistance,
        float bandwidth
) {
    AttachmentContactPatch {
        center = new Vector3f(center);
        dominantCenter = new Vector3f(dominantCenter);
        normal = new Vector3f(normal);
        nearestMidpoint = new Vector3f(nearestMidpoint);
        samples = samples.clone();
    }

    static AttachmentContactPatch none(MeshSurfaceSamples surface) {
        return new AttachmentContactPatch(
                new Vector3f(),
                new Vector3f(),
                new Vector3f(),
                new Vector3f(),
                new Sample[0],
                surface,
                0.0F,
                Float.POSITIVE_INFINITY,
                0.0F
        );
    }

    boolean present() {
        return samples.length > 0;
    }

    record Sample(
            Vector3f childPoint,
            Vector3f supportPoint,
            Vector3f midpoint,
            float initialSignedDistance,
            float weight,
            int childBoxIndex
    ) {
        Sample {
            childPoint = new Vector3f(childPoint);
            supportPoint = new Vector3f(supportPoint);
            midpoint = new Vector3f(midpoint);
        }
    }
}

/**
 * Robust statistics and spatial clustering for a sampled contact patch.
 */
final class AttachmentContactStatistics {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float EPSILON = 1.0E-6F;

    private AttachmentContactStatistics() {
    }

    static List<AttachmentContactPatch.Sample> rejectOutliers(
            List<AttachmentContactPatch.Sample> samples,
            float scale
    ) {
        if (samples.size() < 4) {
            return samples;
        }
        Vector3f center = weightedCenter(samples);
        float[] residuals = new float[samples.size()];
        for (int index = 0; index < samples.size(); index++) {
            residuals[index] = samples.get(index).midpoint().distance(center);
        }
        float[] sorted = residuals.clone();
        Arrays.sort(sorted);
        float median = sorted[sorted.length / 2];
        float limit = Math.max(
                1.0F / PIXELS_PER_BLOCK,
                Math.min(scale * 0.45F, median * 3.0F)
        );
        List<AttachmentContactPatch.Sample> filtered = new ArrayList<>();
        for (int index = 0; index < samples.size(); index++) {
            if (residuals[index] <= limit) {
                filtered.add(samples.get(index));
            }
        }
        return filtered.isEmpty() ? samples : filtered;
    }

    static Cluster dominantCluster(
            List<AttachmentContactPatch.Sample> samples,
            float scale
    ) {
        int[] parent = new int[samples.size()];
        float radius = Math.max(1.0F / PIXELS_PER_BLOCK, scale * 0.30F);
        for (int index = 0; index < parent.length; index++) {
            parent[index] = index;
        }
        for (int first = 0; first < samples.size(); first++) {
            for (int second = first + 1; second < samples.size(); second++) {
                if (samples.get(first).midpoint().distanceSquared(
                        samples.get(second).midpoint()
                ) <= radius * radius) {
                    parent[find(parent, second)] = find(parent, first);
                }
            }
        }
        float total = totalWeight(samples);
        float bestWeight = -1.0F;
        Vector3f bestCenter = weightedCenter(samples);
        for (int root = 0; root < parent.length; root++) {
            if (find(parent, root) != root) {
                continue;
            }
            float weight = 0.0F;
            Vector3f center = new Vector3f();
            for (int index = 0; index < samples.size(); index++) {
                if (find(parent, index) == root) {
                    float sampleWeight = samples.get(index).weight();
                    center.fma(sampleWeight, samples.get(index).midpoint());
                    weight += sampleWeight;
                }
            }
            if (weight > bestWeight) {
                bestWeight = weight;
                bestCenter = center.div(Math.max(EPSILON, weight));
            }
        }
        return new Cluster(
                bestCenter,
                bestWeight / Math.max(EPSILON, total)
        );
    }

    static float axialConcentration(
            List<AttachmentContactPatch.Sample> samples,
            MeshPrincipalAxis principalAxis
    ) {
        Vector3f axis = new Vector3f(principalAxis.endB())
                .sub(principalAxis.endA());
        float length = axis.length();
        if (length < EPSILON) {
            return 1.0F;
        }
        axis.mul(1.0F / length);
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (AttachmentContactPatch.Sample sample : samples) {
            float projection = sample.midpoint().dot(axis);
            minimum = Math.min(minimum, projection);
            maximum = Math.max(maximum, projection);
        }
        float ratio = Math.max(0.0F, maximum - minimum) / length;
        float ambiguity = Math.max(0.0F, ratio - 0.15F) / 0.45F;
        return 1.0F - Math.max(0.0F, Math.min(1.0F, ambiguity));
    }

    static Vector3f contactNormal(
            List<AttachmentContactPatch.Sample> samples,
            Vector3f childCenter,
            Vector3f supportCenter
    ) {
        Vector3f normal = new Vector3f();
        for (AttachmentContactPatch.Sample sample : samples) {
            normal.fma(
                    sample.weight(),
                    new Vector3f(sample.childPoint())
                            .sub(sample.supportPoint())
            );
        }
        if (normal.lengthSquared() < EPSILON) {
            normal.set(childCenter).sub(supportCenter);
        }
        return normal.lengthSquared() < EPSILON
                ? new Vector3f(0.0F, 1.0F, 0.0F)
                : normal.normalize();
    }

    static Vector3f weightedCenter(
            List<AttachmentContactPatch.Sample> samples
    ) {
        Vector3f center = new Vector3f();
        float weight = 0.0F;
        for (AttachmentContactPatch.Sample sample : samples) {
            center.fma(sample.weight(), sample.midpoint());
            weight += sample.weight();
        }
        return center.div(Math.max(EPSILON, weight));
    }

    static float totalWeight(List<AttachmentContactPatch.Sample> samples) {
        float total = 0.0F;
        for (AttachmentContactPatch.Sample sample : samples) {
            total += sample.weight();
        }
        return total;
    }

    static Vector3f midpoint(Vector3f first, Vector3f second) {
        return new Vector3f(first).add(second).mul(0.5F);
    }

    private static int find(int[] parent, int value) {
        while (parent[value] != value) {
            parent[value] = parent[parent[value]];
            value = parent[value];
        }
        return value;
    }

    record Cluster(Vector3f center, float dominance) {
    }
}
