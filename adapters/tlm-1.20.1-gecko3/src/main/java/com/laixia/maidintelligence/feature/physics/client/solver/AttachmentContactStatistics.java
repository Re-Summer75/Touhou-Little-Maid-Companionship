package com.laixia.maidintelligence.feature.physics.client.solver;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
