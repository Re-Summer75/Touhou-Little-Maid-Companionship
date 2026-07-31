package com.laixia.maidintelligence.feature.physics.layout;


import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.attachment.AttachmentKinematicsInference;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Derives a safe rotation centre from baked geometry. Bedrock models often
 * contain decorative bones whose authored pivot is nowhere near their cubes.
 */
public final class BoneKinematics {
    private static final float EPSILON = 1.0E-6F;

    private BoneKinematics() {
    }

    public static boolean hasDistributedGeometry(BoneModelSnapshot.Bone bone) {
        return AttachmentKinematicsInference.hasDistributedGeometry(bone);
    }

    public static Metrics measure(
            BoneModelSnapshot.Bone bone,
            BoneModelSnapshot.Bone parent,
            PhysicsBoneSelectionPlan.PartType type
    ) {
        BoneModelSnapshot.Bone solidAncestor = parent != null
                && parent.geometry().cubes().getCubeCount() > 0
                ? parent
                : null;
        return measure(bone, parent, solidAncestor, type);
    }

    public static Metrics measure(
            BoneModelSnapshot.Bone bone,
            BoneModelSnapshot.Bone parent,
            BoneModelSnapshot.Bone nearestSolidAncestor,
            PhysicsBoneSelectionPlan.PartType type
    ) {
        return measure(
                bone,
                parent,
                nearestSolidAncestor,
                type,
                PhysicsBoneSelectionPlan.StructureRole.NONE,
                PhysicsBoneSelectionPlan.ChainSegment.none(),
                null
        );
    }

    public static Metrics measure(
            BoneModelSnapshot.Bone bone,
            BoneModelSnapshot.Bone parent,
            BoneModelSnapshot.Bone nearestSolidAncestor,
            PhysicsBoneSelectionPlan.PartType type,
            PhysicsBoneSelectionPlan.StructureRole structureRole
    ) {
        return measure(
                bone,
                parent,
                nearestSolidAncestor,
                type,
                structureRole,
                PhysicsBoneSelectionPlan.ChainSegment.none(),
                null
        );
    }

    public static Metrics measure(
            BoneModelSnapshot.Bone bone,
            BoneModelSnapshot.Bone parent,
            BoneModelSnapshot.Bone nearestSolidAncestor,
            PhysicsBoneSelectionPlan.PartType type,
            PhysicsBoneSelectionPlan.StructureRole structureRole,
            PhysicsBoneSelectionPlan.ChainSegment chainSegment,
            BoneModelSnapshot.Bone nextChainBone
    ) {
        return AttachmentKinematicsInference.measure(
                bone,
                parent,
                nearestSolidAncestor,
                type,
                structureRole,
                chainSegment,
                nextChainBone
        );
    }

    public static final class Metrics {
        private final Vector3f axis;
        private final float leverArm;
        private final float segmentLength;
        private final float safeAngle;
        private final Vector3f authoredPivot;
        private final Vector3f effectivePivot;
        private final Vector3f pivotDelta;
        private final boolean compensatesPivot;
        private final float supportConfidence;
        private final float contactConfidence;
        private final float pivotScore;
        private final boolean supportStabilityPivotCorrected;
        private final boolean supportStabilityPivotPreserved;
        private final boolean attachmentLeverPivotCorrected;
        private final boolean supportStabilityUnsupported;
        private final boolean axisPolarityCorrected;
        private final boolean usesDominantCluster;

