package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import org.joml.Vector3f;

/**
 * Converts an optional model-space atmosphere signal into a moving animation
 * rest direction. The spring follows this target; no physical force is added.
 *
 * <p>Each bone additionally sits in its own eddy, so the shared signal is
 * decorrelated per bone before it becomes a pose target.
 */
final class SpringProceduralPoseDriver {
    private static final float DRIVE_GAIN = 0.90F;
    private static final float SAFETY_SHARE = 0.65F;
    private static final float DEGENERATE_SQUARED = 1.0E-12F;

    private SpringProceduralPoseDriver() {
    }

    static void apply(
            PhysicsSolverLayout.Node node,
            Vector3f modelPoseDrive,
            float turbulenceStep,
            SpringBoneState state,
            SpringBoneScratch scratch
    ) {
        PhysicsBoneSelectionPlan.SpringProfile profile =
                node.decision().profile();
        float response = profile.poseDriveScale();
        if (response <= 0.0F
                || !Float.isFinite(modelPoseDrive.x)
                || !Float.isFinite(modelPoseDrive.y)
                || !Float.isFinite(modelPoseDrive.z)) {
            return;
        }

        Vector3f restDirection = scratch.restDirection;
        float alongAxis = restDirection.dot(modelPoseDrive);
        Vector3f tangent = scratch.poseDriveTangent
                .set(modelPoseDrive)
                .fma(-alongAxis, restDirection);
        float tangentSquared = tangent.lengthSquared();
        if (tangentSquared <= DEGENERATE_SQUARED) {
            return;
        }

        float tangentLength = (float) Math.sqrt(tangentSquared);
        int slot = node.drivenSlot();
        int turbulenceSeed = state.turbulenceSeeds[slot];
        if (turbulenceStep > 0.0F) {
            state.turbulencePhases[slot] += turbulenceStep
                    * SpringMicroTurbulence.frequency(
                    turbulenceSeed,
                    tangentLength
            );
        }
        double turbulencePhase = state.turbulencePhases[slot]
                + SpringMicroTurbulence.phaseOffset(turbulenceSeed);
        float requestedAngle = tangentLength * DRIVE_GAIN * response
                * SpringMicroTurbulence.amplitudeGain(
                turbulenceSeed,
                turbulencePhase
        );
        float geometricLimit = Math.min(
                SpringBoneMath.MAX_ANGLE,
                node.kinematics().safeAngle()
        ) * profile.angleScale() * SAFETY_SHARE;
        float displacementLimit =
                SpringBoneMath.MAX_TIP_DISPLACEMENT
                        * profile.tipDisplacementScale()
                        / node.kinematics().leverArm()
                        * SAFETY_SHARE;
        float angleLimit = Math.max(
                0.0F,
                Math.min(
                        maximumDriveAngle(node.decision().type()),
                        Math.min(geometricLimit, displacementLimit)
                )
        );
        // Soft saturation preserves gust variation below the safety ceiling.
        if (angleLimit <= 0.0F) {
            return;
        }
        float angle = angleLimit * (float) Math.tanh(
                requestedAngle / angleLimit
        );

        tangent.div(tangentLength);
        // Swing azimuth wanders per bone; magnitude limits stay untouched.
        float azimuth = SpringMicroTurbulence.azimuthOffset(
                turbulenceSeed,
                turbulencePhase
        );
        scratch.poseDriveBinormal.set(restDirection).cross(tangent);
        tangent.mul((float) Math.cos(azimuth))
                .fma((float) Math.sin(azimuth), scratch.poseDriveBinormal)
                .normalize();
        restDirection.mul((float) Math.cos(angle))
                .fma((float) Math.sin(angle), tangent)
                .normalize();
    }

    private static float maximumDriveAngle(
            PhysicsBoneSelectionPlan.PartType type
    ) {
        return switch (type) {
            case HEAD_SHELL -> 0.0F;
            case HAIR, RIBBON -> 0.26F;
            case TAIL -> 0.30F;
            case EAR -> 0.17F;
            case SKIRT, WING -> 0.13F;
            case CAPE, GENERIC -> 0.20F;
        };
    }
}
