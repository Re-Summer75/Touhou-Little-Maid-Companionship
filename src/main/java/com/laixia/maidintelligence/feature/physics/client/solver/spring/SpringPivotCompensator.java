package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;
import org.joml.Quaternionf;

/**
 * Keeps corrected virtual pivots fixed while physical rotation is applied.
 */
final class SpringPivotCompensator {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private SpringPivotCompensator() {
    }

    static void applyPose(
            AnimatedGeoBone bone,
            BoneKinematics.Metrics kinematics,
            Quaternionf animation,
            Quaternionf physical,
            float px,
            float py,
            float pz,
            SpringBoneScratch scratch
    ) {
        if (!kinematics.compensatesPivot()) {
            return;
        }
        kinematics.poseCompensationOffsetInto(
                animation,
                physical,
                bone.getScaleX(),
                bone.getScaleY(),
                bone.getScaleZ(),
                scratch.pivotOffset,
                scratch.pivotScratch
        );
        applyOffset(bone, px, py, pz, scratch);
    }

    static void applyLegacy(
            AnimatedGeoBone bone,
            BoneKinematics.Metrics kinematics,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz,
            SpringBoneScratch scratch
    ) {
        if (!kinematics.compensatesPivot()) {
            return;
        }
        scratch.animationRotation.identity().rotateZYX(rz, ry, rx);
        scratch.physicalDelta.identity().rotateZYX(
                bone.getRotationZ(),
                bone.getRotationY(),
                bone.getRotationX()
        ).mul(scratch.animationRotation.invert());
        kinematics.compensationOffsetInto(
                scratch.physicalDelta,
                scratch.pivotOffset,
                scratch.pivotScratch
        );
        applyOffset(bone, px, py, pz, scratch);
    }

    private static void applyOffset(
            AnimatedGeoBone bone,
            float px,
            float py,
            float pz,
            SpringBoneScratch scratch
    ) {
        bone.setPositionX(
                px - scratch.pivotOffset.x * PIXELS_PER_BLOCK
        );
        bone.setPositionY(
                py + scratch.pivotOffset.y * PIXELS_PER_BLOCK
        );
        bone.setPositionZ(
                pz + scratch.pivotOffset.z * PIXELS_PER_BLOCK
        );
    }
}
