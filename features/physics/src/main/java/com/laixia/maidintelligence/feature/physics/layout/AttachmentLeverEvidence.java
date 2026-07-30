package com.laixia.maidintelligence.feature.physics.layout;


import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import org.joml.Vector3f;

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
