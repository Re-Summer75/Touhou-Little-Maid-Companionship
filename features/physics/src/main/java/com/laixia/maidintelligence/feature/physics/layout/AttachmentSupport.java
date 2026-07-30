package com.laixia.maidintelligence.feature.physics.layout;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import org.joml.Vector3f;

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
