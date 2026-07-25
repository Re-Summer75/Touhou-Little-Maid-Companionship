package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Captures a pure-animation affine hierarchy before physics writes bones.
 */
final class AnimationPoseFrame {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private final PhysicsSolverLayout layout;
    private final Matrix4f identityTransform = new Matrix4f();
    private final Quaternionf identityOrientation = new Quaternionf();
    private final Quaternionf localRotation = new Quaternionf();
    private final Matrix4f[] transforms;
    private final Vector3f[] localPivots;
    private final Vector3f[] localTips;

    AnimationPoseFrame(PhysicsSolverLayout layout) {
        this.layout = layout;
        int count = layout.activeNodeCount();
        transforms = new Matrix4f[count];
        localPivots = new Vector3f[count];
        localTips = new Vector3f[count];
        Vector3f axis = new Vector3f();
        for (int index = 0; index < count; index++) {
            transforms[index] = new Matrix4f();
            PhysicsSolverLayout.Node node = layout.node(index);
            if (!node.driven()) {
                continue;
            }
            Vector3f pivot = node.kinematics().effectivePivot();
            localPivots[index] = pivot;
            localTips[index] = new Vector3f(pivot).fma(
                    node.kinematics().segmentLength() / PIXELS_PER_BLOCK,
                    node.axisInto(axis)
            );
        }
    }

    void prepare(SpringBoneState state) {
        identityTransform.identity();
        identityOrientation.identity();
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            compose(index, state);
        }
    }

    void endpointsInto(
            int nodeIndex,
            Vector3f pivotOutput,
            Vector3f tipOutput
    ) {
        Matrix4f transform = transforms[nodeIndex];
        transform.transformPosition(
                localPivots[nodeIndex],
                pivotOutput
        );
        transform.transformPosition(localTips[nodeIndex], tipOutput);
    }

    Quaternionf mountOrientation(
            int nodeIndex,
            SpringBoneState state
    ) {
        int parentIndex = layout.node(nodeIndex).parentIndex();
        return parentIndex < 0
                ? identityOrientation
                : state.animationOrientations[parentIndex];
    }

    void reset() {
        identityTransform.identity();
        identityOrientation.identity();
        for (Matrix4f transform : transforms) {
            transform.identity();
        }
    }

    private void compose(int index, SpringBoneState state) {
        PhysicsSolverLayout.Node node = layout.node(index);
        AnimatedGeoBone bone = node.bone();
        Quaternionf parentOrientation = node.parentIndex() < 0
                ? identityOrientation
                : state.animationOrientations[node.parentIndex()];
        localRotation.identity().rotateZYX(
                bone.getRotationZ(),
                bone.getRotationY(),
                bone.getRotationX()
        );
        state.animationOrientations[index]
                .set(parentOrientation)
                .mul(localRotation);

        Matrix4f parentTransform = node.parentIndex() < 0
                ? identityTransform
                : transforms[node.parentIndex()];
        float pivotX = bone.getPivotX() / PIXELS_PER_BLOCK;
        float pivotY = bone.getPivotY() / PIXELS_PER_BLOCK;
        float pivotZ = bone.getPivotZ() / PIXELS_PER_BLOCK;
        transforms[index].set(parentTransform)
                .translate(
                        -bone.getPositionX() / PIXELS_PER_BLOCK,
                        bone.getPositionY() / PIXELS_PER_BLOCK,
                        bone.getPositionZ() / PIXELS_PER_BLOCK
                )
                .translate(pivotX, pivotY, pivotZ)
                .rotate(localRotation)
                .scale(
                        bone.getScaleX(),
                        bone.getScaleY(),
                        bone.getScaleZ()
                )
                .translate(-pivotX, -pivotY, -pivotZ);
    }
}
