package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import org.joml.Vector3f;

/**
 * Builds compact reference-bone spatial groups once prepared geometry exists.
 */
final class PreparedCollisionSpatialGroups {
    private static final int MAX_GROUP_SIZE = 32;

    private PreparedCollisionSpatialGroups() {
    }

    /**
     * Buckets proxies by reference bone, then splits each bucket spatially.
     * This runs during model preparation rather than on the frame hot path.
     */
    static void build(
            PreparedCollisionProxy[] proxies,
            CollisionSpatialState spatial
    ) {
        spatial.allocateGroups(proxies.length);
        boolean[] bucketed = new boolean[proxies.length];
        int written = 0;
        for (int slot = 0; slot < proxies.length; slot++) {
            if (bucketed[slot]) {
                continue;
            }
            int reference = proxies[slot].referenceNodeIndex();
            int start = written;
            for (int scan = slot; scan < proxies.length; scan++) {
                if (proxies[scan].referenceNodeIndex() == reference) {
                    spatial.grouped[written++] = scan;
                    bucketed[scan] = true;
                }
            }
            subdivide(proxies, spatial, reference, start, written);
        }
        spatial.groupStart[spatial.groupCount] = written;
        measure(proxies, spatial);
    }

    /**
     * Registers {@code grouped[from, to)} as one group, or splits it and
     * recurses. The left half is always registered first so group starts stay
     * ascending, which is what lets {@code groupStart[group + 1]} serve as the
     * end of a group.
     */
    private static void subdivide(
            PreparedCollisionProxy[] proxies,
            CollisionSpatialState spatial,
            int reference,
            int from,
            int to
    ) {
        if (to - from <= MAX_GROUP_SIZE) {
            spatial.groupReference[spatial.groupCount] = reference;
            spatial.groupStart[spatial.groupCount] = from;
            spatial.groupCount++;
            return;
        }
        sortByLongestAxis(proxies, spatial, from, to);
        int middle = from + (to - from) / 2;
        subdivide(proxies, spatial, reference, from, middle);
        subdivide(proxies, spatial, reference, middle, to);
    }

    /**
     * Orders the slots along whichever axis the group is most spread out on.
     * Insertion sort because this runs once per model load over a handful of
     * cubes, not per frame.
     */
    private static void sortByLongestAxis(
            PreparedCollisionProxy[] proxies,
            CollisionSpatialState spatial,
            int from,
            int to
    ) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int cursor = from; cursor < to; cursor++) {
            Vector3f center =
                    proxies[spatial.grouped[cursor]].shape().restCenter();
            minX = Math.min(minX, center.x);
            minY = Math.min(minY, center.y);
            minZ = Math.min(minZ, center.z);
            maxX = Math.max(maxX, center.x);
            maxY = Math.max(maxY, center.y);
            maxZ = Math.max(maxZ, center.z);
        }
        float spanX = maxX - minX;
        float spanY = maxY - minY;
        float spanZ = maxZ - minZ;
        int axis = spanX >= spanY && spanX >= spanZ
                ? 0
                : (spanY >= spanZ ? 1 : 2);
        for (int cursor = from + 1; cursor < to; cursor++) {
            int slot = spatial.grouped[cursor];
            float key = axisValue(proxies, slot, axis);
            int scan = cursor - 1;
            while (scan >= from
                    && axisValue(
                            proxies, spatial.grouped[scan], axis
                    ) > key) {
                spatial.grouped[scan + 1] = spatial.grouped[scan];
                scan--;
            }
            spatial.grouped[scan + 1] = slot;
        }
    }

    private static float axisValue(
            PreparedCollisionProxy[] proxies,
            int slot,
            int axis
    ) {
        Vector3f center = proxies[slot].shape().restCenter();
        return switch (axis) {
            case 0 -> center.x;
            case 1 -> center.y;
            default -> center.z;
        };
    }

    /**
     * One group is rigid: all of its colliders ride the same bone, so a rest
     * bounding sphere stays valid under animation once its centre is carried
     * by that bone's transform. That turns a whole bone's worth of cubes into
     * a single test.
     */
    private static void measure(
            PreparedCollisionProxy[] proxies,
            CollisionSpatialState spatial
    ) {
        for (int group = 0; group < spatial.groupCount; group++) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            float hit = 0.0F;
            boolean bounded = true;
            for (int cursor = spatial.groupStart[group];
                 cursor < spatial.groupStart[group + 1];
                 cursor++) {
                PreparedCollisionProxy proxy =
                        proxies[spatial.grouped[cursor]];
                PreparedCollisionShape shape = proxy.shape();
                float extent = shape.restCullRadius();
                hit = Math.max(hit, proxy.restHitRadius());
                if (!Float.isFinite(extent)) {
                    bounded = false;
                    break;
                }
                Vector3f center = shape.restCenter();
                minX = Math.min(minX, center.x - extent);
                minY = Math.min(minY, center.y - extent);
                minZ = Math.min(minZ, center.z - extent);
                maxX = Math.max(maxX, center.x + extent);
                maxY = Math.max(maxY, center.y + extent);
                maxZ = Math.max(maxZ, center.z + extent);
            }
            if (!bounded) {
                spatial.groupCenter[group] = null;
                continue;
            }
            float sizeX = maxX - minX;
            float sizeY = maxY - minY;
            float sizeZ = maxZ - minZ;
            spatial.groupCenter[group] = new Vector3f(
                    minX + sizeX * 0.5F,
                    minY + sizeY * 0.5F,
                    minZ + sizeZ * 0.5F
            );
            spatial.groupRadius[group] = 0.5F * (float) Math.sqrt(
                    (double) sizeX * sizeX
                            + (double) sizeY * sizeY
                            + (double) sizeZ * sizeZ
            );
            spatial.groupHitRadius[group] = hit;
        }
    }
}
