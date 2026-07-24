package com.laixia.maidintelligence.feature.physics.client.solver;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Derives a safe rotation centre from baked geometry. Bedrock models often
 * contain decorative bones whose authored pivot is nowhere near their cubes.
 */
public final class BoneKinematics {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float MIN_LEVER_PIXELS = 1.0F;
    private static final float EPSILON = 1.0E-6F;

    private BoneKinematics() {
    }

    public static Metrics measure(
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            PhysicsBoneSelectionPlan.PartType type
    ) {
        AnimatedGeoBone solidAncestor = parent != null
                && parent.geoBone().cubes().getCubeCount() > 0
                ? parent
                : null;
        return measure(bone, parent, solidAncestor, type);
    }

    public static Metrics measure(
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            AnimatedGeoBone nearestSolidAncestor,
            PhysicsBoneSelectionPlan.PartType type
    ) {
        return measure(
                bone,
                parent,
                nearestSolidAncestor,
                type,
                PhysicsBoneSelectionPlan.StructureRole.NONE
        );
    }

    public static Metrics measure(
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            AnimatedGeoBone nearestSolidAncestor,
            PhysicsBoneSelectionPlan.PartType type,
            PhysicsBoneSelectionPlan.StructureRole structureRole
    ) {
        Vector3f authoredPivot = pivotOf(bone);
        BoneMeshPartition partition =
                BoneMeshPartition.measure(bone.geoBone().cubes());
        BoneMeshMetrics fullMesh = partition.full();
        if (fullMesh.empty()) {
            return new Metrics(
                    new Vector3f(0.0F, -1.0F, 0.0F),
                    MIN_LEVER_PIXELS,
                    MIN_LEVER_PIXELS,
                    0.8F,
                    authoredPivot,
                    new Vector3f(authoredPivot),
                    0.0F,
                    false
            );
        }
        BoneMeshMetrics frameMesh =
                type == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                        || structureRole
                        == PhysicsBoneSelectionPlan.StructureRole.NONE
                        ? fullMesh
                        : partition.frame();
        BoneAttachmentFrame frame = BoneAttachmentFrame.resolve(
                bone,
                parent,
                nearestSolidAncestor,
                type,
                frameMesh
        );
        float leverArm = Math.max(
                fullMesh.maximumDistanceTo(frame.effectivePivot())
                        * PIXELS_PER_BLOCK,
                MIN_LEVER_PIXELS
        );
        float segmentLength =
                type == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                        ? leverArm
                        : Math.max(
                        frameMesh.maximumProjectionFrom(
                                frame.effectivePivot(),
                                frame.axis()
                        ) * PIXELS_PER_BLOCK,
                        MIN_LEVER_PIXELS
                );
        return new Metrics(
                frame.axis(),
                leverArm,
                segmentLength,
                frame.safeAngle(),
                authoredPivot,
                frame.effectivePivot(),
                frame.supportConfidence(),
                partition.usesDominantCluster()
                        && frameMesh == partition.frame()
        );
    }

    private static Vector3f pivotOf(AnimatedGeoBone bone) {
        return new Vector3f(
                bone.getPivotX() / PIXELS_PER_BLOCK,
                bone.getPivotY() / PIXELS_PER_BLOCK,
                bone.getPivotZ() / PIXELS_PER_BLOCK
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
        private final boolean usesDominantCluster;

        public Metrics(
                Vector3f axis,
                float leverArm,
                float segmentLength,
                float safeAngle,
                Vector3f authoredPivot,
                Vector3f effectivePivot,
                float supportConfidence,
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
