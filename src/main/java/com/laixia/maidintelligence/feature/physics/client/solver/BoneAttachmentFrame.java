package com.laixia.maidintelligence.feature.physics.client.solver;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import org.joml.Vector3f;

/**
 * Resolves the proximal mesh endpoint without trusting author pivots.
 */
record BoneAttachmentFrame(
        Vector3f effectivePivot,
        Vector3f axis,
        float safeAngle,
        float supportConfidence
) {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float MIN_PIVOT_TOLERANCE =
            4.0F / PIXELS_PER_BLOCK;
    private static final float MIN_PRINCIPAL_CONFIDENCE = 0.48F;
    private static final float MIN_SUPPORT_CONFIDENCE = 0.20F;
    private static final float EPSILON = 1.0E-6F;

    static BoneAttachmentFrame resolve(
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            AnimatedGeoBone nearestSolidAncestor,
            PhysicsBoneSelectionPlan.PartType type,
            BoneMeshMetrics mesh
    ) {
        Vector3f authoredPivot = pivotOf(bone);
        if (type == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL) {
            return new BoneAttachmentFrame(
                    new Vector3f(mesh.centroid()),
                    new Vector3f(0.0F, -1.0F, 0.0F),
                    0.35F,
                    1.0F
            );
        }

        AttachmentSupport support = AttachmentSupport.resolve(
                bone,
                parent,
                nearestSolidAncestor,
                type,
                mesh
        );
        Vector3f attachmentHint = support.target(mesh);
        MeshAttachmentAxis endpoints = mesh.attachmentAxis(support);
        Vector3f proximal = endpoints.proximal();
        Vector3f distal = endpoints.distal();
        if (endpoints.confidence() < MIN_PRINCIPAL_CONFIDENCE
                && attachmentHint != null) {
            Vector3f closest = mesh.closestPoint(attachmentHint);
            if (closest.distance(mesh.centroid())
                    > Math.max(0.02F, mesh.diagonal() * 0.12F)
                    && support.score(closest, mesh.centroid()) + 0.03F
                    < support.score(proximal, mesh.centroid())) {
                proximal.set(closest);
            }
        }

        float tolerance = Math.max(
                MIN_PIVOT_TOLERANCE,
                mesh.diagonal() * 0.50F
        );
        boolean detached = mesh.distanceTo(authoredPivot) > tolerance;
        boolean reversed = pointsFromDistalEnd(
                authoredPivot,
                proximal,
                distal,
                endpoints.confidence(),
                endpoints.length(),
                endpoints.supportConfidence()
        );
        boolean misplaced = originFarFromAttachment(
                authoredPivot,
                proximal,
                endpoints.confidence(),
                endpoints.length(),
                endpoints.supportConfidence(),
                mesh,
                support
        );
        boolean corrected = detached || reversed || misplaced;
        Vector3f effectivePivot = corrected
                ? proximal
                : new Vector3f(authoredPivot);

        Vector3f directedAxis = new Vector3f(distal).sub(proximal);
        Vector3f axis = new Vector3f(mesh.centroid())
                .sub(effectivePivot);
        float minimumAxis = Math.max(
                0.02F,
                endpoints.length() * 0.08F
        );
        if (axis.lengthSquared() < minimumAxis * minimumAxis) {
            axis.set(directedAxis);
        } else if (endpoints.supportConfidence()
                >= MIN_SUPPORT_CONFIDENCE
                && directedAxis.lengthSquared() > EPSILON
                && axis.dot(directedAxis) < 0.0F) {
            axis.negate();
        }
        if (axis.lengthSquared() < EPSILON) {
            axis.set(0.0F, -1.0F, 0.0F);
        } else {
            axis.normalize();
        }
        float safeAngle = corrected ? 0.30F : 0.80F;
        if (endpoints.confidence() < MIN_PRINCIPAL_CONFIDENCE) {
            safeAngle = Math.min(safeAngle, 0.25F);
        }
        if (endpoints.supportConfidence() < MIN_SUPPORT_CONFIDENCE) {
            safeAngle = Math.min(safeAngle, 0.18F);
        }
        return new BoneAttachmentFrame(
                effectivePivot,
                axis,
                safeAngle,
                endpoints.supportConfidence()
        );
    }

    private static boolean pointsFromDistalEnd(
            Vector3f authoredPivot,
            Vector3f proximal,
            Vector3f distal,
            float confidence,
            float axisLength,
            float supportConfidence
    ) {
        if (confidence < MIN_PRINCIPAL_CONFIDENCE
                || supportConfidence < MIN_SUPPORT_CONFIDENCE) {
            return false;
        }
        float margin = Math.max(
                0.04F,
                axisLength * 0.12F
        );
        return authoredPivot.distance(distal) + margin
                < authoredPivot.distance(proximal);
    }

    private static boolean originFarFromAttachment(
            Vector3f authoredPivot,
            Vector3f proximal,
            float confidence,
            float axisLength,
            float supportConfidence,
            BoneMeshMetrics mesh,
            AttachmentSupport support
    ) {
        if (confidence < MIN_PRINCIPAL_CONFIDENCE
                || supportConfidence < MIN_SUPPORT_CONFIDENCE) {
            return false;
        }
        float diagonal = mesh.diagonal();
        float tolerance = Math.max(
                0.5F / PIXELS_PER_BLOCK,
                Math.min(diagonal * 0.12F, axisLength * 0.20F)
        );
        float scoreMargin = Math.max(
                0.03F,
                tolerance / Math.max(0.05F, diagonal) * 0.25F
        );
        return authoredPivot.distance(proximal) > tolerance
                && support.score(proximal, mesh.centroid()) + scoreMargin
                < support.score(authoredPivot, mesh.centroid());
    }

    private static Vector3f pivotOf(AnimatedGeoBone bone) {
        return new Vector3f(
                bone.getPivotX() / PIXELS_PER_BLOCK,
                bone.getPivotY() / PIXELS_PER_BLOCK,
                bone.getPivotZ() / PIXELS_PER_BLOCK
        );
    }

}
