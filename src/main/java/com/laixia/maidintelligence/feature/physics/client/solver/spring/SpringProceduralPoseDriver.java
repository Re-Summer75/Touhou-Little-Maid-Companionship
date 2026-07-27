package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SwingRange;
import org.joml.Vector3f;

/**
 * Converts an optional model-space atmosphere signal into a lateral bias on
 * the spring, expressed as {@code tan(angle)} along a unit tangent.
 *
 * <p>The bias is added alongside the restoring pull rather than replacing the
 * rest direction, so the authored pose stays the equilibrium the spring is
 * drawn back to: a bone starts at rest and leans into the wind over several
 * frames, and returns on its own once the wind drops. Because the integrator
 * scales the bias by the same stiffness it applies to the rest direction, the
 * steady-state lean is exactly {@code angle} regardless of frame time or mass.
 *
 * <p>Each bone additionally sits in its own eddy, so the shared signal is
 * decorrelated per bone before it becomes a lean.
 */
final class SpringProceduralPoseDriver {
    /**
     * Sized against the gust-only signal. Rejecting the sustained band costs
     * roughly a third of the field's amplitude, so the lean is scaled back up
     * to keep strong weather reading as strongly as it did when the steady
     * component was still allowed to hold parts over.
     */
    private static final float DRIVE_GAIN = 1.45F;
    /**
     * Share of the segment's own swing ceiling a gust may claim. The remainder
     * is left to inertia and the walk cycle, which are added on top; the hard
     * ceiling behind this is enforced by projection either way, so the share
     * only decides whether a storm reads as strong on its own or spends the
     * frame pinned against the limit by everything else.
     */
    private static final float SAFETY_SHARE = 0.78F;
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
        scratch.poseDriveBias.zero();
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
        float angleLimit = Math.max(
                0.0F,
                Math.min(
                        maximumDriveAngle(node.decision().type()),
                        SwingRange.maximum(node) * SAFETY_SHARE
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
        /*
         * Equilibrium requires the restoring pull and the bias to be parallel
         * to the leaning direction: k/cos(angle) = bias/sin(angle). The bias
         * therefore carries tan(angle) and the integrator supplies k, which
         * already folds in dt and mass.
         */
        scratch.poseDriveBias.set(tangent).mul((float) Math.tan(angle));
    }

    /**
     * Lean, in radians, a part reaches only in the strongest weather the field
     * can produce. Ordinary breezes land far below this: the request saturates
     * softly against it, so raising the ceiling opens up the storm end without
     * touching how calm days read.
     */
    private static float maximumDriveAngle(
            PhysicsBoneSelectionPlan.PartType type
    ) {
        return switch (type) {
            case HEAD_SHELL -> 0.0F;
            case HAIR, RIBBON -> 0.40F;
            case TAIL -> 0.46F;
            case EAR -> 0.26F;
            case SKIRT, WING -> 0.22F;
            case CAPE, GENERIC -> 0.32F;
        };
    }
}
