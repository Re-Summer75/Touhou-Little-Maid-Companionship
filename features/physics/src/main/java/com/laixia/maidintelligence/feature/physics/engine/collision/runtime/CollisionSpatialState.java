package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import org.joml.Vector3f;

/**
 * Preallocated grouping, reach-culling and live-slot selection state.
 */
final class CollisionSpatialState {
    int[] grouped = new int[0];
    int[] groupReference = new int[0];
    int[] groupStart = new int[0];
    Vector3f[] groupCenter = new Vector3f[0];
    float[] groupRadius = new float[0];
    float[] groupHitRadius = new float[0];
    int groupCount;

    final int[] active;
    final float[] activeSlack;
    final int[] previousActiveStamp;
    int bindingStamp = 1;
    int activeCount;
    boolean bound;
    final Vector3f transformedGroupCenter = new Vector3f();

    CollisionSpatialState(int proxyCount, int activeLimit) {
        active = new int[Math.min(proxyCount, activeLimit)];
        activeSlack = new float[active.length];
        previousActiveStamp = new int[proxyCount];
    }

    void allocateGroups(int proxyCount) {
        grouped = new int[proxyCount];
        groupReference = new int[proxyCount];
        groupStart = new int[proxyCount + 1];
        groupCenter = new Vector3f[proxyCount];
        groupRadius = new float[proxyCount];
        groupHitRadius = new float[proxyCount];
        groupCount = 0;
    }

    void beginBinding() {
        if (++bindingStamp == 0) {
            for (int index = 0; index < previousActiveStamp.length; index++) {
                previousActiveStamp[index] = 0;
            }
            bindingStamp = 1;
        }
        if (bound) {
            for (int index = 0; index < activeCount; index++) {
                previousActiveStamp[active[index]] = bindingStamp;
            }
        }
        activeCount = 0;
        bound = true;
    }

    boolean wasActive(int proxyIndex) {
        return proxyIndex >= 0
                && proxyIndex < previousActiveStamp.length
                && previousActiveStamp[proxyIndex] == bindingStamp;
    }

    void reset() {
        activeCount = 0;
        bound = false;
    }

    int liveCount(int proxyCount) {
        return bound ? activeCount : proxyCount;
    }

    int proxyIndex(int liveSlot) {
        return bound ? active[liveSlot] : liveSlot;
    }
}
