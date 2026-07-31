package com.laixia.maidintelligence.feature.physics.layout.attachment;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.discovery.classifier.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import org.joml.Vector3f;

/**
 * Resolves the proximal mesh endpoint without trusting author pivots.
 */
record BoneAttachmentFrame(
        Vector3f effectivePivot,
        Vector3f axis,
        float safeAngle,
        float supportConfidence,
        float contactConfidence,
        float pivotScore,
        boolean supportStabilityCorrected,
        boolean supportStabilityPreserved,
        boolean attachmentLeverCorrected,
        boolean supportStabilityUnsupported
) {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float MIN_PRINCIPAL_CONFIDENCE = 0.48F;

    static BoneAttachmentFrame resolve(
            BoneModelSnapshot.Bone bone,
            BoneModelSnapshot.Bone parent,
            BoneModelSnapshot.Bone nearestSolidAncestor,
            PhysicsBoneSelectionPlan.PartType type,
            PhysicsBoneSelectionPlan.StructureRole structureRole,
            PhysicsBoneSelectionPlan.ChainSegment chainSegment,
            BoneMeshMetrics mesh,
            BoneMeshMetrics loadMesh
    ) {
        Vector3f authoredPivot = pivotOf(bone);
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
        proximal.set(mesh.closestPoint(proximal));
        distal.set(mesh.closestPoint(distal));
        PivotInferenceResult inference = inferPivot(
                authoredPivot,
                proximal,
                mesh,
                support,
                endpoints.supportConfidence()
        );
        float contactConfidence = inference.contactBased()
                ? inference.confidence()
                : 0.0F;
        float pivotConfidence = inference.contactBased()
                ? contactConfidence
                : endpoints.supportConfidence();
        float supportConfidence = Math.max(
                endpoints.supportConfidence(),
                contactConfidence
        );
        if (type == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL) {
            return HeadShellAttachmentFrame.resolve(
                    mesh,
                    inference,
                    supportConfidence,
                    contactConfidence
            );
        }

        AttachmentPivotResolution pivot =
                AttachmentPivotResolution.resolve(
                        type,
                        structureRole,
                        chainSegment,
                authoredPivot,
                        inference,
                        mesh,
                        loadMesh,
                        endpoints,
                        support,
                        pivotConfidence,
                        contactConfidence
                );
        Vector3f effectivePivot = pivot.pivot();
        Vector3f axis = AttachmentFrameGeometry.axis(
                mesh,
                endpoints,
                proximal,
                distal,
                effectivePivot
        );
        float safeAngle = AttachmentFrameGeometry.safeAngle(
                pivot.corrected(),
                endpoints.confidence(),
                pivotConfidence,
                pivot.strictAngleLimit()
        );
        return new BoneAttachmentFrame(
                effectivePivot,
                axis,
                safeAngle,
                supportConfidence,
                contactConfidence,
                inference.score(),
                pivot.supportStabilityCorrected(),
                pivot.supportStabilityPreserved(),
                pivot.attachmentLeverCorrected(),
                pivot.supportStabilityUnsupported()
        );
    }

    private static PivotInferenceResult inferPivot(
            Vector3f authoredPivot,
            Vector3f fallbackPivot,
            BoneMeshMetrics mesh,
            AttachmentSupport support,
            float fallbackConfidence
    ) {
        if (support.body() == null) {
            return PivotInferenceResult.fallback(
                    fallbackPivot,
                    fallbackConfidence
            );
        }
        AttachmentContactPatch patch = AttachmentContactAnalyzer.analyze(
                mesh,
                support.body()
        );
        return VirtualPivotOptimizer.optimize(
                authoredPivot,
                fallbackPivot,
                mesh,
                support.body(),
                patch
        );
    }

    private static Vector3f pivotOf(BoneModelSnapshot.Bone bone) {
        return new Vector3f(
                bone.getPivotX() / PIXELS_PER_BLOCK,
                bone.getPivotY() / PIXELS_PER_BLOCK,
                bone.getPivotZ() / PIXELS_PER_BLOCK
        );
    }

}

/**
 * Scores a possible attachment point against its supporting geometry and
 * gravity. Authored hierarchy pivots are deliberately only secondary evidence.
 */
