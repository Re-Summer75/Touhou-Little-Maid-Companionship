package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Test-only copy of the pre-optimization full-tree recursive solver. It is the
 * behavioral oracle for the iterative active-skeleton implementation.
 */
final class ReferenceSpringBoneSolver {
    private static final double STIFFNESS = 6.0D;
    private static final double GRAVITY_POWER = 0.9D;
    private static final double DRAG = 0.35D;
    private static final double INERTIA_GAIN = 7.0D;
    private static final double TURN_GAIN = 3.0D;

    /*
     * Copied on purpose rather than shared: this class exists to disagree with
     * the production solver when the production solver is wrong, which it
     * cannot do while reading the same constants. Keep the values in step with
     * SwingRange by hand.
     */
    private static final float MAX_ANGLE = 1.05F;
    private static final float MAX_DEFLECT_X = 0.6F;
    private static final float MAX_DEFLECT_Y = 0.6F;
    private static final float MAX_DEFLECT_Z = 0.6F;
    private static final float MAX_TIP_DISPLACEMENT = 4.5F;
    private static final float REFERENCE_DELTA_SECONDS = 1.0F / 60.0F;
    private static final double EPSILON = 1.0E-5D;

    private final AnimatedGeoModel model;
    private final PhysicsBoneSelectionPlan plan;
    private final Map<AnimatedGeoBone, BoneData> bones =
            new IdentityHashMap<>();

    ReferenceSpringBoneSolver(
            AnimatedGeoModel model,
            PhysicsBoneSelectionPlan plan
    ) {
        this.model = model;
        this.plan = plan;
    }

