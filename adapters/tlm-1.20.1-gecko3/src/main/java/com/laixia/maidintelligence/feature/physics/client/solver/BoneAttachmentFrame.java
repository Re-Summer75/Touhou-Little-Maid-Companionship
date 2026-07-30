package com.laixia.maidintelligence.feature.physics.client.solver;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import org.joml.Vector3f;

/**
 * Resolves the proximal mesh endpoint without trusting author pivots.
 */
record BoneAttachmentFrame(
        Vector3f effectivePivot,
        Vector3f axis,
        float safeAngle,
        float supportConfidence,
        float contactConfidence,
        float pivotScore,
        boolean supportStabilityCorrected,
        boolean supportStabilityPreserved,
        boolean attachmentLeverCorrected,
        boolean supportStabilityUnsupported
) {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float MIN_PRINCIPAL_CONFIDENCE = 0.48F;

    static BoneAttachmentFrame resolve(
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            AnimatedGeoBone nearestSolidAncestor,
            PhysicsBoneSelectionPlan.PartType type,
            PhysicsBoneSelectionPlan.StructureRole structureRole,
            PhysicsBoneSelectionPlan.ChainSegment chainSegment,
            BoneMeshMetrics mesh,
            BoneMeshMetrics loadMesh
    ) {
        Vector3f authoredPivot = pivotOf(bone);
        AttachmentSupport support = AttachmentSupport.resolve(
                bone,
                parent,
                nearestSolidAncestor,
                type,
                mesh
        );
        Vector3f attachmentHint = support.target(mesh);
        MeshAttachmentAxis endpoints = mesh.attachmentAxis(support);
        Vector3f proximal = endpoints.proximal();
        Vector3f distal = endpoints.distal();
        if (endpoints.confidence() < MIN_PRINCIPAL_CONFIDENCE
                && attachmentHint != null) {
            Vector3f closest = mesh.closestPoint(attachmentHint);
            if (closest.distance(mesh.centroid())
                    > Math.max(0.02F, mesh.diagonal() * 0.12F)
                    && support.score(closest, mesh.centroid()) + 0.03F
                    < support.score(proximal, mesh.centroid())) {
                proximal.set(closest);
            }
        }
        proximal.set(mesh.closestPoint(proximal));
        distal.set(mesh.closestPoint(distal));
        PivotInferenceResult inference = inferPivot(
                authoredPivot,
                proximal,
                mesh,
                support,
                endpoints.supportConfidence()
        );
        float contactConfidence = inference.contactBased()
                ? inference.confidence()
                : 0.0F;
        float pivotConfidence = inference.contactBased()
                ? contactConfidence
                : endpoints.supportConfidence();
        float supportConfidence = Math.max(
                endpoints.supportConfidence(),
                contactConfidence
        );
        if (type == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL) {
            return HeadShellAttachmentFrame.resolve(
                    mesh,
                    inference,
                    supportConfidence,
                    contactConfidence
            );
        }

        AttachmentPivotResolution pivot =
                AttachmentPivotResolution.resolve(
                        type,
                        structureRole,
                        chainSegment,
                authoredPivot,
                        inference,
                        mesh,
                        loadMesh,
                        endpoints,
                        support,
                        pivotConfidence,
                        contactConfidence
                );
        Vector3f effectivePivot = pivot.pivot();
        Vector3f axis = AttachmentFrameGeometry.axis(
                mesh,
                endpoints,
                proximal,
                distal,
                effectivePivot
        );
        float safeAngle = AttachmentFrameGeometry.safeAngle(
                pivot.corrected(),
                endpoints.confidence(),
                pivotConfidence,
                pivot.strictAngleLimit()
        );
        return new BoneAttachmentFrame(
                effectivePivot,
                axis,
                safeAngle,
                supportConfidence,
                contactConfidence,
                inference.score(),
                pivot.supportStabilityCorrected(),
                pivot.supportStabilityPreserved(),
                pivot.attachmentLeverCorrected(),
                pivot.supportStabilityUnsupported()
        );
    }

    private static PivotInferenceResult inferPivot(
            Vector3f authoredPivot,
            Vector3f fallbackPivot,
            BoneMeshMetrics mesh,
            AttachmentSupport support,
            float fallbackConfidence
    ) {
        if (support.body() == null) {
            return PivotInferenceResult.fallback(
                    fallbackPivot,
                    fallbackConfidence
            );
        }
        AttachmentContactPatch patch = AttachmentContactAnalyzer.analyze(
                mesh,
                support.body()
        );
        return VirtualPivotOptimizer.optimize(
                authoredPivot,
                fallbackPivot,
                mesh,
                support.body(),
                patch
        );
    }

    private static Vector3f pivotOf(AnimatedGeoBone bone) {
        return new Vector3f(
                bone.getPivotX() / PIXELS_PER_BLOCK,
                bone.getPivotY() / PIXELS_PER_BLOCK,
                bone.getPivotZ() / PIXELS_PER_BLOCK
        );
    }

}
