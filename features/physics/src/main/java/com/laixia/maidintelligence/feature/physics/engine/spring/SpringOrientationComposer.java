package com.laixia.maidintelligence.feature.physics.engine.spring;


import org.joml.Quaternionf;

/**
 * Composes local Gecko ZYX rotations through the active hierarchy.
 */
final class SpringOrientationComposer {
    private SpringOrientationComposer() {
    }

    static void compose(
            Quaternionf parent,
            float rotationX,
            float rotationY,
            float rotationZ,
            Quaternionf output,
            SpringBoneScratch scratch
    ) {
        scratch.localRotation.identity().rotateZYX(
                rotationZ,
                rotationY,
                rotationX
        );
        output.set(parent).mul(scratch.localRotation);
    }

}
