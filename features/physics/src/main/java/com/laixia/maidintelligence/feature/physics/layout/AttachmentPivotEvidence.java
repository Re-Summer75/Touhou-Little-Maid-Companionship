package com.laixia.maidintelligence.feature.physics.layout;


import org.joml.Vector3f;

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
