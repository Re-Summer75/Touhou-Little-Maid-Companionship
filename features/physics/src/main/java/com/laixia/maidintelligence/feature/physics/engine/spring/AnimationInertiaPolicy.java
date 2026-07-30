package com.laixia.maidintelligence.feature.physics.engine.spring;


import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;

/**
 * Maps authored animation kinematics to a bounded secondary-motion signal.
 */
final class AnimationInertiaPolicy {
    private static final float SCALE_ACCELERATION_SHARE = 0.25F;

    private AnimationInertiaPolicy() {
    }

    static float linearGain(PhysicsSolverLayout.Node node) {
        return typeGain(node.decision().type())
                * node.constraint().rotationInertiaScale();
    }

    static float angularGain(PhysicsSolverLayout.Node node) {
        float inertia = node.constraint().rotationInertiaScale();
        /*
         * Reference transport already preserves the inertia fraction itself.
         * Only inject the complementary nonlinear angular acceleration so the
         * old displacement lag is not counted a second time.
         */
        return typeGain(node.decision().type())
                * inertia
                * (1.0F - inertia);
    }

    static float scaleGain(PhysicsSolverLayout.Node node) {
        return linearGain(node) * SCALE_ACCELERATION_SHARE;
    }

    private static float typeGain(
            PhysicsBoneSelectionPlan.PartType type
    ) {
        return switch (type) {
            case HEAD_SHELL -> 0.20F;
            case HAIR -> 1.00F;
            case TAIL -> 1.10F;
            case EAR -> 0.65F;
            case SKIRT -> 0.90F;
            case RIBBON -> 1.10F;
            case CAPE -> 0.85F;
            case WING -> 0.70F;
            case GENERIC -> 0.80F;
        };
    }
}
