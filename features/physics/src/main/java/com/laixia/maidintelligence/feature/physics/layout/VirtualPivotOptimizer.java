package com.laixia.maidintelligence.feature.physics.layout;


import org.joml.Vector3f;

/**
 * Selects a contact-aware pivot using fixed offline virtual swings.
 */
final class VirtualPivotOptimizer {
    private static final float EPSILON = 1.0E-6F;

    private VirtualPivotOptimizer() {
    }

    static PivotInferenceResult optimize(
            Vector3f authoredPivot,
            Vector3f fallbackPivot,
            BoneMeshMetrics child,
            BoneMeshMetrics support,
            AttachmentContactPatch patch
    ) {
        if (!patch.present()) {
            return PivotInferenceResult.fallback(fallbackPivot, 0.0F);
        }
        Vector3f[] candidates = VirtualPivotCandidates.generate(
                authoredPivot,
                fallbackPivot,
                child,
                patch
        );
        Vector3f bestPivot = candidates[0];
        float bestScore = Float.POSITIVE_INFINITY;
        float secondScore = Float.POSITIVE_INFINITY;
        for (Vector3f candidate : candidates) {
            float score = VirtualPivotScorer.score(
                    candidate,
                    authoredPivot,
                    child,
                    support,
                    patch
            ).total();
            if (score < bestScore) {
                secondScore = bestScore;
                bestScore = score;
                bestPivot = candidate;
            } else if (score < secondScore) {
                secondScore = score;
            }
        }
        float separation = Float.isFinite(secondScore)
                ? clamp(
                (secondScore - bestScore)
                        / Math.max(EPSILON, secondScore),
                0.0F,
                1.0F
        )
                : 1.0F;
        float quality = 1.0F / (1.0F + bestScore * 4.0F);
        float confidence = patch.confidence()
                * (0.55F + separation * 0.45F)
                * quality;
        return new PivotInferenceResult(
                bestPivot,
                clamp(confidence, 0.0F, 1.0F),
                bestScore,
                true
        );
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
