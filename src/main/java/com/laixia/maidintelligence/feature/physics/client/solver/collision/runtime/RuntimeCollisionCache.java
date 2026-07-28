package com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime;

import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.client.solver.spring.RuntimeCollisionFrames;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Solver-owned fixed cache of all per-node prepared collision geometry.
 *
 * <p>Colliders are shared: a cube blocking forty strands is transformed once
 * per frame, and each segment only keeps its own pivot, lever arm and rest
 * overlap against it.
 */
public final class RuntimeCollisionCache {
    private final PreparedCollisionProxySet[] nodeSets;
    private final PreparedCollisionShape[] shapes;
    private final int[] preparedGenerations;
    private final CollisionScratch debugScratch = new CollisionScratch();
    private final boolean hasAnyProxies;
    private int generation;
    private int shapeGeneration;
    private float frameDeltaSeconds;

    public RuntimeCollisionCache(
            PhysicsSolverLayout layout,
            RuntimeCollisionFrames frames
    ) {
        int count = layout.activeNodeCount();
        nodeSets = new PreparedCollisionProxySet[count];
        preparedGenerations = new int[count];
        CollisionScratch scratch = new CollisionScratch();
        Map<PreparedCollisionShape.Key, PreparedCollisionShape> interned =
                new HashMap<>();
        List<PreparedCollisionShape> unique = new ArrayList<>();
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
                PreparedCollisionProxy target = prepared.proxy(proxyIndex);
                proxy.copyStaticShape(
                        frames.restOrientation(proxy.referenceNodeIndex()),
                        target,
                        scratch
                );
                PreparedCollisionShape shared = interned.putIfAbsent(
                        target.shape().key(),
                        target.shape()
                );
                if (shared == null) {
                    unique.add(target.shape());
                } else {
                    target.bind(shared);
                }
                target.setSource(proxy.source());
                /*
                 * Only a rigid reference has an authored pose to measure
                 * against. A driven one is wherever the solver last left it,
                 * so treating its overlap as authored would let two pieces of
                 * cloth licence each other deeper every frame.
                 */
                int reference = proxy.referenceNodeIndex();
                target.setAnimationPoseAllowanceEligible(
                        reference < 0
                                || (reference < count
                                && !layout.node(reference).driven())
                );
            }
            prepared.groupByReference();
        }
        shapes = unique.toArray(new PreparedCollisionShape[0]);
        hasAnyProxies = anyProxies;
    }

    public void beginFrame(float dt) {
        this.frameDeltaSeconds = Float.isFinite(dt)
                ? Math.max(0.0F, dt)
                : 0.0F;
        if (++generation != 0) {
            return;
        }
        for (int index = 0; index < preparedGenerations.length; index++) {
            preparedGenerations[index] = 0;
        }
        shapeGeneration = 0;
        generation = 1;
    }

    public boolean hasProxies(int nodeIndex) {
        return valid(nodeIndex) && nodeSets[nodeIndex].proxyCount() > 0;
    }

    public boolean hasAnyProxies() {
        return hasAnyProxies;
    }

    /**
     * Only the colliders in reach this frame carry a valid pose, so the debug
     * snapshot reports exactly the set the solver tested.
     */
    public int preparedProxyCount(int nodeIndex) {
        return prepared(nodeIndex) ? nodeSets[nodeIndex].liveCount() : 0;
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
        nodeSets[nodeIndex].liveProxy(proxyIndex).copyDebugData(
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
            float maximumSwing,
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
        prepareShapes(frames);
        set.bindFrame(
                runtimePivotModel,
                frames.maxBasisScale(nodeIndex),
                runtimeSegmentLength,
                maximumSwing,
                frames,
                restDirection,
                frameDeltaSeconds,
                scratch
        );
        preparedGenerations[nodeIndex] = generation;
        return set;
    }

    public void reset() {
        for (int index = 0; index < preparedGenerations.length; index++) {
            preparedGenerations[index] = 0;
            nodeSets[index].resetFrame();
        }
        generation = 0;
        shapeGeneration = 0;
        frameDeltaSeconds = 0.0F;
    }

    /**
     * Transforms every collider once for the whole model.
     *
     * <p>Up front rather than on first use, which was measured both ways. Posing
     * lazily skips the shapes behind a rejected cull bucket, which sounds like
     * most of them, but a collider is shared by every segment that can reach it,
     * so the stamp deciding whether to skip gets checked once per pairing —
     * thousands of times — to avoid posing a few hundred shapes. It also defeats
     * the motion bounds that let a segment reuse a measured gap, since a shape
     * nobody needed last frame has no one-frame travel to report. Together those
     * cost more than the posing they avoid: 305 against 288 microseconds here.
     */
    private void prepareShapes(RuntimeCollisionFrames frames) {
        if (shapeGeneration == generation) {
            return;
        }
        for (PreparedCollisionShape shape : shapes) {
            int reference = shape.referenceNodeIndex();
            shape.prepareNow(
                    frames.affineDelta(reference),
                    frames.normalTransform(reference),
                    frames.maxBasisScale(reference)
            );
        }
        shapeGeneration = generation;
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
