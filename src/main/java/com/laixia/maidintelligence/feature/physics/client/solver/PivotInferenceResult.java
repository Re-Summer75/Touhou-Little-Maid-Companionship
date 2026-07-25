package com.laixia.maidintelligence.feature.physics.client.solver;

import org.joml.Vector3f;

/**
 * Baked pivot candidate and diagnostics from contact-aware optimization.
 */
record PivotInferenceResult(
        Vector3f pivot,
        float confidence,
        float score,
        boolean contactBased
) {
    PivotInferenceResult {
        pivot = new Vector3f(pivot);
    }

    static PivotInferenceResult fallback(
            Vector3f pivot,
            float confidence
    ) {
        return new PivotInferenceResult(
                pivot,
                confidence,
                Float.POSITIVE_INFINITY,
                false
        );
    }
}
