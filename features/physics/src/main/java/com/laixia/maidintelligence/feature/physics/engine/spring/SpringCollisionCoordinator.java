package com.laixia.maidintelligence.feature.physics.engine.spring;


import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProjection;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.PreparedCollisionProxySet;

/**
 * Coordinates the frame prepass and one driven node's prepared proxies.
 */
final class SpringCollisionCoordinator {
    private SpringCollisionCoordinator() {
    }

    static void beginFrame(SpringBoneContext context, float dt) {
        if (!context.constraintsEnabled
                || !context.collisionCache.hasAnyProxies()) {
            return;
        }
        context.collisionFrames.prepare(context.endpoints);
        context.collisionCache.beginFrame(dt);
    }

    static CollisionProjection prepareNode(
            SpringBoneContext context,
            int nodeIndex,
            float rotationX,
            float rotationY,
            float rotationZ
    ) {
        SpringBoneScratch scratch = context.scratch;
        scratch.runtimeSafetyScale =
                context.endpoints.resolveAnimatedScaleBound(nodeIndex);
        scratch.runtimeSegmentLength = 0.0F;
        if (!context.constraintsEnabled) {
            return PreparedCollisionProxySet.EMPTY;
        }
        boolean hasProxies = context.collisionCache.hasProxies(nodeIndex);
        boolean needsSkirtCoupling = context.layout
                .skirtBranchConstraints()
                .pairForFollowerNode(nodeIndex) >= 0;
        if (!hasProxies && !needsSkirtCoupling) {
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
        scratch.runtimeSegmentLength =
                context.endpoints.resolveAnimatedSegmentLength(
                        nodeIndex,
                        scratch.pivotScratch,
                        scratch.endpointScratch
                );
        if (!hasProxies) {
            return PreparedCollisionProxySet.EMPTY;
        }
        return context.collisionCache.prepareNode(
                nodeIndex,
                scratch.pivotScratch,
                scratch.runtimeSegmentLength,
                scratch.authoredRestDirection,
                SpringConstraintProjector.maximumSwing(
                        context.layout.node(nodeIndex),
                        scratch.runtimeSafetyScale
                ),
                scratch.collision,
                context.collisionFrames
        );
    }
}
