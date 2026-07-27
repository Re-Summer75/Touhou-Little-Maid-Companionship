package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Carries rendered affine transforms through the active skeleton and caches
 * exact model-space pivots and tips for later segment constraints.
 */
final class RuntimeBoneEndpoints {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private final PhysicsSolverLayout layout;
    private final Matrix4f identityTransform = new Matrix4f();
    private final Matrix4f animatedPivotTransform = new Matrix4f();
    private final Matrix4f[] renderedTransforms;
    private final Vector3f[] localPivots;
    private final Vector3f[] localTips;
    private final Vector3f[] renderedPivots;
    private final Vector3f[] renderedTips;
    private final float[] renderedScaleBounds;
    private final boolean[] pivotValid;
    private final boolean[] tipValid;

    RuntimeBoneEndpoints(PhysicsSolverLayout layout) {
        this.layout = layout;
        int count = layout.activeNodeCount();
        renderedTransforms = new Matrix4f[count];
        localPivots = new Vector3f[count];
        localTips = new Vector3f[count];
        renderedPivots = new Vector3f[count];
        renderedTips = new Vector3f[count];
        renderedScaleBounds = new float[count];
        pivotValid = new boolean[count];
        tipValid = new boolean[count];
        Vector3f axis = new Vector3f();
        for (int index = 0; index < count; index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            AnimatedGeoBone bone = node.bone();
            renderedTransforms[index] = new Matrix4f();
            localPivots[index] = node.driven()
                    ? node.kinematics().effectivePivot()
                    : authoredPivot(bone, new Vector3f());
            localTips[index] = new Vector3f(localPivots[index]);
            if (node.driven()) {
                node.axisInto(axis);
                localTips[index].fma(
                        node.kinematics().segmentLength()
                                / PIXELS_PER_BLOCK,
                        axis
                );
            }
            renderedPivots[index] = new Vector3f();
            renderedTips[index] = new Vector3f();
        }
    }

    Vector3f resolveAnimatedPivot(
            int nodeIndex,
            Quaternionf localAnimationRotation,
            Vector3f output
    ) {
        PhysicsSolverLayout.Node node = layout.node(nodeIndex);
        AnimatedGeoBone bone = node.bone();
        Matrix4f parentTransform = node.parentIndex() < 0
                ? identityTransform
                : renderedTransforms[node.parentIndex()];
        float pivotX = bone.getPivotX() / PIXELS_PER_BLOCK;
        float pivotY = bone.getPivotY() / PIXELS_PER_BLOCK;
        float pivotZ = bone.getPivotZ() / PIXELS_PER_BLOCK;
        animatedPivotTransform.set(parentTransform)
                .translate(
                        -bone.getPositionX() / PIXELS_PER_BLOCK,
                        bone.getPositionY() / PIXELS_PER_BLOCK,
                        bone.getPositionZ() / PIXELS_PER_BLOCK
                )
                .translate(pivotX, pivotY, pivotZ)
                .rotate(localAnimationRotation)
                .scale(
                        bone.getScaleX(),
                        bone.getScaleY(),
                        bone.getScaleZ()
                )
                .translate(-pivotX, -pivotY, -pivotZ);
        return animatedPivotTransform.transformPosition(
                localPivots[nodeIndex],
                output
        );
    }

    float resolveAnimatedSegmentLength(
            int nodeIndex,
            Vector3f runtimePivot,
            Vector3f scratch
    ) {
        animatedPivotTransform.transformPosition(
                localTips[nodeIndex],
                scratch
        );
        return Math.max(1.0E-6F, scratch.distance(runtimePivot));
    }

    float resolveAnimatedScaleBound(int nodeIndex) {
        PhysicsSolverLayout.Node node = layout.node(nodeIndex);
        float parentScale = node.parentIndex() < 0
                ? 1.0F
                : renderedScaleBounds[node.parentIndex()];
        AnimatedGeoBone bone = node.bone();
        float localScale = Math.max(
                Math.abs(bone.getScaleX()),
                Math.max(
                        Math.abs(bone.getScaleY()),
                        Math.abs(bone.getScaleZ())
                )
        );
        float result = parentScale * localScale;
        return Float.isFinite(result) ? Math.max(0.0F, result) : 1.0F;
    }

    void update(int index, Quaternionf localOrientation) {
        PhysicsSolverLayout.Node node = layout.node(index);
        AnimatedGeoBone bone = node.bone();
        Matrix4f parentTransform = node.parentIndex() < 0
                ? identityTransform
                : renderedTransforms[node.parentIndex()];
        float pivotX = bone.getPivotX() / PIXELS_PER_BLOCK;
        float pivotY = bone.getPivotY() / PIXELS_PER_BLOCK;
        float pivotZ = bone.getPivotZ() / PIXELS_PER_BLOCK;
        Matrix4f transform = renderedTransforms[index];
        transform.set(parentTransform)
                .translate(
                        -bone.getPositionX() / PIXELS_PER_BLOCK,
                        bone.getPositionY() / PIXELS_PER_BLOCK,
                        bone.getPositionZ() / PIXELS_PER_BLOCK
                )
                .translate(pivotX, pivotY, pivotZ)
                .rotate(localOrientation)
                .scale(
                        bone.getScaleX(),
                        bone.getScaleY(),
                        bone.getScaleZ()
                )
                .translate(-pivotX, -pivotY, -pivotZ);
        renderedScaleBounds[index] = resolveAnimatedScaleBound(index);
        transform.transformPosition(
                localPivots[index],
                renderedPivots[index]
        );
        pivotValid[index] = true;
        if (node.driven()) {
            transform.transformPosition(
                    localTips[index],
                    renderedTips[index]
            );
            tipValid[index] = true;
        } else {
            renderedTips[index].set(renderedPivots[index]);
            tipValid[index] = false;
        }
    }

    /**
     * Full model-space transform written on the last completed pass, physics
     * included. Read before the node loop it still holds the previous frame.
     */
    Matrix4f renderedTransform(int nodeIndex) {
        return renderedTransforms[nodeIndex];
    }

    boolean hasRenderedTransform(int nodeIndex) {
        return nodeIndex >= 0
                && nodeIndex < pivotValid.length
                && pivotValid[nodeIndex];
    }

    boolean copyPivot(int nodeIndex, Vector3f output) {
        if (nodeIndex < 0
                || nodeIndex >= renderedPivots.length
                || !pivotValid[nodeIndex]) {
            output.zero();
            return false;
        }
        output.set(renderedPivots[nodeIndex]);
        return true;
    }

    boolean copyTip(int nodeIndex, Vector3f output) {
        if (nodeIndex < 0
                || nodeIndex >= renderedTips.length
                || !tipValid[nodeIndex]) {
            output.zero();
            return false;
        }
        output.set(renderedTips[nodeIndex]);
        return true;
    }

    void reset() {
        identityTransform.identity();
        animatedPivotTransform.identity();
        for (int index = 0; index < renderedTransforms.length; index++) {
            renderedTransforms[index].identity();
            renderedPivots[index].zero();
            renderedTips[index].zero();
            renderedScaleBounds[index] = 1.0F;
            pivotValid[index] = false;
            tipValid[index] = false;
        }
    }

    private static Vector3f authoredPivot(
            AnimatedGeoBone bone,
            Vector3f output
    ) {
        return output.set(
                bone.getPivotX() / PIXELS_PER_BLOCK,
                bone.getPivotY() / PIXELS_PER_BLOCK,
                bone.getPivotZ() / PIXELS_PER_BLOCK
        );
    }
}
