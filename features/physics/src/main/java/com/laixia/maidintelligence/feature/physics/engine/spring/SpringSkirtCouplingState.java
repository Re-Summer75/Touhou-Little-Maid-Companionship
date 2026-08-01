package com.laixia.maidintelligence.feature.physics.engine.spring;

import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;

/**
 * Per-step XPBD multipliers for skirt branch tethers.
 */
final class SpringSkirtCouplingState {
    final float[] lambdas;

    SpringSkirtCouplingState(PhysicsSolverLayout layout) {
        lambdas = new float[
                layout.skirtBranchConstraints().pairCount()
                ];
    }

    void beginFrame() {
        for (int index = 0; index < lambdas.length; index++) {
            lambdas[index] = 0.0F;
        }
    }

    void reset() {
        beginFrame();
    }
}
