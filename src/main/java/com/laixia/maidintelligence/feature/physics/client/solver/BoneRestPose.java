package com.laixia.maidintelligence.feature.physics.client.solver;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.snapshot.BoneSnapshot;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.IdentityHashMap;

/**
 * Exact bind-pose affine transform matching Gecko's render order.
 */
final class BoneRestPose {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private final Quaternionf orientation;
    private final Matrix4f transform;

    private BoneRestPose(Quaternionf orientation, Matrix4f transform) {
        this.orientation = orientation;
        this.transform = transform;
    }

    static IdentityHashMap<AnimatedGeoBone, BoneRestPose> collect(
            AnimatedGeoModel model
    ) {
        IdentityHashMap<AnimatedGeoBone, BoneRestPose> output =
                new IdentityHashMap<>();
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            collect(
                    bone,
                    new Quaternionf(),
                    new Matrix4f(),
                    output
            );
        }
        return output;
    }

    private static void collect(
            AnimatedGeoBone bone,
            Quaternionf parentOrientation,
            Matrix4f parentTransform,
            IdentityHashMap<AnimatedGeoBone, BoneRestPose> output
    ) {
        BoneSnapshot initial = bone.getInitialSnapshot();
        Quaternionf local = new Quaternionf().rotateZYX(
                initial.rotationValueZ,
                initial.rotationValueY,
                initial.rotationValueX
        );
        Quaternionf orientation = new Quaternionf(parentOrientation)
                .mul(local);
        float pivotX = bone.getPivotX() / PIXELS_PER_BLOCK;
        float pivotY = bone.getPivotY() / PIXELS_PER_BLOCK;
        float pivotZ = bone.getPivotZ() / PIXELS_PER_BLOCK;
        Matrix4f transform = new Matrix4f(parentTransform)
                .translate(
                        -initial.positionOffsetX / PIXELS_PER_BLOCK,
                        initial.positionOffsetY / PIXELS_PER_BLOCK,
                        initial.positionOffsetZ / PIXELS_PER_BLOCK
                )
                .translate(pivotX, pivotY, pivotZ)
                .rotate(local)
                .scale(
                        initial.scaleValueX,
                        initial.scaleValueY,
                        initial.scaleValueZ
                )
                .translate(-pivotX, -pivotY, -pivotZ);
        output.put(bone, new BoneRestPose(orientation, transform));
        for (AnimatedGeoBone child : bone.children()) {
            collect(child, orientation, transform, output);
        }
    }

    Quaternionf orientation() {
        return orientation;
    }

    Vector3f transformPosition(Vector3f position, Vector3f output) {
        return transform.transformPosition(position, output);
    }
}
