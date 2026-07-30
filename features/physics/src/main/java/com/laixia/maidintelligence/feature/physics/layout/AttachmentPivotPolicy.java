package com.laixia.maidintelligence.feature.physics.layout;


import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import org.joml.Vector3f;

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
