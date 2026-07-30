package com.laixia.maidintelligence.feature.physics.discovery;


import com.laixia.maidintelligence.feature.physics.layout.BoneKinematics;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;

/**
 * Keeps corrected, single-bone mounted parts near their authored pose.
 */
final class AttachmentMountStabilizer {
    private AttachmentMountStabilizer() {
    }

    static PhysicsBoneSelectionPlan.Decision apply(
            PhysicsBoneSelectionPlan.Decision decision,
            BoneKinematics.Metrics kinematics
    ) {
        boolean supportEvidence =
                kinematics.supportStabilityPivotCorrected()
                        || kinematics.supportStabilityPivotPreserved();
        boolean mountedSupport = supportEvidence
                && isMountedOrnament(decision);
        boolean stabilizationEvidence = mountedSupport
                || kinematics.attachmentLeverPivotCorrected();
        if (decision.source() != PhysicsBoneSelectionPlan.Source.AUTO
                || !stabilizationEvidence) {
            return decision;
        }
        PhysicsBoneSelectionPlan.SpringProfile stable =
                new PhysicsBoneSelectionPlan.SpringProfile(
                        1.35F, 0.0F, 0.15F, 1.35F,
                        0.45F, 0.55F, 0.45F, 0.50F
                );
        PhysicsBoneSelectionPlan.ConstraintProfile source =
                decision.constraints();
        PhysicsBoneSelectionPlan.ConstraintProfile constraints =
                new PhysicsBoneSelectionPlan.ConstraintProfile(
                        source.simulationSpace(),
                        Math.min(
                                0.10F,
                                source.rotationInertiaScale() * 0.25F
                        ),
                        new PhysicsBoneSelectionPlan.SwingLimits(
                                0.16F, 0.16F, 0.16F, 0.10F
                        ),
                        source.backstop(),
                        source.headCollision(),
                        source.hitRadiusScale(),
                        source.collision(),
                        source.enabled()
                );
        return decision.withDynamics(
                decision.profile().multiply(stable),
                constraints,
                PhysicsBoneSelectionPlan.StructureRole.COMPOUND_SINGLE_BONE,
                decision.chainSegment()
        );
    }

    private static boolean isMountedOrnament(
            PhysicsBoneSelectionPlan.Decision decision
    ) {
        return decision.structureRole()
                == PhysicsBoneSelectionPlan.StructureRole.DANGLING_ACCESSORY
                || decision.type() == PhysicsBoneSelectionPlan.PartType.RIBBON
                || decision.type() == PhysicsBoneSelectionPlan.PartType.CAPE;
    }
}
