package com.laixia.maidintelligence.feature.physics.layout.attachment;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
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

/**
 * Chooses between authored and confidence-gated inferred pivots.
 */
final class AttachmentPivotPolicy {
    private static final float MIN_SUPPORT_CONFIDENCE = 0.20F;
    private static final float MIN_TENTATIVE_CONFIDENCE = 0.12F;

    private AttachmentPivotPolicy() {
    }

    static Result resolve(
            Vector3f authoredPivot,
            Vector3f inferredPivot,
            BoneMeshMetrics mesh,
            PhysicsBoneSelectionPlan.ChainSegment chainSegment,
            float inferenceConfidence,
            float detachedTolerance,
            boolean reversed,
            boolean misplaced,
            boolean supportStabilityImprovement,
            boolean preserveAuthoredForStability
    ) {
        float authoredDistance = mesh.distanceTo(authoredPivot);
        boolean detached = authoredDistance > detachedTolerance;
        boolean clearlyDetached = authoredDistance > Math.max(
                detachedTolerance,
                mesh.diagonal() * 0.60F
        );
        // Distinct authored pivots are reliable joint evidence for a real
        // chain, unless the joint is clearly detached from its own segment.
        boolean authoredChainJoint = chainSegment.count() > 1
                && !clearlyDetached;
        boolean reliableInference =
                inferenceConfidence >= MIN_SUPPORT_CONFIDENCE;
        boolean tentativeGrossCorrection = clearlyDetached
                && inferenceConfidence >= MIN_TENTATIVE_CONFIDENCE;
        boolean corrected = !authoredChainJoint
                && !preserveAuthoredForStability
                && (reliableInference
                && (detached || reversed || misplaced)
                || tentativeGrossCorrection
                || supportStabilityImprovement);
        if (!corrected) {
            return new Result(new Vector3f(authoredPivot), false);
        }
        return new Result(new Vector3f(inferredPivot), true);
    }

    record Result(Vector3f pivot, boolean corrected) {
    }
}

/**
 * Detects authored pivots placed at the wrong attachment endpoint.
 */
final class AttachmentPivotEvidence {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float MIN_PRINCIPAL_CONFIDENCE = 0.48F;
    private static final float MIN_SUPPORT_CONFIDENCE = 0.20F;

    private AttachmentPivotEvidence() {
    }

    static boolean reversed(
            Vector3f authoredPivot,
            Vector3f proximal,
            Vector3f distal,
            MeshAttachmentAxis endpoints
    ) {
        if (endpoints.confidence() < MIN_PRINCIPAL_CONFIDENCE
                || endpoints.supportConfidence()
                < MIN_SUPPORT_CONFIDENCE) {
            return false;
        }
        float margin = Math.max(0.04F, endpoints.length() * 0.12F);
        return authoredPivot.distance(distal) + margin
                < authoredPivot.distance(proximal);
    }

    static boolean misplaced(
            Vector3f authoredPivot,
            Vector3f inferredPivot,
            MeshAttachmentAxis endpoints,
            BoneMeshMetrics mesh,
            AttachmentSupport support
    ) {
        if (endpoints.confidence() < MIN_PRINCIPAL_CONFIDENCE
                || endpoints.supportConfidence()
                < MIN_SUPPORT_CONFIDENCE) {
            return false;
        }
        float diagonal = mesh.diagonal();
        float tolerance = Math.max(
                2.0F / PIXELS_PER_BLOCK,
                Math.min(
                        diagonal * 0.12F,
                        endpoints.length() * 0.20F
                )
        );
        float scoreMargin = Math.max(
                0.03F,
                tolerance / Math.max(0.05F, diagonal) * 0.25F
        );
        return authoredPivot.distance(inferredPivot) > tolerance
                && support.score(inferredPivot, mesh.centroid())
                + scoreMargin
                < support.score(authoredPivot, mesh.centroid());
    }
}

/**
 * Detects a peripheral authored pivot that gives compact visible geometry an
 * unnecessarily large rotation lever despite reliable contact evidence.
 */