    void solve(
            Vector3f acceleration,
            float yawRate,
            float dt,
            boolean paused
    ) {
        MotionSignals motion = new MotionSignals(
                new Vector3f(acceleration),
                yawRate
        );
        Quaternionf root = new Quaternionf();
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            solve(bone, null, root, motion, dt, paused);
        }
    }

    private void solve(
            AnimatedGeoBone bone,
            AnimatedGeoBone parentBone,
            Quaternionf parentRenderedOrientation,
            MotionSignals motion,
            float dt,
            boolean paused
    ) {
        float rx = bone.getRotationX();
        float ry = bone.getRotationY();
        float rz = bone.getRotationZ();
        float px = bone.getPositionX();
        float py = bone.getPositionY();
        float pz = bone.getPositionZ();
        Quaternionf boneBaseOrientation = composeOrientation(
                parentRenderedOrientation,
                rx,
                ry,
                rz
        );

        BoneData data = dataFor(bone, parentBone, plan.decision(bone));
        if (data != null) {
            Vector3f restDir = boneBaseOrientation.transform(
                    new Vector3f(data.boneAxis)
            ).normalize();
            if (!data.initialized) {
                data.currentDir.set(restDir);
                data.prevDir.set(restDir);
                data.initialized = true;
            }

            if (!paused && dt > EPSILON) {
                Vector3f next = new Vector3f(data.currentDir);
                PhysicsBoneSelectionPlan.SpringProfile profile = data.profile;
                float drag = Mth.clamp(
                        (float) DRAG * profile.dragScale(),
                        0.0F,
                        0.95F
                );
                float retention = dragRetention(drag, dt);
                float stepRatio = data.previousDt > EPSILON
                        ? Mth.clamp(dt / data.previousDt, 0.25F, 4.0F)
                        : 1.0F;
                next.add(
                        (data.currentDir.x() - data.prevDir.x())
                                * retention * stepRatio,
                        (data.currentDir.y() - data.prevDir.y())
                                * retention * stepRatio,
                        (data.currentDir.z() - data.prevDir.z())
                                * retention * stepRatio
                );
                float stiffness = (float) (
                        STIFFNESS
                                * profile.stiffnessScale()
                                / Math.max(1.0F, profile.massScale())
                                * dt
                );
                next.add(
                        restDir.x() * stiffness,
                        restDir.y() * stiffness,
                        restDir.z() * stiffness
                );
                Vector3f externalForce = motion.force(
                        profile,
                        restDir,
                        data.currentDir
                );
                next.add(
                        externalForce.x() * dt,
                        externalForce.y() * dt,
                        externalForce.z() * dt
                );
                if (next.lengthSquared() > EPSILON) {
                    next.normalize();
                    data.prevDir.set(data.currentDir);
                    data.currentDir.set(next);
                }
                data.previousDt = dt;
            }
            applyDeflection(
                    bone,
                    boneBaseOrientation,
                    data,
                    rx,
                    ry,
                    rz,
                    px,
                    py,
                    pz
            );
        }

        Quaternionf renderedOrientation = composeOrientation(
                parentRenderedOrientation,
                bone.getRotationX(),
                bone.getRotationY(),
                bone.getRotationZ()
        );
        for (AnimatedGeoBone child : bone.children()) {
            solve(
                    child,
                    bone,
                    renderedOrientation,
                    motion,
                    dt,
                    paused
            );
        }
    }

    private void applyDeflection(
            AnimatedGeoBone bone,
            Quaternionf animationOrientation,
            BoneData data,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz
    ) {
        Vector3f localDir = animationOrientation.transformInverse(
                new Vector3f(data.currentDir)
        );
        if (localDir.lengthSquared() < EPSILON) {
            return;
        }
        localDir.normalize();
        Vector3f axis = data.boneAxis.cross(localDir, new Vector3f());
        float sin = axis.length();
        if (sin < EPSILON) {
            return;
        }
        axis.div(sin);
        float dot = Math.max(
                -1.0F,
                Math.min(1.0F, data.boneAxis.dot(localDir))
        );
        float cap = Math.min(
                Math.min(MAX_ANGLE, data.safeAngle)
                        * data.profile.angleScale(),
                MAX_TIP_DISPLACEMENT
                        * data.profile.tipDisplacementScale()
                        / data.leverArm
        );
        float angle = Math.min((float) Math.atan2(sin, dot), cap);
        float dRx = clampAbs(
                axis.x() * angle,
                MAX_DEFLECT_X * data.profile.angleScale()
        );
        float dRy = clampAbs(
                axis.y() * angle,
                MAX_DEFLECT_Y * data.profile.angleScale()
        );
        float dRz = clampAbs(
                axis.z() * angle,
                MAX_DEFLECT_Z * data.profile.angleScale()
        );
        bone.setRotationX(rx + dRx);
        bone.setRotationY(ry + dRy);
        bone.setRotationZ(rz + dRz);
        compensatePivot(bone, data, rx, ry, rz, px, py, pz);
    }

    private void compensatePivot(
            AnimatedGeoBone bone,
            BoneData data,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz
    ) {
        if (!data.kinematics.compensatesPivot()) {
            return;
        }
        Quaternionf animationRotation =
                new Quaternionf().rotateZYX(rz, ry, rx);
        Quaternionf physicalDelta = new Quaternionf().rotateZYX(
                bone.getRotationZ(),
                bone.getRotationY(),
                bone.getRotationX()
        ).mul(animationRotation.invert());
        Vector3f pivotDelta = data.kinematics.effectivePivot()
                .sub(data.kinematics.authoredPivot());
        Vector3f offset = new Vector3f(pivotDelta).sub(
                physicalDelta.transform(new Vector3f(pivotDelta))
        );
        bone.setPositionX(px - offset.x * 16.0F);
        bone.setPositionY(py + offset.y * 16.0F);
        bone.setPositionZ(pz + offset.z * 16.0F);
    }

    private BoneData dataFor(
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            PhysicsBoneSelectionPlan.Decision decision
    ) {
        if (!decision.driven()) {
            return null;
        }
        BoneData cached = bones.get(bone);
        if (cached != null) {
            return cached;
        }
        BoneKinematics.Metrics kinematics = plan.kinematics(bone);
        if (kinematics == null) {
            kinematics = BoneKinematics.measure(
                    bone,
                    parent,
                    decision.type()
            );
        }
        BoneData created = new BoneData(kinematics, decision.profile());
        bones.put(bone, created);
        return created;
    }

    boolean copyCurrentDirection(
            AnimatedGeoBone bone,
            Vector3f output
    ) {
        BoneData data = bones.get(bone);
        if (data == null || !data.initialized) {
            output.zero();
            return false;
        }
        output.set(data.currentDir);
        return true;
    }

    private static float clampAbs(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private static float dragRetention(float drag, float dt) {
        float clampedDrag = Mth.clamp(drag, 0.0F, 0.95F);
        if (dt <= 0.0F) {
            return 1.0F;
        }
        return (float) Math.pow(
                1.0F - clampedDrag,
                dt / REFERENCE_DELTA_SECONDS
        );
    }

    private static Quaternionf composeOrientation(
            Quaternionf parent,
            float rotationX,
            float rotationY,
            float rotationZ
    ) {
        return new Quaternionf(parent).mul(
                new Quaternionf().rotateZYX(
                        rotationZ,
                        rotationY,
                        rotationX
                )
        );
    }

    private record MotionSignals(Vector3f acceleration, float yawRate) {
        /**
         * @param restDir    the authored pose, which gravity is resolved
         *                   against rather than against the world; the author
         *                   drew the part where gravity had already settled
         *                   it, so a world-down pull would apply gravity twice
         *                   and displace the pose.
         * @param currentDir where the segment is now, which decides how much
         *                   of the pose-displacing perpendicular pull is let
         *                   in.
         */
        private Vector3f force(
                PhysicsBoneSelectionPlan.SpringProfile profile,
                Vector3f restDir,
                Vector3f currentDir
        ) {
            float inertia = (float) INERTIA_GAIN * profile.inertiaScale();
            float turn = (float) TURN_GAIN * profile.turnScale();
            float gravity = (float) GRAVITY_POWER * profile.gravityScale();
            float alongRest = -restDir.y() * gravity;
            float displaced = Mth.clamp(
                    1.0F - currentDir.dot(restDir),
                    0.0F,
                    1.0F
            );
            float seated = 1.0F - displaced;
            return new Vector3f(
                    acceleration.x() * -inertia + yawRate * turn
                            + restDir.x() * alongRest * seated,
                    acceleration.y() * -inertia
                            + restDir.y() * alongRest * seated
                            - gravity * displaced,
                    acceleration.z() * -inertia
                            + restDir.z() * alongRest * seated
            );
        }
    }

    private static final class BoneData {
        private final BoneKinematics.Metrics kinematics;
        private final Vector3f boneAxis;
        private final float leverArm;
        private final float safeAngle;
        private final PhysicsBoneSelectionPlan.SpringProfile profile;
        private final Vector3f currentDir = new Vector3f();
        private final Vector3f prevDir = new Vector3f();
        private float previousDt;
        private boolean initialized;

        private BoneData(
                BoneKinematics.Metrics kinematics,
                PhysicsBoneSelectionPlan.SpringProfile profile
        ) {
            this.kinematics = kinematics;
            this.boneAxis = kinematics.axis();
            this.leverArm = kinematics.leverArm();
            this.safeAngle = kinematics.safeAngle();
            this.profile = profile;
        }
    }
}
