package com.laixia.maidintelligence.feature.physics.engine.spring;


/**
 * Mutable per-frame diagnostics kept separate from simulation state.
 */
final class SpringBoneMetrics {
    private int visitedNodeCount;
    private float peakDeflection;
    private int constraintProjectionCount;
    private int collisionProjectionCount;

    void beginFrame() {
        visitedNodeCount = 0;
        peakDeflection = 0.0F;
        constraintProjectionCount = 0;
        collisionProjectionCount = 0;
    }

    void visitNode() {
        visitedNodeCount++;
    }

    void recordDeflection(float angle) {
        peakDeflection = Math.max(peakDeflection, angle);
    }

    void recordConstraintProjection() {
        constraintProjectionCount++;
    }

    void recordCollisionProjection() {
        collisionProjectionCount++;
    }

    int visitedNodeCount() {
        return visitedNodeCount;
    }

    float peakDeflection() {
        return peakDeflection;
    }

    int constraintProjectionCount() {
        return constraintProjectionCount;
    }

    int collisionProjectionCount() {
        return collisionProjectionCount;
    }

    void reset() {
        beginFrame();
    }
}
