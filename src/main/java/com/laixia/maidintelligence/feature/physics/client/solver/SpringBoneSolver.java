package com.laixia.maidintelligence.feature.physics.client.solver;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.CollisionProxyDebugData;
import com.laixia.maidintelligence.feature.physics.client.solver.spring.SpringBoneEngine;
import com.laixia.maidintelligence.feature.physics.client.solver.spring.SpringBoneMath;
import org.joml.Vector3f;

/**
 * Stable public facade for the modular allocation-free spring solver.
 */
public final class SpringBoneSolver {
    private final SpringBoneEngine engine;

    public SpringBoneSolver(PhysicsSolverLayout layout) {
        this(layout, true);
    }

    public SpringBoneSolver(
            PhysicsSolverLayout layout,
            boolean constraintsEnabled
    ) {
        engine = new SpringBoneEngine(layout, constraintsEnabled);
    }

    public void solve(
            Vector3f modelAcceleration,
            float yawRate,
            float dt,
            boolean paused
    ) {
        engine.solve(modelAcceleration, yawRate, dt, paused);
    }

    public void reset() {
        engine.reset();
    }

    /**
     * Removes the previous physics overlay before animation edits the bones.
     */
    public void restoreAnimationPose() {
        engine.restoreAnimationPose();
    }

    public PhysicsSolverLayout layout() {
        return engine.layout();
    }

    public int lastVisitedNodeCount() {
        return engine.lastVisitedNodeCount();
    }

    public float lastPeakDeflection() {
        return engine.lastPeakDeflection();
    }

    public int lastConstraintProjectionCount() {
        return engine.lastConstraintProjectionCount();
    }

    public int lastCollisionProjectionCount() {
        return engine.lastCollisionProjectionCount();
    }

    public boolean copyCurrentDirection(
            int drivenSlot,
            Vector3f output
    ) {
        return engine.copyCurrentDirection(drivenSlot, output);
    }

    public boolean copyPreviousDirection(
            int drivenSlot,
            Vector3f output
    ) {
        return engine.copyPreviousDirection(drivenSlot, output);
    }

    /**
     * Copies the latest conditioned animation acceleration in model space.
     */
    public boolean copyAnimationAcceleration(
            int drivenSlot,
            Vector3f output
    ) {
        return engine.copyAnimationAcceleration(drivenSlot, output);
    }

    /**
     * Copies an active node's rendered pivot in model-space blocks.
     */
    public boolean copyRuntimePivot(
            int activeNodeIndex,
            Vector3f output
    ) {
        return engine.copyRuntimePivot(activeNodeIndex, output);
    }

    /**
     * Copies a driven node's rendered tip in model-space blocks.
     */
    public boolean copyRuntimeTip(
            int activeNodeIndex,
            Vector3f output
    ) {
        return engine.copyRuntimeTip(activeNodeIndex, output);
    }

    /**
     * Returns the proxies prepared for this node during the latest solve.
     */
    public int preparedProxyCount(int activeNodeIndex) {
        return engine.preparedProxyCount(activeNodeIndex);
    }

    /**
     * Copies one latest-frame proxy and its current endpoint clearance.
     */
    public boolean copyPreparedCollisionProxy(
            int activeNodeIndex,
            int proxyIndex,
            CollisionProxyDebugData output
    ) {
        return engine.copyPreparedCollisionProxy(
                activeNodeIndex,
                proxyIndex,
                output
        );
    }

    public static float dragRetention(float drag, float dt) {
        return SpringBoneMath.dragRetention(drag, dt);
    }
}
