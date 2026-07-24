package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.snapshot.BoneSnapshot;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Captures the complete animation pose before any driven bone is modified.
 */
public final class RuntimeCollisionFrames {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private final PhysicsSolverLayout layout;
    private final Matrix4f identity = new Matrix4f();
    private final Matrix3f identityNormal = new Matrix3f();
    private final Quaternionf identityOrientation = new Quaternionf();
    private final Quaternionf localRotation = new Quaternionf();
    private final Matrix4f[] bindInverses;
    private final Matrix4f[] animationTransforms;
    private final Matrix4f[] affineDeltas;
    private final Matrix3f[] normalTransforms;
    private final Quaternionf[] restOrientations;
    private final float[] maxBasisScales;
    private final boolean[] required;

    public RuntimeCollisionFrames(PhysicsSolverLayout layout) {
        this.layout = layout;
        int count = layout.activeNodeCount();
        bindInverses = new Matrix4f[count];
        animationTransforms = new Matrix4f[count];
        affineDeltas = new Matrix4f[count];
        normalTransforms = new Matrix3f[count];
        restOrientations = new Quaternionf[count];
        maxBasisScales = new float[count];
        required = CollisionFrameDependencyMask.create(layout);
        Matrix4f[] bindTransforms = new Matrix4f[count];
        for (int index = 0; index < count; index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            AnimatedGeoBone bone = node.bone();
            BoneSnapshot initial = bone.getInitialSnapshot();
            Quaternionf parentOrientation = node.parentIndex() < 0
                    ? identityOrientation
                    : restOrientations[node.parentIndex()];
            localRotation.identity().rotateZYX(
                    initial.rotationValueZ,
                    initial.rotationValueY,
                    initial.rotationValueX
            );
            restOrientations[index] = new Quaternionf(parentOrientation)
                    .mul(localRotation);
            Matrix4f parentTransform = node.parentIndex() < 0
                    ? identity
                    : bindTransforms[node.parentIndex()];
            bindTransforms[index] = compose(
                    new Matrix4f(parentTransform),
                    bone,
                    initial.positionOffsetX,
                    initial.positionOffsetY,
                    initial.positionOffsetZ,
                    localRotation,
                    initial.scaleValueX,
                    initial.scaleValueY,
                    initial.scaleValueZ
            );
            bindInverses[index] = new Matrix4f(bindTransforms[index]).invert();
            animationTransforms[index] = new Matrix4f();
            affineDeltas[index] = new Matrix4f();
            normalTransforms[index] = new Matrix3f();
            maxBasisScales[index] = 1.0F;
        }
    }

    public void prepare() {
        identity.identity();
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (!required[index]) {
                continue;
            }
            PhysicsSolverLayout.Node node = layout.node(index);
            AnimatedGeoBone bone = node.bone();
            Matrix4f parent = node.parentIndex() < 0
                    ? identity
                    : animationTransforms[node.parentIndex()];
            localRotation.identity().rotateZYX(
                    bone.getRotationZ(),
                    bone.getRotationY(),
                    bone.getRotationX()
            );
            compose(
                    animationTransforms[index].set(parent),
                    bone,
                    bone.getPositionX(),
                    bone.getPositionY(),
                    bone.getPositionZ(),
                    localRotation,
                    bone.getScaleX(),
                    bone.getScaleY(),
                    bone.getScaleZ()
            );
            Matrix4f delta = affineDeltas[index]
                    .set(animationTransforms[index])
                    .mul(bindInverses[index]);
            delta.normal(normalTransforms[index]);
            maxBasisScales[index] = AffineScaleBound.upperBound(delta);
        }
    }

    public Matrix4f affineDelta(int nodeIndex) {
        return valid(nodeIndex) ? affineDeltas[nodeIndex] : identity;
    }

    public Matrix3f normalTransform(int nodeIndex) {
        return valid(nodeIndex)
                ? normalTransforms[nodeIndex]
                : identityNormal;
    }

    public Quaternionf restOrientation(int nodeIndex) {
        return valid(nodeIndex)
                ? restOrientations[nodeIndex]
                : identityOrientation;
    }

    public float maxBasisScale(int nodeIndex) {
        return valid(nodeIndex) ? maxBasisScales[nodeIndex] : 1.0F;
    }

    void reset() {
        identity.identity();
        identityNormal.identity();
        identityOrientation.identity();
        for (int index = 0; index < animationTransforms.length; index++) {
            animationTransforms[index].identity();
            affineDeltas[index].identity();
            normalTransforms[index].identity();
            maxBasisScales[index] = 1.0F;
        }
    }

    private boolean valid(int nodeIndex) {
        return nodeIndex >= 0 && nodeIndex < affineDeltas.length;
    }

    private static Matrix4f compose(
            Matrix4f output,
            AnimatedGeoBone bone,
            float positionX,
            float positionY,
            float positionZ,
            Quaternionf rotation,
            float scaleX,
            float scaleY,
            float scaleZ
    ) {
        float pivotX = bone.getPivotX() / PIXELS_PER_BLOCK;
        float pivotY = bone.getPivotY() / PIXELS_PER_BLOCK;
        float pivotZ = bone.getPivotZ() / PIXELS_PER_BLOCK;
        return output.translate(
                -positionX / PIXELS_PER_BLOCK,
                positionY / PIXELS_PER_BLOCK,
                positionZ / PIXELS_PER_BLOCK
        ).translate(pivotX, pivotY, pivotZ)
                .rotate(rotation)
                .scale(scaleX, scaleY, scaleZ)
                .translate(-pivotX, -pivotY, -pivotZ);
    }

}