record AttachmentSupport(
        BoneMeshMetrics body,
        Vector3f hierarchyHint,
        float hierarchyWeight,
        GravityPreference gravity,
        float gravityStrength,
        float scale
) {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float MIN_SCALE = 0.05F;

    enum GravityPreference {
        NONE,
        UPPER,
        LOWER
    }

    static AttachmentSupport resolve(
            BoneModelSnapshot.Bone bone,
            BoneModelSnapshot.Bone parent,
            BoneModelSnapshot.Bone nearestSolidAncestor,
            PhysicsBoneSelectionPlan.PartType type,
            BoneMeshMetrics mesh
    ) {
        BoneMeshMetrics body = supportBody(nearestSolidAncestor);
        Vector3f hierarchyHint = null;
        float hierarchyWeight = 0.0F;
        if (parent != null && !hasGeometry(parent)) {
            Vector3f parentPivot = pivotOf(parent);
            float usefulDistance = Math.max(
                    4.0F / PIXELS_PER_BLOCK,
                    mesh.diagonal()
            );
            if (mesh.distanceTo(parentPivot) <= usefulDistance) {
                hierarchyHint = parentPivot;
                hierarchyWeight = 0.70F;
            }
        }
        if (hierarchyHint == null && body == null && parent != null) {
            hierarchyHint = pivotOf(parent);
            hierarchyWeight = 0.20F;
        }

        float scale = Math.max(MIN_SCALE, mesh.diagonal());
        float height = Math.max(0.0F, mesh.max().y - mesh.min().y);
        float gravityStrength = Math.min(
                1.0F,
                height / Math.max(MIN_SCALE, scale * 0.35F)
        );
        boolean fringe = PhysicsBoneClassifier.isFringeHint(bone.getName())
                || parent != null
                && PhysicsBoneClassifier.isFringeHint(parent.getName());
        GravityPreference gravity = gravityPreference(
                type,
                mesh,
                body,
                fringe
        );
        return new AttachmentSupport(
                body,
                hierarchyHint,
                hierarchyWeight,
                gravity,
                gravityStrength,
                scale
        );
    }

    float score(Vector3f point, Vector3f center) {
        float score = 0.0F;
        if (body != null) {
            score += body.distanceTo(point) / scale * 2.25F;
        }
        if (hierarchyHint != null) {
            score += point.distance(hierarchyHint)
                    / scale * hierarchyWeight;
        }
        float vertical = clamp(
                (point.y - center.y) / scale,
                -2.0F,
                2.0F
        );
        if (gravity == GravityPreference.UPPER) {
            score -= vertical * gravityStrength * 1.35F;
        } else if (gravity == GravityPreference.LOWER) {
            score += vertical * gravityStrength * 1.35F;
        }
        return score;
    }

    Vector3f target(BoneMeshMetrics mesh) {
        if (hierarchyHint != null) {
            return new Vector3f(hierarchyHint);
        }
        return body == null
                ? null
                : body.closestPoint(mesh.centroid());
    }

    private static GravityPreference gravityPreference(
            PhysicsBoneSelectionPlan.PartType type,
            BoneMeshMetrics mesh,
            BoneMeshMetrics body,
            boolean fringe
    ) {
        if (!hangsUnderGravity(type)) {
            return GravityPreference.NONE;
        }
        if (body != null) {
            float height = Math.max(0.0F, mesh.max().y - mesh.min().y);
            float margin = Math.max(0.025F, height * 0.08F);
            float verticalOverlap = Math.min(mesh.max().y, body.max().y)
                    - Math.max(mesh.min().y, body.min().y);
            if (fringe && verticalOverlap >= -margin) {
                return GravityPreference.UPPER;
            }
            float topDistance = body.distanceTo(mesh.topCenter());
            float bottomDistance = body.distanceTo(mesh.bottomCenter());
            if (bottomDistance + margin < topDistance) {
                return GravityPreference.LOWER;
            }
        }
        return GravityPreference.UPPER;
    }

    private static BoneMeshMetrics supportBody(
            BoneModelSnapshot.Bone nearestSolidAncestor
    ) {
        if (nearestSolidAncestor == null) {
            return null;
        }
        BoneMeshMetrics metrics = BoneMeshMetrics.measure(
                nearestSolidAncestor.geometry().cubes()
        );
        return metrics.empty() ? null : metrics;
    }

    private static boolean hangsUnderGravity(
            PhysicsBoneSelectionPlan.PartType type
    ) {
        return type == PhysicsBoneSelectionPlan.PartType.HAIR
                || type == PhysicsBoneSelectionPlan.PartType.SKIRT
                || type == PhysicsBoneSelectionPlan.PartType.CAPE;
    }

    private static boolean hasGeometry(BoneModelSnapshot.Bone bone) {
        return bone.geometry().cubes().getCubeCount() > 0;
    }

    private static Vector3f pivotOf(BoneModelSnapshot.Bone bone) {
        return new Vector3f(
                bone.getPivotX() / PIXELS_PER_BLOCK,
                bone.getPivotY() / PIXELS_PER_BLOCK,
                bone.getPivotZ() / PIXELS_PER_BLOCK
        );
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

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

/**
 * Derives the normalized attachment axis and its geometric safety limit.
 */
final class AttachmentFrameGeometry {
    private static final float MAX_SWING_ANGLE = 1.05F;
    private static final float MIN_PRINCIPAL_CONFIDENCE = 0.48F;
    private static final float MIN_SUPPORT_CONFIDENCE = 0.20F;
    /**
     * Ceiling for a segment whose rotation centre had to be inferred. Turning
     * far around a guessed pivot moves the mesh somewhere the author never
     * placed it, so the error grows with the angle and this stays well under
     * the solver's maximum swing angle.
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
        float angle = corrected ? INFERRED_PIVOT_ANGLE : MAX_SWING_ANGLE;
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
