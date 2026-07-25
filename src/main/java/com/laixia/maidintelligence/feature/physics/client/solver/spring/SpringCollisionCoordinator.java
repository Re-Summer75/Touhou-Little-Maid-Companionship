package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.PreparedCollisionProxySet;

/**
 * Coordinates the frame prepass and one driven node's prepared proxies.
 */
final class SpringCollisionCoordinator {
    private SpringCollisionCoordinator() {
    }

    static void beginFrame(SpringBoneContext context) {
        if (!context.constraintsEnabled
                || !context.collisionCache.hasAnyProxies()) {
            return;
        }
        context.collisionFrames.prepare();
        context.collisionCache.beginFrame();
    }

    static PreparedCollisionProxySet prepareNode(
            SpringBoneContext context,
            int nodeIndex,
            float rotationX,
            float rotationY,
            float rotationZ
    ) {
        SpringBoneScratch scratch = context.scratch;
        scratch.runtimeSafetyScale =
                context.endpoints.resolveAnimatedScaleBound(nodeIndex);
        if (!context.constraintsEnabled
                || !context.collisionCache.hasProxies(nodeIndex)) {
            return PreparedCollisionProxySet.EMPTY;
        }
        scratch.animationRotation.identity().rotateZYX(
                rotationZ,
                rotationY,
                rotationX
        );
        context.endpoints.resolveAnimatedPivot(
                nodeIndex,
                scratch.animationRotation,
                scratch.pivotScratch
        );
        float runtimeSegmentLength =
                context.endpoints.resolveAnimatedSegmentLength(
                        nodeIndex,
                        scratch.pivotScratch,
                        scratch.endpointScratch
                );
        return context.collisionCache.prepareNode(
                nodeIndex,
                scratch.pivotScratch,
                runtimeSegmentLength,
                scratch.restDirection,
                scratch.collision,
                context.collisionFrames
        );
    }
}
