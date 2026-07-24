package com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionScratch;
import org.joml.Vector3f;

/**
 * Fixed-size prepared proxy list for one active node.
 */
public final class PreparedCollisionProxySet {
    private static final int MAX_PASSES = 96;
    public static final PreparedCollisionProxySet EMPTY =
            new PreparedCollisionProxySet(0);

    private final PreparedCollisionProxy[] proxies;
    private final Vector3f passStart = new Vector3f();
    private final Vector3f pairBase = new Vector3f();
    private final Vector3f pairTangent = new Vector3f();

    public PreparedCollisionProxySet(int proxyCount) {
        proxies = new PreparedCollisionProxy[Math.max(0, proxyCount)];
        for (int index = 0; index < proxies.length; index++) {
            proxies[index] = new PreparedCollisionProxy();
        }
    }

    public int proxyCount() {
        return proxies.length;
    }

    public PreparedCollisionProxy proxy(int index) {
        return proxies[index];
    }

    public boolean project(
            Vector3f direction,
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
            passStart.set(direction);
            boolean passCorrected = false;
            for (PreparedCollisionProxy proxy : proxies) {
                passCorrected |= proxy.project(direction, scratch);
            }
            if (passCorrected && proxies.length == 2) {
                passCorrected |= PreparedPlanePairProjector.project(
                        proxies[0],
                        proxies[1],
                        direction,
                        scratch,
                        pairBase,
                        pairTangent
                );
            }
            corrected |= passCorrected;
            if (!passCorrected
                    || direction.distanceSquared(passStart) <= 1.0E-12F) {
                break;
            }
        }
        return corrected;
    }

    public float clearance(
            int proxyIndex,
            Vector3f direction,
            CollisionScratch scratch
    ) {
        return proxies[proxyIndex].clearance(direction, scratch);
    }
}
