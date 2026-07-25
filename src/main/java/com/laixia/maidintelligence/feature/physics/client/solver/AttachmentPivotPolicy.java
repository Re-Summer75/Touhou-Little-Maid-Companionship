package com.laixia.maidintelligence.feature.physics.client.solver;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import org.joml.Vector3f;

/**
 * Chooses between authored, inferred, and nearest-surface pivots.
 */
final class AttachmentPivotPolicy {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float MIN_SUPPORT_CONFIDENCE = 0.20F;

    private AttachmentPivotPolicy() {
    }

    static Result resolve(
            Vector3f authoredPivot,
            Vector3f inferredPivot,
            BoneMeshMetrics mesh,
            PhysicsBoneSelectionPlan.ChainSegment chainSegment,
            float supportConfidence,
            float detachedTolerance,
            boolean reversed,
            boolean misplaced
    ) {
        float authoredDistance = mesh.distanceTo(authoredPivot);
        boolean detached = authoredDistance > detachedTolerance;
        boolean clearlyDetached = authoredDistance > Math.max(
                detachedTolerance,
                mesh.diagonal() * 0.60F
        );
        float unsupportedGap = Math.max(
                1.0F / PIXELS_PER_BLOCK,
                Math.min(
                        2.0F / PIXELS_PER_BLOCK,
                        mesh.diagonal() * 0.18F
                )
        );
        boolean unsupportedExternalPivot =
                supportConfidence < MIN_SUPPORT_CONFIDENCE
                        && authoredDistance > unsupportedGap;
        // Distinct authored pivots are reliable joint evidence for a real
        // chain, unless the joint is clearly detached from its own segment.
        boolean authoredChainJoint = chainSegment.count() > 1
                && !clearlyDetached;
        boolean corrected = !authoredChainJoint
                && (detached || reversed || misplaced
                || unsupportedExternalPivot);
        if (!corrected) {
            return new Result(new Vector3f(authoredPivot), false);
        }
        Vector3f pivot = supportConfidence < MIN_SUPPORT_CONFIDENCE
                ? mesh.closestPoint(authoredPivot)
                : new Vector3f(inferredPivot);
        return new Result(pivot, true);
    }

    record Result(Vector3f pivot, boolean corrected) {
    }
}
