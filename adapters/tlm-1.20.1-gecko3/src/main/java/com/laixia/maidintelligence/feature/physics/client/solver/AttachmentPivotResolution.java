package com.laixia.maidintelligence.feature.physics.client.solver;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import org.joml.Vector3f;

/**
 * Combines authored-pivot, contact, support, and lever evidence.
 */
record AttachmentPivotResolution(
        Vector3f pivot,
        boolean corrected,
        boolean supportStabilityCorrected,
        boolean supportStabilityPreserved,
        boolean attachmentLeverCorrected,
        boolean supportStabilityUnsupported,
        boolean strictAngleLimit
) {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float MIN_PIVOT_TOLERANCE =
            4.0F / PIXELS_PER_BLOCK;

    static AttachmentPivotResolution resolve(
            PhysicsBoneSelectionPlan.PartType type,
            PhysicsBoneSelectionPlan.StructureRole structureRole,
            PhysicsBoneSelectionPlan.ChainSegment chainSegment,
            Vector3f authoredPivot,
            PivotInferenceResult inference,
            BoneMeshMetrics mesh,
            BoneMeshMetrics loadMesh,
            MeshAttachmentAxis endpoints,
            AttachmentSupport support,
            float pivotConfidence,
            float contactConfidence
    ) {
        boolean leverImprovement =
                AttachmentLeverEvidence.supportsCorrection(
                        type,
                        chainSegment,
                        authoredPivot,
                        inference.pivot(),
                        mesh,
                        loadMesh,
                        contactConfidence
                );
        boolean reversed = AttachmentPivotEvidence.reversed(
                authoredPivot,
                endpoints.proximal(),
                endpoints.distal(),
                endpoints
        );
        boolean misplaced = leverImprovement
                || AttachmentPivotEvidence.misplaced(
                authoredPivot,
                inference.pivot(),
                endpoints,
                mesh,
                support
        );
        SupportStabilityPivotEvidence.Assessment stability =
                SupportStabilityPivotEvidence.assess(
                        type,
                        structureRole,
                        chainSegment,
                        authoredPivot,
                        inference.pivot(),
                        loadMesh,
                        contactConfidence
                );
        boolean stabilityImprovement = stability
                == SupportStabilityPivotEvidence.Assessment
                .STABILIZING_CONTACT;
        boolean preserveAuthored = stability
                == SupportStabilityPivotEvidence.Assessment
                .PRESERVE_AUTHORED;
        float tolerance = Math.max(
                MIN_PIVOT_TOLERANCE,
                mesh.diagonal() * 0.50F
        );
        AttachmentPivotPolicy.Result result = AttachmentPivotPolicy.resolve(
                authoredPivot,
                inference.pivot(),
                mesh,
                chainSegment,
                pivotConfidence,
                tolerance,
                reversed,
                misplaced,
                stabilityImprovement,
                preserveAuthored
        );
        boolean corrected = result.corrected();
        boolean strictSupport = (stabilityImprovement || preserveAuthored)
                && SupportStabilityPivotEvidence.isMountedOrnament(
                type,
                structureRole
        );
        return new AttachmentPivotResolution(
                result.pivot(),
                corrected,
                stabilityImprovement,
                preserveAuthored,
                leverImprovement && corrected,
                stability
                        == SupportStabilityPivotEvidence.Assessment.UNSUPPORTED,
                strictSupport || leverImprovement
        );
    }
}
