package com.laixia.maidintelligence.feature.physics.layout;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;

/**
 * Selects a dominant connected cube cluster for attachment-frame inference.
 * The full mesh remains authoritative for the safety lever arm.
 */
record BoneMeshPartition(
        BoneMeshMetrics full,
        BoneMeshMetrics frame,
        boolean distributedWithoutDominantCluster
) {
    private static final float JOIN_MARGIN = 0.08F;
    private static final float MIN_WEIGHT_SHARE = 0.58F;

    static BoneMeshPartition measure(BoneModelSnapshot.Mesh mesh) {
        BoneMeshMetrics full = BoneMeshMetrics.measure(mesh);
        int count = mesh.getCubeCount();
        if (count < 2 || full.empty()) {
            return new BoneMeshPartition(full, full, false);
        }
        int[] parent = new int[count];
        OrientedCubeBounds[] bounds = new OrientedCubeBounds[count];
        for (int cube = 0; cube < count; cube++) {
            parent[cube] = cube;
            bounds[cube] = OrientedCubeBounds.from(mesh, cube);
        }
        for (int first = 0; first < count; first++) {
            for (int second = first + 1; second < count; second++) {
                if (bounds[first].hasVisibleSurface()
                        && bounds[second].hasVisibleSurface()
                        && bounds[first].touches(
                        bounds[second],
                        JOIN_MARGIN
                )) {
                    union(parent, first, second);
                }
            }
        }
        int componentCount = componentCount(parent);
        BoneMeshMetrics dominant = dominant(mesh, parent, full);
        return new BoneMeshPartition(
                full,
                dominant == null ? full : dominant,
                componentCount > 1 && dominant == null
        );
    }

    boolean usesDominantCluster() {
        return frame != full;
    }

    private static int componentCount(int[] parent) {
        int count = 0;
        for (int cube = 0; cube < parent.length; cube++) {
            if (find(parent, cube) == cube) {
                count++;
            }
        }
        return count;
    }

    private static BoneMeshMetrics dominant(
            BoneModelSnapshot.Mesh mesh,
            int[] parent,
            BoneMeshMetrics full
    ) {
        BoneMeshMetrics best = null;
        for (int root = 0; root < parent.length; root++) {
            if (find(parent, root) != root) {
                continue;
            }
            int memberCount = 0;
            for (int cube = 0; cube < parent.length; cube++) {
                if (find(parent, cube) == root) {
                    memberCount++;
                }
            }
            if (memberCount == parent.length) {
                return null;
            }
            int[] members = new int[memberCount];
            int cursor = 0;
            for (int cube = 0; cube < parent.length; cube++) {
                if (find(parent, cube) == root) {
                    members[cursor++] = cube;
                }
            }
            BoneMeshMetrics candidate =
                    BoneMeshMetrics.measure(mesh, members);
            if (best == null
                    || candidate.totalWeight() > best.totalWeight()) {
                best = candidate;
            }
        }
        if (best == null
                || best.totalWeight()
                < full.totalWeight() * MIN_WEIGHT_SHARE) {
            return null;
        }
        return best;
    }

    private static int find(int[] parent, int value) {
        int root = value;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[value] != value) {
            int next = parent[value];
            parent[value] = root;
            value = next;
        }
        return root;
    }

    private static void union(int[] parent, int first, int second) {
        int firstRoot = find(parent, first);
        int secondRoot = find(parent, second);
        if (firstRoot != secondRoot) {
            parent[secondRoot] = firstRoot;
        }
    }
}
