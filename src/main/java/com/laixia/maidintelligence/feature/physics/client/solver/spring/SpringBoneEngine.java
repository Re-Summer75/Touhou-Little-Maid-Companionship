package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.CollisionProxyDebugData;
import org.joml.Vector3f;

/**
 * Internal runtime API used by the stable {@code SpringBoneSolver} facade.
 */
public final class SpringBoneEngine {
    private final SpringBoneContext context;
    private final Vector3f debugDirection = new Vector3f();

    public SpringBoneEngine(
            PhysicsSolverLayout layout,
            boolean constraintsEnabled
    ) {
        context = new SpringBoneContext(layout, constraintsEnabled);
    }

    public void solve(
            Vector3f modelAcceleration,
            float yawRate,
            float dt,
            boolean paused
    ) {
        SpringBoneFrameRunner.solve(
                context,
                modelAcceleration,
                yawRate,
                dt,
                paused
        );
    }

    public void reset() {
        context.state.reset();
        context.animationMotion.reset();
        context.animationPose.reset();
        context.endpoints.reset();
        context.collisionFrames.reset();
        context.collisionCache.reset();
        context.metrics.reset();
    }

    public void restoreAnimationPose() {
        context.animationPose.restore();
    }

    public PhysicsSolverLayout layout() {
        return context.layout;
    }

    public int lastVisitedNodeCount() {
        return context.metrics.visitedNodeCount();
    }

    public float lastPeakDeflection() {
        return context.metrics.peakDeflection();
    }

    public int lastConstraintProjectionCount() {
        return context.metrics.constraintProjectionCount();
    }

    public int lastCollisionProjectionCount() {
        return context.metrics.collisionProjectionCount();
    }

    public boolean copyCurrentDirection(
            int drivenSlot,
            Vector3f output
    ) {
        return context.state.copyCurrentDirection(drivenSlot, output);
    }

    public boolean copyPreviousDirection(
            int drivenSlot,
            Vector3f output
    ) {
        return context.state.copyPreviousDirection(drivenSlot, output);
    }

    public boolean copyAnimationAcceleration(
            int drivenSlot,
            Vector3f output
    ) {
        return context.animationMotion.copyAcceleration(
                drivenSlot,
                output
        );
    }

    public boolean copyRuntimePivot(
            int activeNodeIndex,
            Vector3f output
    ) {
        return context.endpoints.copyPivot(activeNodeIndex, output);
    }

    public boolean copyRuntimeTip(
            int activeNodeIndex,
            Vector3f output
    ) {
        return context.endpoints.copyTip(activeNodeIndex, output);
    }

    public int preparedProxyCount(int activeNodeIndex) {
        return context.collisionCache.preparedProxyCount(activeNodeIndex);
    }

    public boolean copyPreparedCollisionProxy(
            int activeNodeIndex,
            int proxyIndex,
            CollisionProxyDebugData output
    ) {
        if (activeNodeIndex < 0
                || activeNodeIndex >= context.layout.activeNodeCount()) {
            output.reset();
            return false;
        }
        PhysicsSolverLayout.Node node = context.layout.node(activeNodeIndex);
        if (!node.driven()
                || !context.state.copyCurrentDirection(
                node.drivenSlot(),
                debugDirection
        )) {
            output.reset();
            return false;
        }
        return context.collisionCache.copyPreparedCollisionProxy(
                activeNodeIndex,
                proxyIndex,
                debugDirection,
                output
        );
    }
}
