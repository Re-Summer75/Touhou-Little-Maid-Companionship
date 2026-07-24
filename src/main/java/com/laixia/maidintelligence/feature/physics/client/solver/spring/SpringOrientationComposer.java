package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import org.joml.Quaternionf;

/**
 * Composes local Gecko ZYX rotations through the active hierarchy.
 */
final class SpringOrientationComposer {
    private SpringOrientationComposer() {
    }

    static void collectAnimationOrientations(
            PhysicsSolverLayout layout,
            SpringBoneState state,
            SpringBoneScratch scratch
    ) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            AnimatedGeoBone bone = node.bone();
            Quaternionf parent = node.parentIndex() < 0
                    ? scratch.rootOrientation
                    : state.animationOrientations[node.parentIndex()];
            compose(
                    parent,
                    bone.getRotationX(),
                    bone.getRotationY(),
                    bone.getRotationZ(),
                    state.animationOrientations[index],
                    scratch
            );
        }
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

    static void composeBoth(
            Quaternionf animationParent,
            Quaternionf renderedParent,
            float rotationX,
            float rotationY,
            float rotationZ,
            Quaternionf animationOutput,
            Quaternionf renderedOutput,
            SpringBoneScratch scratch
    ) {
        scratch.localRotation.identity().rotateZYX(
                rotationZ,
                rotationY,
                rotationX
        );
        animationOutput.set(animationParent).mul(scratch.localRotation);
        renderedOutput.set(renderedParent).mul(scratch.localRotation);
    }
}
