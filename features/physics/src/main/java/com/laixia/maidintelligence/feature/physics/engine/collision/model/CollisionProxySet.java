package com.laixia.maidintelligence.feature.physics.engine.collision.model;


import com.laixia.maidintelligence.feature.physics.engine.collision.model.projection.CollisionProjectionPrimitives;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immutable ordered proxy list associated with one driven bone.
 */
public final class CollisionProxySet {
    /*
     * Nearly tangent spherical caps can converge slowly. Normal frames exit
     * after one or two passes; this bound only protects deep intersections.
     */
    private static final int MAX_PASSES = 96;
    public static final CollisionProxySet EMPTY =
            new CollisionProxySet(new CollisionProxy[0]);

    private final CollisionProxy[] proxies;

    public CollisionProxySet(CollisionProxy... proxies) {
        this.proxies = proxies == null
                ? new CollisionProxy[0]
                : proxies.clone();
        for (CollisionProxy proxy : this.proxies) {
            if (proxy == null) {
                throw new IllegalArgumentException(
                        "Collision proxy cannot be null"
                );
            }
        }
    }

    public int proxyCount() {
        return proxies.length;
    }

    public CollisionProxy proxy(int index) {
        return proxies[index];
    }

    public boolean hasKind(CollisionProxyKind kind) {
        return firstIndex(kind) >= 0;
    }

    public int firstIndex(CollisionProxyKind kind) {
        for (int index = 0; index < proxies.length; index++) {
            if (proxies[index].kind() == kind) {
                return index;
            }
        }
        return -1;
    }

    public boolean referencesBefore(int nodeIndex) {
        for (CollisionProxy proxy : proxies) {
            int referenceIndex = proxy.referenceNodeIndex();
            if (referenceIndex >= nodeIndex) {
                return false;
            }
        }
        return true;
    }

    public boolean project(
            Vector3f direction,
            Quaternionf[] referenceOrientations,
            Quaternionf rootOrientation,
            CollisionScratch scratch
    ) {
        return project(
                direction,
                referenceOrientations,
                rootOrientation,
                scratch,
                MAX_PASSES
        );
    }

    public boolean project(
            Vector3f direction,
            Quaternionf[] referenceOrientations,
            Quaternionf rootOrientation,
            CollisionScratch scratch,
            int maxPasses
    ) {
        if (proxies.length == 0) {
            return false;
        }
        boolean corrected = false;
        int passes = proxies.length == 1
                ? 1
                : Math.max(1, Math.min(MAX_PASSES, maxPasses));
        for (int pass = 0; pass < passes; pass++) {
            scratch.passStart.set(direction);
            boolean passCorrected = false;
            for (CollisionProxy proxy : proxies) {
                Quaternionf reference = referenceOrientation(
                        proxy.referenceNodeIndex(),
                        referenceOrientations,
                        rootOrientation
                );
                passCorrected |= proxy.project(
                        direction,
                        reference,
                        scratch
                );
            }
            corrected |= passCorrected;
            if (!passCorrected || direction.distanceSquared(
                    scratch.passStart
            ) <= CollisionProjectionPrimitives.EPSILON
                    * CollisionProjectionPrimitives.EPSILON) {
                break;
            }
        }
        return corrected;
    }

    public float clearance(
            int proxyIndex,
            Vector3f direction,
            Quaternionf referenceOrientation,
            CollisionScratch scratch
    ) {
        return proxies[proxyIndex].clearance(
                direction,
                referenceOrientation,
                scratch
        );
    }

    private static Quaternionf referenceOrientation(
            int referenceIndex,
            Quaternionf[] orientations,
            Quaternionf rootOrientation
    ) {
        return referenceIndex < 0 || referenceIndex >= orientations.length
                ? rootOrientation
                : orientations[referenceIndex];
    }
}
