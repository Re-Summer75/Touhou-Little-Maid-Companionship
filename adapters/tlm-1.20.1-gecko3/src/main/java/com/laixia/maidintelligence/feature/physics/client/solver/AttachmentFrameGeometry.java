package com.laixia.maidintelligence.feature.physics.client.solver;

import org.joml.Vector3f;

/**
 * Derives the normalized attachment axis and its geometric safety limit.
 */
final class AttachmentFrameGeometry {
    private static final float MIN_PRINCIPAL_CONFIDENCE = 0.48F;
    private static final float MIN_SUPPORT_CONFIDENCE = 0.20F;
    /**
     * Ceiling for a segment whose rotation centre had to be inferred. Turning
     * far around a guessed pivot moves the mesh somewhere the author never
     * placed it, so the error grows with the angle and this stays well under
     * {@link SwingRange#MAX_ANGLE}.
     */
    private static final float INFERRED_PIVOT_ANGLE = 0.40F;
    private static final float EPSILON = 1.0E-6F;

    private AttachmentFrameGeometry() {
    }

    static Vector3f axis(
            BoneMeshMetrics mesh,
            MeshAttachmentAxis endpoints,
            Vector3f proximal,
            Vector3f distal,
            Vector3f effectivePivot
    ) {
        Vector3f directed = new Vector3f(distal).sub(proximal);
        Vector3f axis = new Vector3f(mesh.centroid())
                .sub(effectivePivot);
        float minimum = Math.max(0.02F, endpoints.length() * 0.08F);
        if (directed.lengthSquared() > EPSILON
                && endpoints.confidence()
                >= MIN_PRINCIPAL_CONFIDENCE) {
            float alignment = axis.lengthSquared() < EPSILON
                    ? 0.0F
                    : axis.dot(directed)
                    / (axis.length() * directed.length());
            if (axis.lengthSquared() < minimum * minimum
                    || Math.abs(alignment) < 0.70F) {
                axis.set(directed);
            } else if (alignment < 0.0F) {
                axis.negate();
            }
        } else if (axis.lengthSquared() < minimum * minimum) {
            axis.set(directed);
        }
        if (axis.lengthSquared() < EPSILON) {
            return axis.set(0.0F, -1.0F, 0.0F);
        }
        return axis.normalize();
    }

    static float safeAngle(
            boolean corrected,
            float endpointConfidence,
            float pivotConfidence,
            boolean supportStabilityImprovement
    ) {
        float angle = corrected ? INFERRED_PIVOT_ANGLE : SwingRange.MAX_ANGLE;
        if (endpointConfidence < MIN_PRINCIPAL_CONFIDENCE) {
            angle = Math.min(angle, 0.25F);
        }
        if (pivotConfidence < MIN_SUPPORT_CONFIDENCE) {
            angle = Math.min(angle, 0.18F);
        }
        return supportStabilityImprovement
                ? Math.min(angle, 0.12F)
                : angle;
    }
}