        public Metrics(
                Vector3f axis,
                float leverArm,
                float segmentLength,
                float safeAngle,
                Vector3f authoredPivot,
                Vector3f effectivePivot,
                float supportConfidence,
                float contactConfidence,
                float pivotScore,
                boolean supportStabilityPivotCorrected,
                boolean supportStabilityPivotPreserved,
                boolean attachmentLeverPivotCorrected,
                boolean supportStabilityUnsupported,
                boolean axisPolarityCorrected,
                boolean usesDominantCluster
        ) {
            this.axis = new Vector3f(axis);
            this.leverArm = leverArm;
            this.segmentLength = segmentLength;
            this.safeAngle = safeAngle;
            this.authoredPivot = new Vector3f(authoredPivot);
            this.effectivePivot = new Vector3f(effectivePivot);
            this.pivotDelta = new Vector3f(effectivePivot).sub(authoredPivot);
            this.compensatesPivot = pivotDelta.lengthSquared() > EPSILON;
            this.supportConfidence = supportConfidence;
            this.contactConfidence = contactConfidence;
            this.pivotScore = pivotScore;
            this.supportStabilityPivotCorrected =
                    supportStabilityPivotCorrected;
            this.supportStabilityPivotPreserved =
                    supportStabilityPivotPreserved;
            this.attachmentLeverPivotCorrected =
                    attachmentLeverPivotCorrected;
            this.supportStabilityUnsupported =
                    supportStabilityUnsupported;
            this.axisPolarityCorrected = axisPolarityCorrected;
            this.usesDominantCluster = usesDominantCluster;
        }

        public Vector3f axis() {
            return new Vector3f(axis);
        }

        public Vector3f axisInto(Vector3f output) {
            return output.set(axis);
        }

        public float leverArm() {
            return leverArm;
        }

        public float segmentLength() {
            return segmentLength;
        }

        public float safeAngle() {
            return safeAngle;
        }

        public Vector3f authoredPivot() {
            return new Vector3f(authoredPivot);
        }

        public Vector3f effectivePivot() {
            return new Vector3f(effectivePivot);
        }

        public boolean compensatesPivot() {
            return compensatesPivot;
        }

        public float supportConfidence() {
            return supportConfidence;
        }

        public float contactConfidence() {
            return contactConfidence;
        }

        public float pivotScore() {
            return pivotScore;
        }

        public boolean supportStabilityPivotCorrected() {
            return supportStabilityPivotCorrected;
        }

        public boolean supportStabilityPivotPreserved() {
            return supportStabilityPivotPreserved;
        }

        public boolean attachmentLeverPivotCorrected() {
            return attachmentLeverPivotCorrected;
        }

        public boolean supportStabilityUnsupported() {
            return supportStabilityUnsupported;
        }

        public boolean axisPolarityCorrected() {
            return axisPolarityCorrected;
        }

        public boolean usesDominantCluster() {
            return usesDominantCluster;
        }

        public Vector3f compensationOffset(Quaternionf physicalDelta) {
            return compensationOffsetInto(
                    physicalDelta,
                    new Vector3f(),
                    new Vector3f()
            );
        }

        public Vector3f compensationOffsetInto(
                Quaternionf physicalDelta,
                Vector3f output,
                Vector3f scratch
        ) {
            if (!compensatesPivot) {
                return output.zero();
            }
            physicalDelta.transform(pivotDelta, scratch);
            return output.set(
                    pivotDelta.x - scratch.x,
                    pivotDelta.y - scratch.y,
                    pivotDelta.z - scratch.z
            );
        }

        public Vector3f poseCompensationOffsetInto(
                Quaternionf animationRotation,
                Quaternionf physicalRotation,
                Vector3f output,
                Vector3f scratch
        ) {
            return poseCompensationOffsetInto(
                    animationRotation,
                    physicalRotation,
                    1.0F,
                    1.0F,
                    1.0F,
                    output,
                    scratch
            );
        }

        public Vector3f poseCompensationOffsetInto(
                Quaternionf animationRotation,
                Quaternionf physicalRotation,
                float scaleX,
                float scaleY,
                float scaleZ,
                Vector3f output,
                Vector3f scratch
        ) {
            if (!compensatesPivot) {
                return output.zero();
            }
            output.set(
                    pivotDelta.x * scaleX,
                    pivotDelta.y * scaleY,
                    pivotDelta.z * scaleZ
            );
            animationRotation.transform(output);
            float animationX = output.x;
            float animationY = output.y;
            float animationZ = output.z;
            scratch.set(
                    pivotDelta.x * scaleX,
                    pivotDelta.y * scaleY,
                    pivotDelta.z * scaleZ
            );
            physicalRotation.transform(scratch);
            return output.set(
                    animationX - scratch.x,
                    animationY - scratch.y,
                    animationZ - scratch.z
            );
        }
    }

}