final class AttachmentLeverEvidence {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float MIN_CONTACT_CONFIDENCE = 0.20F;
    private static final float MIN_SHIFT_PIXELS = 0.75F;
    private static final float MAX_SHIFT_PIXELS = 2.0F;
    private static final float SHIFT_SCALE = 0.20F;
    private static final float MIN_REDUCTION_PIXELS = 0.75F;
    private static final float MAX_REDUCTION_PIXELS = 2.0F;
    private static final float REDUCTION_SCALE = 0.18F;
    private static final float MAX_INFERRED_RADIUS_RATIO = 0.55F;
    private static final float MIN_SURFACE_DISTANCE_PIXELS = 0.125F;
    private static final float MAX_SURFACE_DISTANCE_PIXELS = 0.5F;
    private static final float SURFACE_DISTANCE_SCALE = 0.04F;
    private static final float MIN_SECONDARY_EXTENT_RATIO = 0.55F;
    private static final int MIN_COMPOUND_CUBES = 3;

    private AttachmentLeverEvidence() {
    }

    static boolean supportsCorrection(
            PhysicsBoneSelectionPlan.PartType type,
            PhysicsBoneSelectionPlan.ChainSegment chainSegment,
            Vector3f authoredPivot,
            Vector3f inferredPivot,
            BoneMeshMetrics attachmentMesh,
            BoneMeshMetrics visibleMesh,
            float contactConfidence
    ) {
        if (!isEligibleType(type)
                || chainSegment.count() > 1
                || visibleMesh.cubeCount() < MIN_COMPOUND_CUBES
                || !isCompact(visibleMesh)
                || contactConfidence < MIN_CONTACT_CONFIDENCE) {
            return false;
        }
        float diagonalPixels = visibleMesh.diagonal() * PIXELS_PER_BLOCK;
        float minimumShift = clamp(
                diagonalPixels * SHIFT_SCALE,
                MIN_SHIFT_PIXELS,
                MAX_SHIFT_PIXELS
        );
        float minimumReduction = clamp(
                diagonalPixels * REDUCTION_SCALE,
                MIN_REDUCTION_PIXELS,
                MAX_REDUCTION_PIXELS
        );
        float surfaceTolerance = clamp(
                diagonalPixels * SURFACE_DISTANCE_SCALE,
                MIN_SURFACE_DISTANCE_PIXELS,
                MAX_SURFACE_DISTANCE_PIXELS
        );
        Vector3f center = visibleMesh.centroid();
        float authoredRadius = authoredPivot.distance(center)
                * PIXELS_PER_BLOCK;
        float inferredRadius = inferredPivot.distance(center)
                * PIXELS_PER_BLOCK;
        return authoredPivot.distance(inferredPivot) * PIXELS_PER_BLOCK
                >= minimumShift
                && authoredRadius - inferredRadius >= minimumReduction
                && inferredRadius
                <= authoredRadius * MAX_INFERRED_RADIUS_RATIO
                && attachmentMesh.distanceTo(inferredPivot)
                * PIXELS_PER_BLOCK <= surfaceTolerance;
    }

    private static boolean isEligibleType(
            PhysicsBoneSelectionPlan.PartType type
    ) {
        return type == PhysicsBoneSelectionPlan.PartType.HAIR
                || type == PhysicsBoneSelectionPlan.PartType.RIBBON
                || type == PhysicsBoneSelectionPlan.PartType.CAPE
                || type == PhysicsBoneSelectionPlan.PartType.GENERIC;
    }

    private static boolean isCompact(BoneMeshMetrics mesh) {
        Vector3f size = new Vector3f(mesh.max()).sub(mesh.min());
        float longest = Math.max(size.x, Math.max(size.y, size.z));
        float shortest = Math.min(size.x, Math.min(size.y, size.z));
        float middle = size.x + size.y + size.z - longest - shortest;
        return middle >= longest * MIN_SECONDARY_EXTENT_RATIO;
    }

