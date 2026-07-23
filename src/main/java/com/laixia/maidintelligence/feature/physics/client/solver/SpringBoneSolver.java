package com.laixia.maidintelligence.feature.physics.client.solver;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Allocation-free iterative spring solver over a {@link PhysicsSolverLayout}.
 */
public final class SpringBoneSolver {
    private static final double STIFFNESS = 6.0D;
    private static final float GRAVITY_POWER = 0.9F;
    private static final float DRAG = 0.35F;
    private static final float INERTIA_GAIN = 7.0F;
    private static final float TURN_GAIN = 3.0F;

    private static final float MAX_ANGLE = 0.8F;
    private static final float MAX_DEFLECT_X = 0.6F;
    private static final float MAX_DEFLECT_Y = 0.6F;
    private static final float MAX_DEFLECT_Z = 0.6F;
    private static final float MAX_TIP_DISPLACEMENT = 3.0F;

    private static final float REFERENCE_DELTA_SECONDS = 1.0F / 60.0F;
    private static final float EPSILON = 1.0E-5F;

    private final PhysicsSolverLayout layout;
    private final Quaternionf[] renderedOrientations;
    private final Vector3f[] currentDirections;
    private final Vector3f[] previousDirections;
    private final float[] previousDeltaSeconds;
    private final boolean[] initialized;

    private final Quaternionf rootOrientation = new Quaternionf();
    private final Quaternionf localRotation = new Quaternionf();
    private final Quaternionf animationRotation = new Quaternionf();
    private final Quaternionf physicalDelta = new Quaternionf();
    private final Vector3f restDirection = new Vector3f();
    private final Vector3f nextDirection = new Vector3f();
    private final Vector3f localDirection = new Vector3f();
    private final Vector3f deflectionAxis = new Vector3f();
    private final Vector3f pivotOffset = new Vector3f();
    private final Vector3f pivotScratch = new Vector3f();

    private int lastVisitedNodeCount;
    private float lastPeakDeflection;

    public SpringBoneSolver(PhysicsSolverLayout layout) {
        this.layout = layout;
        this.renderedOrientations =
                new Quaternionf[layout.activeNodeCount()];
        for (int index = 0; index < renderedOrientations.length; index++) {
            renderedOrientations[index] = new Quaternionf();
        }
        int drivenCount = layout.drivenBoneCount();
        this.currentDirections = new Vector3f[drivenCount];
        this.previousDirections = new Vector3f[drivenCount];
        for (int slot = 0; slot < drivenCount; slot++) {
            currentDirections[slot] = new Vector3f();
            previousDirections[slot] = new Vector3f();
        }
        this.previousDeltaSeconds = new float[drivenCount];
        this.initialized = new boolean[drivenCount];
    }

