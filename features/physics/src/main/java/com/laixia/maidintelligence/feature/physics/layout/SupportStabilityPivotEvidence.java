package com.laixia.maidintelligence.feature.physics.layout;


import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import org.joml.Vector3f;

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