    private static float clamp(
            float value,
            float minimum,
            float maximum
    ) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

/**
 * Detects scale-independent inverted support and a stabilizing contact pivot.
 */
final class SupportStabilityPivotEvidence {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float MIN_CONTACT_CONFIDENCE = 0.15F;
    private static final float MIN_MARGIN_PIXELS = 0.125F;
    private static final float MAX_MARGIN_PIXELS = 0.5F;
    private static final float MARGIN_SCALE = 0.025F;
    private static final float MIN_IMPROVEMENT_PIXELS = 0.25F;
    private static final float MAX_IMPROVEMENT_PIXELS = 1.0F;
    private static final float IMPROVEMENT_SCALE = 0.05F;
    private static final float MIN_PRESERVE_DISTANCE_PIXELS = 0.5F;
    private static final float MAX_PRESERVE_DISTANCE_PIXELS = 2.0F;
    private static final float PRESERVE_DISTANCE_SCALE = 0.15F;

    private SupportStabilityPivotEvidence() {
    }

    static Assessment assess(
            PhysicsBoneSelectionPlan.PartType type,
            PhysicsBoneSelectionPlan.StructureRole structureRole,
            PhysicsBoneSelectionPlan.ChainSegment chainSegment,
            Vector3f authoredPivot,
            Vector3f inferredPivot,
            BoneMeshMetrics mesh,
            float contactConfidence
    ) {
        if (chainSegment.count() > 1) {
            return Assessment.NONE;
        }
        float diagonalPixels = mesh.diagonal() * PIXELS_PER_BLOCK;
        float margin = clamp(
                diagonalPixels * MARGIN_SCALE,
                MIN_MARGIN_PIXELS,
                MAX_MARGIN_PIXELS
        );
        float minimumImprovement = clamp(
                diagonalPixels * IMPROVEMENT_SCALE,
                MIN_IMPROVEMENT_PIXELS,
                MAX_IMPROVEMENT_PIXELS
        );
        float authoredHeight = (
                authoredPivot.y - mesh.centroid().y
        ) * PIXELS_PER_BLOCK;
        float inferredHeight = (
                inferredPivot.y - mesh.centroid().y
        ) * PIXELS_PER_BLOCK;
        float improvement = (
                inferredPivot.y - authoredPivot.y
        ) * PIXELS_PER_BLOCK;
        float preserveDistance = clamp(
                diagonalPixels * PRESERVE_DISTANCE_SCALE,
                MIN_PRESERVE_DISTANCE_PIXELS,
                MAX_PRESERVE_DISTANCE_PIXELS
        );
        boolean reliableContact =
                contactConfidence >= MIN_CONTACT_CONFIDENCE;
        if (isMountedOrnament(type, structureRole)
                && reliableContact
                && authoredHeight >= -margin
                && inferredHeight <= -margin
                && improvement <= -minimumImprovement
                && mesh.distanceTo(authoredPivot) * PIXELS_PER_BLOCK
                <= preserveDistance) {
            return Assessment.PRESERVE_AUTHORED;
        }
        if (authoredHeight > -margin) {
            return Assessment.NONE;
        }
        if (reliableContact
                && inferredHeight >= margin
                && improvement >= minimumImprovement) {
            return Assessment.STABILIZING_CONTACT;
        }
        return requiresRigidFallback(structureRole)
                ? Assessment.UNSUPPORTED
                : Assessment.NONE;
    }

    private static boolean requiresRigidFallback(
            PhysicsBoneSelectionPlan.StructureRole structureRole
    ) {
        return structureRole
                == PhysicsBoneSelectionPlan.StructureRole.DANGLING_ACCESSORY;
    }

    static boolean isMountedOrnament(
            PhysicsBoneSelectionPlan.PartType type,
            PhysicsBoneSelectionPlan.StructureRole structureRole
    ) {
        return structureRole
                == PhysicsBoneSelectionPlan.StructureRole.DANGLING_ACCESSORY
                || type == PhysicsBoneSelectionPlan.PartType.RIBBON
                || type == PhysicsBoneSelectionPlan.PartType.CAPE;
    }

    private static float clamp(
            float value,
            float minimum,
            float maximum
    ) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    enum Assessment {
        NONE,
        STABILIZING_CONTACT,
        PRESERVE_AUTHORED,
        UNSUPPORTED
    }
}
