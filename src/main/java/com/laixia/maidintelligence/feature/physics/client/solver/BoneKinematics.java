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
    private static final float MIN_PIVOT_TOLERANCE = 4.0F / PIXELS_PER_BLOCK;
    private static final float EPSILON = 1.0E-6F;

    private BoneKinematics() {
    }

    public static Metrics measure(
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            PhysicsBoneSelectionPlan.PartType type
    ) {
        Vector3f authoredPivot = pivotOf(bone);
        BoneMeshMetrics mesh = BoneMeshMetrics.measure(bone.geoBone().cubes());
        if (mesh.empty()) {
            return new Metrics(
                    new Vector3f(0.0F, -1.0F, 0.0F),
                    MIN_LEVER_PIXELS,
                    0.8F,
                    authoredPivot,
                    new Vector3f(authoredPivot)
            );
        }

        float tolerance = Math.max(
                MIN_PIVOT_TOLERANCE,
                mesh.diagonal() * 0.50F
        );
        boolean detached = mesh.distanceTo(authoredPivot) > tolerance;
        Vector3f effectivePivot;
        float safeAngle;
        if (type == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL) {
            // A skullcap should rock around itself, not orbit an edge pivot.
            effectivePivot = new Vector3f(mesh.centroid());
            safeAngle = 0.35F;
        } else if (detached) {
            if (hangsVertically(type)) {
                effectivePivot = mesh.topCenter();
            } else {
                Vector3f attachmentHint = parent == null
                        ? mesh.centroid()
                        : pivotOf(parent);
                effectivePivot = mesh.closestPoint(attachmentHint);
            }
            safeAngle = 0.30F;
        } else {
            effectivePivot = new Vector3f(authoredPivot);
            safeAngle = 0.8F;
        }

        Vector3f axis = type == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                ? new Vector3f(0.0F, -1.0F, 0.0F)
                : new Vector3f(mesh.centroid()).sub(effectivePivot);
        if (axis.lengthSquared() < EPSILON) {
            axis.set(0.0F, -1.0F, 0.0F);
        } else {
            axis.normalize();
        }
        return new Metrics(
                axis,
                Math.max(
                        mesh.maximumDistanceTo(effectivePivot) * PIXELS_PER_BLOCK,
                        MIN_LEVER_PIXELS
                ),
                safeAngle,
                authoredPivot,
                effectivePivot
        );
    }

    private static Vector3f pivotOf(AnimatedGeoBone bone) {
        return new Vector3f(
                bone.getPivotX() / PIXELS_PER_BLOCK,
                bone.getPivotY() / PIXELS_PER_BLOCK,
                bone.getPivotZ() / PIXELS_PER_BLOCK
        );
    }

    private static boolean hangsVertically(
            PhysicsBoneSelectionPlan.PartType type
    ) {
        return type == PhysicsBoneSelectionPlan.PartType.HAIR
                || type == PhysicsBoneSelectionPlan.PartType.SKIRT
                || type == PhysicsBoneSelectionPlan.PartType.CAPE;
    }

    public static final class Metrics {
        private final Vector3f axis;
        private final float leverArm;
        private final float safeAngle;
        private final Vector3f authoredPivot;
        private final Vector3f effectivePivot;
        private final Vector3f pivotDelta;
        private final boolean compensatesPivot;

        public Metrics(
                Vector3f axis,
                float leverArm,
                float safeAngle,
                Vector3f authoredPivot,
                Vector3f effectivePivot
        ) {
            this.axis = new Vector3f(axis);
            this.leverArm = leverArm;
            this.safeAngle = safeAngle;
            this.authoredPivot = new Vector3f(authoredPivot);
            this.effectivePivot = new Vector3f(effectivePivot);
            this.pivotDelta = new Vector3f(effectivePivot).sub(authoredPivot);
            this.compensatesPivot = pivotDelta.lengthSquared() > EPSILON;
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
    }

}
