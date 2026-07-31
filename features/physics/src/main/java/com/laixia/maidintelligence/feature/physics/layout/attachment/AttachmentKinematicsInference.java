package com.laixia.maidintelligence.feature.physics.layout.attachment;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.BoneKinematics;
import org.joml.Vector3f;

/**
 * Narrow cross-package entry point for attachment-frame inference.
 */
public final class AttachmentKinematicsInference {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float MIN_LEVER_PIXELS = 1.0F;
    private static final float EPSILON = 1.0E-6F;

    private AttachmentKinematicsInference() {
    }

    public static boolean hasDistributedGeometry(BoneModelSnapshot.Bone bone) {
        return BoneMeshPartition.measure(
                bone.geometry().cubes()
        ).distributedWithoutDominantCluster();
    }

    public static BoneKinematics.Metrics measure(
            BoneModelSnapshot.Bone bone,
            BoneModelSnapshot.Bone parent,
            BoneModelSnapshot.Bone nearestSolidAncestor,
            PhysicsBoneSelectionPlan.PartType type,
            PhysicsBoneSelectionPlan.StructureRole structureRole,
            PhysicsBoneSelectionPlan.ChainSegment chainSegment,
            BoneModelSnapshot.Bone nextChainBone
    ) {
        Vector3f authoredPivot = pivotOf(bone);
        BoneMeshPartition partition =
                BoneMeshPartition.measure(bone.geometry().cubes());
        BoneMeshMetrics fullMesh = partition.full();
        if (fullMesh.empty()) {
            return new BoneKinematics.Metrics(
                    new Vector3f(0.0F, -1.0F, 0.0F),
                    MIN_LEVER_PIXELS,
                    MIN_LEVER_PIXELS,
                    0.8F,
                    authoredPivot,
                    new Vector3f(authoredPivot),
                    0.0F,
                    0.0F,
                    Float.POSITIVE_INFINITY,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
            );
        }
        BoneMeshMetrics frameMesh =
                type == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                        || chainSegment.count() > 1
                        || structureRole
                        == PhysicsBoneSelectionPlan.StructureRole
                        .DANGLING_ACCESSORY
                        || structureRole
                        == PhysicsBoneSelectionPlan.StructureRole.NONE
                        ? fullMesh
                        : partition.frame();
        BoneAttachmentFrame frame = BoneAttachmentFrame.resolve(
                bone,
                parent,
                nearestSolidAncestor,
                type,
                structureRole,
                chainSegment,
                frameMesh,
                fullMesh
        );
        float leverArm = Math.max(
                fullMesh.maximumDistanceTo(frame.effectivePivot())
                        * PIXELS_PER_BLOCK,
                MIN_LEVER_PIXELS
        );
        Vector3f axis = frame.axis();
        boolean polarityFlipped = false;
        if (nextChainBone == null) {
            polarityFlipped = BoneAxisPolarity.alignWithVisibleMass(
                    axis,
                    fullMesh,
                    frame.effectivePivot()
            );
        }
        BoneMeshMetrics segmentMesh = polarityFlipped ? fullMesh : frameMesh;
        float segmentLength =
                type == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                        ? leverArm
                        : Math.max(
                        segmentMesh.maximumProjectionFrom(
                                frame.effectivePivot(),
                                axis
                        ) * PIXELS_PER_BLOCK,
                        MIN_LEVER_PIXELS
                );
        if (nextChainBone != null) {
            Vector3f joint = pivotOf(nextChainBone)
                    .sub(frame.effectivePivot());
            float jointLength = joint.length();
            if (jointLength > EPSILON) {
                axis.set(joint).mul(1.0F / jointLength);
                segmentLength = Math.max(
                        jointLength * PIXELS_PER_BLOCK,
                        MIN_LEVER_PIXELS
                );
            }
        }
        return new BoneKinematics.Metrics(
                axis,
                leverArm,
                segmentLength,
                frame.safeAngle(),
                authoredPivot,
                frame.effectivePivot(),
                frame.supportConfidence(),
                frame.contactConfidence(),
                frame.pivotScore(),
                frame.supportStabilityCorrected(),
                frame.supportStabilityPreserved(),
                frame.attachmentLeverCorrected(),
                frame.supportStabilityUnsupported(),
                polarityFlipped,
                partition.usesDominantCluster()
                        && frameMesh == partition.frame()
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
