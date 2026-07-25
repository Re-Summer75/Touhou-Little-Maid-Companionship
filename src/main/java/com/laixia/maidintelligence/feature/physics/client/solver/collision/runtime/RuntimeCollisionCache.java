package com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime;

import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.client.solver.spring.RuntimeCollisionFrames;
import org.joml.Vector3f;

/**
 * Solver-owned fixed cache of all per-node prepared collision geometry.
 */
public final class RuntimeCollisionCache {
    private final PreparedCollisionProxySet[] nodeSets;
    private final int[] preparedGenerations;
    private final CollisionScratch debugScratch = new CollisionScratch();
    private final boolean hasAnyProxies;
    private int generation;

    public RuntimeCollisionCache(
            PhysicsSolverLayout layout,
            RuntimeCollisionFrames frames
    ) {
        int count = layout.activeNodeCount();
        nodeSets = new PreparedCollisionProxySet[count];
        preparedGenerations = new int[count];
        CollisionScratch scratch = new CollisionScratch();
        boolean anyProxies = false;
        for (int nodeIndex = 0; nodeIndex < count; nodeIndex++) {
            PhysicsSolverLayout.Node node = layout.node(nodeIndex);
            CollisionProxySet baked = node.driven()
                    ? node.constraint().collisionProxies()
                    : CollisionProxySet.EMPTY;
            if (baked.proxyCount() == 0) {
                nodeSets[nodeIndex] = PreparedCollisionProxySet.EMPTY;
                continue;
            }
            PreparedCollisionProxySet prepared =
                    new PreparedCollisionProxySet(baked.proxyCount());
            nodeSets[nodeIndex] = prepared;
            anyProxies = true;
            for (int proxyIndex = 0;
                 proxyIndex < baked.proxyCount();
                 proxyIndex++) {
                CollisionProxy proxy = baked.proxy(proxyIndex);
                proxy.copyStaticShape(
                        frames.restOrientation(proxy.referenceNodeIndex()),
                        prepared.proxy(proxyIndex),
                        scratch
                );
                prepared.proxy(proxyIndex).setSource(proxy.source());
            }
        }
        hasAnyProxies = anyProxies;
    }

    public void beginFrame() {
        if (++generation != 0) {
            return;
        }
        for (int index = 0; index < preparedGenerations.length; index++) {
            preparedGenerations[index] = 0;
        }
        generation = 1;
    }

    public boolean hasProxies(int nodeIndex) {
        return valid(nodeIndex) && nodeSets[nodeIndex].proxyCount() > 0;
    }

    public boolean hasAnyProxies() {
        return hasAnyProxies;
    }

    public int preparedProxyCount(int nodeIndex) {
        return prepared(nodeIndex) ? nodeSets[nodeIndex].proxyCount() : 0;
    }

    public boolean copyPreparedCollisionProxy(
            int nodeIndex,
            int proxyIndex,
            Vector3f currentDirection,
            CollisionProxyDebugData output
    ) {
        int count = preparedProxyCount(nodeIndex);
        if (proxyIndex < 0 || proxyIndex >= count) {
            output.reset();
            return false;
        }
        nodeSets[nodeIndex].proxy(proxyIndex).copyDebugData(
                currentDirection,
                output,
                debugScratch
        );
        return true;
    }

    public PreparedCollisionProxySet prepareNode(
            int nodeIndex,
            Vector3f runtimePivotModel,
            float runtimeSegmentLength,
            Vector3f restDirection,
            CollisionScratch scratch,
            RuntimeCollisionFrames frames
    ) {
        if (!hasProxies(nodeIndex)) {
            return PreparedCollisionProxySet.EMPTY;
        }
        PreparedCollisionProxySet set = nodeSets[nodeIndex];
        if (preparedGenerations[nodeIndex] == generation) {
            return set;
        }
        for (int proxyIndex = 0;
             proxyIndex < set.proxyCount();
             proxyIndex++) {
            PreparedCollisionProxy proxy = set.proxy(proxyIndex);
            int referenceIndex = proxy.referenceNodeIndex();
            proxy.prepare(
                    runtimePivotModel,
                    frames.affineDelta(referenceIndex),
                    frames.normalTransform(referenceIndex),
                    frames.maxBasisScale(referenceIndex),
                    frames.maxBasisScale(nodeIndex),
                    runtimeSegmentLength
            );
            proxy.allowInitialRestPose(restDirection, scratch);
        }
        preparedGenerations[nodeIndex] = generation;
        return set;
    }

    public void reset() {
        for (int index = 0; index < preparedGenerations.length; index++) {
            preparedGenerations[index] = 0;
            PreparedCollisionProxySet set = nodeSets[index];
            for (int proxyIndex = 0;
                 proxyIndex < set.proxyCount();
                 proxyIndex++) {
                set.proxy(proxyIndex).resetRestAllowance();
            }
        }
        generation = 0;
    }

    private boolean valid(int nodeIndex) {
        return nodeIndex >= 0 && nodeIndex < nodeSets.length;
    }

    private boolean prepared(int nodeIndex) {
        return generation != 0
                && valid(nodeIndex)
                && preparedGenerations[nodeIndex] == generation;
    }
}
