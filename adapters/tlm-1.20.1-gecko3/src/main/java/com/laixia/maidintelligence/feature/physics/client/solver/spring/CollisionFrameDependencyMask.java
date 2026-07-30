package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;

/**
 * Marks only collision references and their transform ancestors.
 */
final class CollisionFrameDependencyMask {
    private CollisionFrameDependencyMask() {
    }

    static boolean[] create(PhysicsSolverLayout layout) {
        boolean[] required = new boolean[layout.activeNodeCount()];
        for (int nodeIndex = 0;
             nodeIndex < layout.activeNodeCount();
             nodeIndex++) {
            PhysicsSolverLayout.Node node = layout.node(nodeIndex);
            if (!node.driven()) {
                continue;
            }
            CollisionProxySet proxies =
                    node.constraint().collisionProxies();
            if (proxies.proxyCount() > 0) {
                markPath(layout, nodeIndex, required);
            }
            for (int proxyIndex = 0;
                 proxyIndex < proxies.proxyCount();
                 proxyIndex++) {
                CollisionProxy proxy = proxies.proxy(proxyIndex);
                markPath(layout, proxy.referenceNodeIndex(), required);
            }
        }
        return required;
    }

    private static void markPath(
            PhysicsSolverLayout layout,
            int nodeIndex,
            boolean[] required
    ) {
        int cursor = nodeIndex;
        while (cursor >= 0 && !required[cursor]) {
            required[cursor] = true;
            cursor = layout.node(cursor).parentIndex();
        }
    }
}