    public void solve(
            Vector3f modelAcceleration,
            float yawRate,
            float dt,
            boolean paused
    ) {
        rootOrientation.identity();
        lastVisitedNodeCount = 0;
        lastPeakDeflection = 0.0F;

        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            AnimatedGeoBone bone = node.bone();
            float rx = bone.getRotationX();
            float ry = bone.getRotationY();
            float rz = bone.getRotationZ();
            float px = bone.getPositionX();
            float py = bone.getPositionY();
            float pz = bone.getPositionZ();
            Quaternionf parentOrientation = node.parentIndex() < 0
                    ? rootOrientation
                    : renderedOrientations[node.parentIndex()];
            Quaternionf boneBaseOrientation = renderedOrientations[index];
            composeOrientationInto(
                    parentOrientation,
                    rx,
                    ry,
                    rz,
                    boneBaseOrientation
            );

            if (node.driven()) {
                integrateAndApply(
                        node,
                        boneBaseOrientation,
                        modelAcceleration,
                        yawRate,
                        dt,
                        paused,
                        rx,
                        ry,
                        rz,
                        px,
                        py,
                        pz
                );
            }

            composeOrientationInto(
                    parentOrientation,
                    bone.getRotationX(),
                    bone.getRotationY(),
                    bone.getRotationZ(),
                    boneBaseOrientation
            );
            lastVisitedNodeCount++;
        }
    }

    private void integrateAndApply(
            PhysicsSolverLayout.Node node,
            Quaternionf boneBaseOrientation,
            Vector3f modelAcceleration,
            float yawRate,
            float dt,
            boolean paused,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz
    ) {
        int slot = node.drivenSlot();
        Vector3f boneAxis = node.axis();
        boneBaseOrientation.transform(boneAxis, restDirection).normalize();
        if (!initialized[slot]) {
            currentDirections[slot].set(restDirection);
            previousDirections[slot].set(restDirection);
            initialized[slot] = true;
        }

        PhysicsBoneSelectionPlan.SpringProfile profile =
                node.decision().profile();
        if (!paused && dt > EPSILON) {
            Vector3f current = currentDirections[slot];
            Vector3f previous = previousDirections[slot];
            nextDirection.set(current);
            float drag = Mth.clamp(
                    DRAG * profile.dragScale(),
                    0.0F,
                    0.95F
            );
            float retention = dragRetention(drag, dt);
            float previousDt = previousDeltaSeconds[slot];
            float stepRatio = previousDt > EPSILON
                    ? Mth.clamp(dt / previousDt, 0.25F, 4.0F)
                    : 1.0F;
            nextDirection.add(
                    (current.x() - previous.x()) * retention * stepRatio,
                    (current.y() - previous.y()) * retention * stepRatio,
                    (current.z() - previous.z()) * retention * stepRatio
            );
            float stiffness = (float) (
                    STIFFNESS * profile.stiffnessScale() * dt
            );
            nextDirection.add(
                    restDirection.x() * stiffness,
                    restDirection.y() * stiffness,
                    restDirection.z() * stiffness
            );
            float inertia = INERTIA_GAIN * profile.inertiaScale();
            float turn = TURN_GAIN * profile.turnScale();
            nextDirection.add(
                    (modelAcceleration.x() * -inertia + yawRate * turn) * dt,
                    (modelAcceleration.y() * -inertia
                            - GRAVITY_POWER * profile.gravityScale()) * dt,
                    modelAcceleration.z() * -inertia * dt
            );
            if (nextDirection.lengthSquared() > EPSILON) {
                nextDirection.normalize();
                previous.set(current);
                current.set(nextDirection);
            }
            previousDeltaSeconds[slot] = dt;
        }

        applyDeflection(
                node,
                boneBaseOrientation,
                rx,
                ry,
                rz,
                px,
                py,
                pz
        );
    }

    private void applyDeflection(
            PhysicsSolverLayout.Node node,
            Quaternionf boneAnimationOrientation,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz
    ) {
        int slot = node.drivenSlot();
        Vector3f boneAxis = node.axis();
        boneAnimationOrientation.transformInverse(
                currentDirections[slot],
                localDirection
        );
        if (localDirection.lengthSquared() < EPSILON) {
            return;
        }
        localDirection.normalize();
        boneAxis.cross(localDirection, deflectionAxis);
        float sin = deflectionAxis.length();
        if (sin < EPSILON) {
            return;
        }
        deflectionAxis.div(sin);
        float dot = Math.max(
                -1.0F,
                Math.min(1.0F, boneAxis.dot(localDirection))
        );
        PhysicsBoneSelectionPlan.SpringProfile profile =
                node.decision().profile();
        BoneKinematics.Metrics kinematics = node.kinematics();
        float cap = Math.min(
                Math.min(MAX_ANGLE, kinematics.safeAngle())
                        * profile.angleScale(),
                MAX_TIP_DISPLACEMENT * profile.tipDisplacementScale()
                        / kinematics.leverArm()
        );
        float angle = Math.min((float) Math.acos(dot), cap);
        float dRx = clampAbs(
                deflectionAxis.x() * angle,
                MAX_DEFLECT_X * profile.angleScale()
        );
        float dRy = clampAbs(
                deflectionAxis.y() * angle,
                MAX_DEFLECT_Y * profile.angleScale()
        );
        float dRz = clampAbs(
                deflectionAxis.z() * angle,
                MAX_DEFLECT_Z * profile.angleScale()
        );
        AnimatedGeoBone bone = node.bone();
        bone.setRotationX(rx + dRx);
        bone.setRotationY(ry + dRy);
        bone.setRotationZ(rz + dRz);
        compensatePivot(
                bone,
                kinematics,
                rx,
                ry,
                rz,
                px,
                py,
                pz
        );
        lastPeakDeflection = Math.max(lastPeakDeflection, angle);
    }

    private void compensatePivot(
            AnimatedGeoBone bone,
            BoneKinematics.Metrics kinematics,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz
    ) {
        if (!kinematics.compensatesPivot()) {
            return;
        }
        animationRotation.identity().rotateZYX(rz, ry, rx);
        physicalDelta.identity().rotateZYX(
                bone.getRotationZ(),
                bone.getRotationY(),
                bone.getRotationX()
        ).mul(animationRotation.invert());
        kinematics.compensationOffsetInto(
                physicalDelta,
                pivotOffset,
                pivotScratch
        );
        bone.setPositionX(px - pivotOffset.x * 16.0F);
        bone.setPositionY(py + pivotOffset.y * 16.0F);
        bone.setPositionZ(pz + pivotOffset.z * 16.0F);
    }

    private void composeOrientationInto(
            Quaternionf parent,
            float rotationX,
            float rotationY,
            float rotationZ,
            Quaternionf output
    ) {
        localRotation.identity().rotateZYX(
                rotationZ,
                rotationY,
                rotationX
        );
        output.set(parent).mul(localRotation);
    }

    public void reset() {
        for (int slot = 0; slot < currentDirections.length; slot++) {
            currentDirections[slot].zero();
            previousDirections[slot].zero();
            previousDeltaSeconds[slot] = 0.0F;
            initialized[slot] = false;
        }
        for (Quaternionf orientation : renderedOrientations) {
            orientation.identity();
        }
        lastVisitedNodeCount = 0;
        lastPeakDeflection = 0.0F;
    }

    public PhysicsSolverLayout layout() {
        return layout;
    }

    public int lastVisitedNodeCount() {
        return lastVisitedNodeCount;
    }

    public float lastPeakDeflection() {
        return lastPeakDeflection;
    }

    public boolean copyCurrentDirection(int drivenSlot, Vector3f output) {
        if (drivenSlot < 0
                || drivenSlot >= currentDirections.length
                || !initialized[drivenSlot]) {
            output.zero();
            return false;
        }
        output.set(currentDirections[drivenSlot]);
        return true;
    }

    public static float dragRetention(float drag, float dt) {
        float clampedDrag = Mth.clamp(drag, 0.0F, 0.95F);
        if (dt <= 0.0F) {
            return 1.0F;
        }
        return (float) Math.pow(
                1.0F - clampedDrag,
                dt / REFERENCE_DELTA_SECONDS
        );
    }

    private static float clampAbs(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }
}
