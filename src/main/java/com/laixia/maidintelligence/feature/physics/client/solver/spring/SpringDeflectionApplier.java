package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import org.joml.Quaternionf;

/**
 * Converts the simulated direction into Gecko rotation and pivot offsets.
 */
final class SpringDeflectionApplier {
    private SpringDeflectionApplier() {
    }

    static void apply(
            boolean constraintsEnabled,
            PhysicsSolverLayout.Node node,
            Quaternionf boneAnimationOrientation,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz,
            SpringBoneState state,
            SpringBoneScratch scratch,
            SpringBoneMetrics metrics
    ) {
        int slot = node.drivenSlot();
        node.axisInto(scratch.boneAxis);
        boneAnimationOrientation.transformInverse(
                state.currentDirections[slot],
                scratch.localDirection
        );
        if (scratch.localDirection.lengthSquared()
                < SpringBoneMath.EPSILON) {
            return;
        }
        scratch.localDirection.normalize();
        float dot = Math.max(
                -1.0F,
                Math.min(
                        1.0F,
                        scratch.boneAxis.dot(scratch.localDirection)
                )
        );
        scratch.boneAxis.cross(
                scratch.localDirection,
                scratch.deflectionAxis
        );
        float sin = scratch.deflectionAxis.length();
        if (sin < SpringBoneMath.EPSILON) {
            if (dot >= 0.0F) {
                return;
            }
            if (Math.abs(scratch.boneAxis.y) < 0.90F) {
                scratch.deflectionAxis.set(0.0F, 1.0F, 0.0F)
                        .cross(scratch.boneAxis);
            } else {
                scratch.deflectionAxis.set(1.0F, 0.0F, 0.0F)
                        .cross(scratch.boneAxis);
            }
            scratch.deflectionAxis.normalize();
        } else {
            scratch.deflectionAxis.div(sin);
        }

        PhysicsBoneSelectionPlan.SpringProfile profile =
                node.decision().profile();
        BoneKinematics.Metrics kinematics = node.kinematics();
        float cap = Math.min(
                Math.min(
                        SpringBoneMath.MAX_ANGLE,
                        kinematics.safeAngle()
                ) * profile.angleScale(),
                SpringBoneMath.MAX_TIP_DISPLACEMENT
                        * profile.tipDisplacementScale()
                        / kinematics.leverArm()
        );
        boolean constrainedOutput =
                constraintsEnabled && node.constraint().enabled();
        float angle = constrainedOutput
                ? (float) Math.acos(dot)
                : Math.min((float) Math.acos(dot), cap);
        AnimatedGeoBone bone = node.bone();
        if (constrainedOutput) {
            applyConstrained(
                    bone,
                    kinematics,
                    angle,
                    rx,
                    ry,
                    rz,
                    px,
                    py,
                    pz,
                    scratch
            );
        } else {
            applyLegacy(
                    bone,
                    kinematics,
                    profile,
                    angle,
                    rx,
                    ry,
                    rz,
                    px,
                    py,
                    pz,
                    scratch
            );
        }
        metrics.recordDeflection(angle);
    }

    private static void applyConstrained(
            AnimatedGeoBone bone,
            BoneKinematics.Metrics kinematics,
            float angle,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz,
            SpringBoneScratch scratch
    ) {
        scratch.animationRotation.identity().rotateZYX(rz, ry, rx);
        scratch.physicalDelta.identity().rotateAxis(
                angle,
                scratch.deflectionAxis.x,
                scratch.deflectionAxis.y,
                scratch.deflectionAxis.z
        );
        scratch.localRotation.set(scratch.animationRotation)
                .mul(scratch.physicalDelta);
        SpringBoneMath.eulerZYXInto(
                scratch.localRotation,
                scratch.rotationEuler
        );
        bone.setRotationX(scratch.rotationEuler.x);
        bone.setRotationY(scratch.rotationEuler.y);
        bone.setRotationZ(scratch.rotationEuler.z);
        SpringPivotCompensator.applyPose(
                bone,
                kinematics,
                scratch.animationRotation,
                scratch.localRotation,
                px,
                py,
                pz,
                scratch
        );
    }

    private static void applyLegacy(
            AnimatedGeoBone bone,
            BoneKinematics.Metrics kinematics,
            PhysicsBoneSelectionPlan.SpringProfile profile,
            float angle,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz,
            SpringBoneScratch scratch
    ) {
        float dRx = SpringBoneMath.clampAbs(
                scratch.deflectionAxis.x() * angle,
                SpringBoneMath.MAX_DEFLECT_X * profile.angleScale()
        );
        float dRy = SpringBoneMath.clampAbs(
                scratch.deflectionAxis.y() * angle,
                SpringBoneMath.MAX_DEFLECT_Y * profile.angleScale()
        );
        float dRz = SpringBoneMath.clampAbs(
                scratch.deflectionAxis.z() * angle,
                SpringBoneMath.MAX_DEFLECT_Z * profile.angleScale()
        );
        bone.setRotationX(rx + dRx);
        bone.setRotationY(ry + dRy);
        bone.setRotationZ(rz + dRz);
        SpringPivotCompensator.applyLegacy(
                bone,
                kinematics,
                rx,
                ry,
                rz,
                px,
                py,
                pz,
                scratch
        );
    }
}
