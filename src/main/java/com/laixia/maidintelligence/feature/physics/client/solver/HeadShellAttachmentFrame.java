package com.laixia.maidintelligence.feature.physics.client.solver;

import org.joml.Vector3f;

/**
 * Uses head/support contact when available and centroid only as a fallback.
 */
final class HeadShellAttachmentFrame {
    private static final float MIN_CONTACT_CONFIDENCE = 0.15F;
    private static final float EPSILON = 1.0E-6F;

    private HeadShellAttachmentFrame() {
    }

    static BoneAttachmentFrame resolve(
            BoneMeshMetrics mesh,
            PivotInferenceResult inference,
            float supportConfidence,
            float contactConfidence
    ) {
        boolean useContact = inference.contactBased()
                && contactConfidence >= MIN_CONTACT_CONFIDENCE;
        Vector3f pivot = useContact
                ? new Vector3f(inference.pivot())
                : new Vector3f(mesh.centroid());
        Vector3f axis = new Vector3f(mesh.centroid()).sub(pivot);
        if (axis.lengthSquared() < EPSILON) {
            axis.set(0.0F, -1.0F, 0.0F);
        } else {
            axis.normalize();
        }
        return new BoneAttachmentFrame(
                pivot,
                axis,
                useContact ? 0.30F : 0.35F,
                supportConfidence,
                contactConfidence,
                inference.score(),
                false,
                false,
                false,
                false
        );
    }
}
