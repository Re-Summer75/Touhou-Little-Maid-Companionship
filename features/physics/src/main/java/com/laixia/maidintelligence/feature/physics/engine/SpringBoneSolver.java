package com.laixia.maidintelligence.feature.physics.engine;


import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import com.laixia.maidintelligence.feature.physics.engine.spring.SpringBoneEngine;
import com.laixia.maidintelligence.feature.physics.engine.spring.SpringBoneMath;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
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

    /**
     * Solves against an optional procedural model-space pose target. The pose
     * signal moves the animation rest direction and is not integrated as a
     * physical force.
     */
    public void solve(
            Vector3f modelAcceleration,
            Vector3f modelPoseDrive,
            float yawRate,
            float dt,
            boolean paused
    ) {
        engine.solve(
                modelAcceleration,
                modelPoseDrive,
                yawRate,
                dt,
                paused
        );
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

    public float projectionDamping(int drivenSlot) {
        return engine.projectionDamping(drivenSlot);
    }

    public int projectionReversals(int drivenSlot) {
        return engine.projectionReversals(drivenSlot);
    }

    public float contactSupport(int drivenSlot) {
        return engine.contactSupport(drivenSlot);
    }

    public float lastIntegratorStep(int drivenSlot) {
        return engine.lastIntegratorStep(drivenSlot);
    }

    public float lastProjectionStep(int drivenSlot) {
        return engine.lastProjectionStep(drivenSlot);
    }

    /** Bit 1: swing, bit 2: collider, bit 4: skirt branch tether. */
    public int lastProjectionSource(int drivenSlot) {
        return engine.lastProjectionSource(drivenSlot);
    }

    /** The pose the spring pulls this segment back towards. */
    public boolean copyRestDirection(int drivenSlot, Vector3f output) {
        return engine.copyRestDirection(drivenSlot, output);
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
