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
    private static final float MAX_REFERENCE_DELTA =
            (float) Math.toRadians(75.0D);

    private final PhysicsSolverLayout layout;
    private final boolean constraintsEnabled;
    private final Quaternionf[] animationOrientations;
    private final Quaternionf[] renderedOrientations;
    private final Quaternionf[] previousReferenceOrientations;
    private final Quaternionf[] frameReferenceDeltas;
    private final boolean[] frameReferenceAbrupt;
    private final int[] frameReferenceGeneration;
    private final Vector3f[] currentDirections;
    private final Vector3f[] previousDirections;
    private final float[] previousDeltaSeconds;
    private final boolean[] initialized;
    private final boolean[] referenceInitialized;

    private final Quaternionf rootOrientation = new Quaternionf();
    private final Quaternionf localRotation = new Quaternionf();
    private final Quaternionf animationRotation = new Quaternionf();
    private final Quaternionf physicalDelta = new Quaternionf();
    private final Quaternionf referenceInverse = new Quaternionf();
    private final Quaternionf referenceTransport = new Quaternionf();
    private final Vector3f restDirection = new Vector3f();
    private final Vector3f nextDirection = new Vector3f();
    private final Vector3f localDirection = new Vector3f();
    private final Vector3f deflectionAxis = new Vector3f();
    private final Vector3f rotationEuler = new Vector3f();
    private final Vector3f pivotOffset = new Vector3f();
    private final Vector3f pivotScratch = new Vector3f();
    private final Vector3f constraintRight = new Vector3f();
    private final Vector3f collisionPivot = new Vector3f();
    private final Vector3f collisionTip = new Vector3f();
    private final Vector3f collisionPlanePoint = new Vector3f();
    private final Vector3f collisionNormal = new Vector3f();

    private int lastVisitedNodeCount;
    private float lastPeakDeflection;
    private int lastConstraintProjectionCount;
    private int lastCollisionProjectionCount;
    private int referenceGeneration;

    public SpringBoneSolver(PhysicsSolverLayout layout) {
        this(layout, true);
    }

    public SpringBoneSolver(
            PhysicsSolverLayout layout,
            boolean constraintsEnabled
    ) {
        this.layout = layout;
        this.constraintsEnabled = constraintsEnabled;
        this.animationOrientations =
                new Quaternionf[layout.activeNodeCount()];
        this.renderedOrientations =
                new Quaternionf[layout.activeNodeCount()];
        for (int index = 0; index < renderedOrientations.length; index++) {
            animationOrientations[index] = new Quaternionf();
            renderedOrientations[index] = new Quaternionf();
        }
        int activeCount = layout.activeNodeCount();
        this.previousReferenceOrientations =
                new Quaternionf[activeCount];
        this.frameReferenceDeltas = new Quaternionf[activeCount];
        for (int index = 0; index < activeCount; index++) {
            previousReferenceOrientations[index] = new Quaternionf();
            frameReferenceDeltas[index] = new Quaternionf();
        }
        this.frameReferenceAbrupt = new boolean[activeCount];
        this.frameReferenceGeneration = new int[activeCount];
        this.referenceInitialized = new boolean[activeCount];
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
        lastConstraintProjectionCount = 0;
        lastCollisionProjectionCount = 0;
        if (constraintsEnabled && ++referenceGeneration == 0) {
            for (int index = 0;
                 index < frameReferenceGeneration.length;
                 index++) {
                frameReferenceGeneration[index] = 0;
            }
            referenceGeneration = 1;
        }

        boolean inlineAnimationOrientations =
                constraintsEnabled && layout.referencesPreordered();
        if (constraintsEnabled && !inlineAnimationOrientations) {
            collectAnimationOrientations();
        }

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
            if (inlineAnimationOrientations) {
                Quaternionf animationParent = node.parentIndex() < 0
                        ? rootOrientation
                        : animationOrientations[node.parentIndex()];
                composeOrientationsInto(
                        animationParent,
                        parentOrientation,
                        rx,
                        ry,
                        rz,
                        animationOrientations[index],
                        boneBaseOrientation
                );
            } else {
                composeOrientationInto(
                        parentOrientation,
                        rx,
                        ry,
                        rz,
                        boneBaseOrientation
                );
            }

            if (node.driven()) {
                int referenceIndex =
                        node.constraint().referenceNodeIndex();
                Quaternionf referenceOrientation =
                        !constraintsEnabled || referenceIndex < 0
                        ? rootOrientation
                        : animationOrientations[referenceIndex];
                int collisionReferenceIndex =
                        node.constraint().collisionReferenceNodeIndex();
                Quaternionf collisionReferenceOrientation =
                        !constraintsEnabled || collisionReferenceIndex < 0
                                ? rootOrientation
                                : animationOrientations[
                                        collisionReferenceIndex
                                ];
                integrateAndApply(
                        node,
                        boneBaseOrientation,
                        referenceOrientation,
                        collisionReferenceOrientation,
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
                composeOrientationInto(
                        parentOrientation,
                        bone.getRotationX(),
                        bone.getRotationY(),
                        bone.getRotationZ(),
                        boneBaseOrientation
                );
            }
            lastVisitedNodeCount++;
        }
    }

    private void collectAnimationOrientations() {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            AnimatedGeoBone bone = node.bone();
            Quaternionf parentOrientation = node.parentIndex() < 0
                    ? rootOrientation
                    : animationOrientations[node.parentIndex()];
            composeOrientationInto(
                    parentOrientation,
                    bone.getRotationX(),
                    bone.getRotationY(),
                    bone.getRotationZ(),
                    animationOrientations[index]
            );
        }
    }

    private void integrateAndApply(
            PhysicsSolverLayout.Node node,
            Quaternionf boneBaseOrientation,
            Quaternionf referenceOrientation,
            Quaternionf collisionReferenceOrientation,
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
        if (constraintsEnabled) {
            transportReferenceSpace(
                    node.constraint(),
                    slot,
                    referenceOrientation
            );
        }

        PhysicsBoneSelectionPlan.SpringProfile profile =
                node.decision().profile();
        Vector3f current = currentDirections[slot];
        Vector3f previous = previousDirections[slot];
        boolean stepped = !paused && dt > EPSILON;
        if (stepped) {
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
            }
        } else {
            nextDirection.set(current);
        }

        boolean corrected = false;
        if (constraintsEnabled
                && nextDirection.lengthSquared() > EPSILON) {
            corrected = projectConstraints(
                    node.constraint(),
                    boneBaseOrientation,
                    collisionReferenceOrientation,
                    nextDirection
            );
        }
        if (stepped && nextDirection.lengthSquared() > EPSILON) {
            if (corrected) {
                previous.set(nextDirection);
            } else {
                previous.set(current);
            }
            current.set(nextDirection);
            previousDeltaSeconds[slot] = dt;
        } else if (corrected) {
            current.set(nextDirection);
            previous.set(nextDirection);
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

    private void transportReferenceSpace(
            SecondaryMotionConstraint constraint,
            int slot,
            Quaternionf referenceOrientation
    ) {
        int referenceIndex = constraint.referenceNodeIndex();
        if (!constraint.enabled() || referenceIndex < 0) {
            return;
        }
        prepareReferenceDelta(referenceIndex, referenceOrientation);
        if (frameReferenceAbrupt[referenceIndex]) {
            currentDirections[slot].set(restDirection);
            previousDirections[slot].set(restDirection);
            previousDeltaSeconds[slot] = 0.0F;
        } else {
            float follow = 1.0F - constraint.rotationInertiaScale();
            Quaternionf delta = frameReferenceDeltas[referenceIndex];
            if (follow > EPSILON
                    && delta.x() * delta.x()
                    + delta.y() * delta.y()
                    + delta.z() * delta.z() > 1.0E-12F) {
                referenceTransport.identity().slerp(
                        delta,
                        follow
                );
                referenceTransport.transform(currentDirections[slot]);
                referenceTransport.transform(previousDirections[slot]);
                currentDirections[slot].normalize();
                previousDirections[slot].normalize();
            }
        }
    }

    private void prepareReferenceDelta(
            int referenceIndex,
            Quaternionf referenceOrientation
    ) {
        if (frameReferenceGeneration[referenceIndex]
                == referenceGeneration) {
            return;
        }
        frameReferenceGeneration[referenceIndex] = referenceGeneration;
        Quaternionf previous =
                previousReferenceOrientations[referenceIndex];
        Quaternionf delta = frameReferenceDeltas[referenceIndex];
        if (!referenceInitialized[referenceIndex]) {
            previous.set(referenceOrientation);
            delta.identity();
            frameReferenceAbrupt[referenceIndex] = false;
            referenceInitialized[referenceIndex] = true;
            return;
        }

        float dot = previous.x() * referenceOrientation.x()
                + previous.y() * referenceOrientation.y()
                + previous.z() * referenceOrientation.z()
                + previous.w() * referenceOrientation.w();
        if (dot < 0.0F) {
            delta.set(
                    -referenceOrientation.x(),
                    -referenceOrientation.y(),
                    -referenceOrientation.z(),
                    -referenceOrientation.w()
            );
        } else {
            delta.set(referenceOrientation);
        }
        referenceInverse.set(previous).conjugate();
        delta.mul(referenceInverse).normalize();
        float angle = 2.0F * (float) Math.acos(
                Mth.clamp(Math.abs(delta.w()), 0.0F, 1.0F)
        );
        frameReferenceAbrupt[referenceIndex] =
                !Float.isFinite(angle) || angle > MAX_REFERENCE_DELTA;
        previous.set(referenceOrientation);
    }

    private boolean projectConstraints(
            SecondaryMotionConstraint constraint,
            Quaternionf boneOrientation,
            Quaternionf referenceOrientation,
            Vector3f direction
    ) {
        boolean corrected = false;
        for (int iteration = 0; iteration < 4; iteration++) {
            boolean swingCorrected = constraint.projectSwing(
                    direction,
                    restDirection,
                    boneOrientation,
                    constraintRight
            );
            if (swingCorrected) {
                lastConstraintProjectionCount++;
            }
            boolean collisionCorrected = constraint.projectCollision(
                    direction,
                    referenceOrientation,
                    collisionPivot,
                    collisionTip,
                    collisionPlanePoint,
                    collisionNormal
            );
            if (collisionCorrected) {
                lastCollisionProjectionCount++;
            }
            corrected |= swingCorrected || collisionCorrected;
            if (!swingCorrected && !collisionCorrected) {
                break;
            }
        }
        return corrected;
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
        float dot = Math.max(
                -1.0F,
                Math.min(1.0F, boneAxis.dot(localDirection))
        );
        boneAxis.cross(localDirection, deflectionAxis);
        float sin = deflectionAxis.length();
        if (sin < EPSILON) {
            if (dot >= 0.0F) {
                return;
            }
            if (Math.abs(boneAxis.y) < 0.90F) {
                deflectionAxis.set(0.0F, 1.0F, 0.0F).cross(boneAxis);
            } else {
                deflectionAxis.set(1.0F, 0.0F, 0.0F).cross(boneAxis);
            }
            deflectionAxis.normalize();
        } else {
            deflectionAxis.div(sin);
        }
        PhysicsBoneSelectionPlan.SpringProfile profile =
                node.decision().profile();
        BoneKinematics.Metrics kinematics = node.kinematics();
        float cap = Math.min(
                Math.min(MAX_ANGLE, kinematics.safeAngle())
                        * profile.angleScale(),
                MAX_TIP_DISPLACEMENT * profile.tipDisplacementScale()
                        / kinematics.leverArm()
        );
        boolean constrainedOutput =
                constraintsEnabled && node.constraint().enabled();
        float angle = constrainedOutput
                ? (float) Math.acos(dot)
                : Math.min((float) Math.acos(dot), cap);
        AnimatedGeoBone bone = node.bone();
        if (constrainedOutput) {
            animationRotation.identity().rotateZYX(rz, ry, rx);
            physicalDelta.identity().rotateAxis(
                    angle,
                    deflectionAxis.x,
                    deflectionAxis.y,
                    deflectionAxis.z
            );
            localRotation.set(animationRotation).mul(physicalDelta);
            eulerZYXInto(localRotation, rotationEuler);
            bone.setRotationX(rotationEuler.x);
            bone.setRotationY(rotationEuler.y);
            bone.setRotationZ(rotationEuler.z);
            compensatePivotPose(
                    bone,
                    kinematics,
                    animationRotation,
                    localRotation,
                    px,
                    py,
                    pz
            );
            lastPeakDeflection = Math.max(lastPeakDeflection, angle);
            return;
        }

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

    private void compensatePivotPose(
            AnimatedGeoBone bone,
            BoneKinematics.Metrics kinematics,
            Quaternionf animation,
            Quaternionf physical,
            float px,
            float py,
            float pz
    ) {
        if (!kinematics.compensatesPivot()) {
            return;
        }
        kinematics.poseCompensationOffsetInto(
                animation,
                physical,
                bone.getScaleX(),
                bone.getScaleY(),
                bone.getScaleZ(),
                pivotOffset,
                pivotScratch
        );
        applyPivotOffset(bone, px, py, pz);
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
        applyPivotOffset(bone, px, py, pz);
    }

    private void applyPivotOffset(
            AnimatedGeoBone bone,
            float px,
            float py,
            float pz
    ) {
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

    private void composeOrientationsInto(
            Quaternionf animationParent,
            Quaternionf renderedParent,
            float rotationX,
            float rotationY,
            float rotationZ,
            Quaternionf animationOutput,
            Quaternionf renderedOutput
    ) {
        localRotation.identity().rotateZYX(
                rotationZ,
                rotationY,
                rotationX
        );
        animationOutput.set(animationParent).mul(localRotation);
        renderedOutput.set(renderedParent).mul(localRotation);
    }

    public void reset() {
        for (int slot = 0; slot < currentDirections.length; slot++) {
            currentDirections[slot].zero();
            previousDirections[slot].zero();
            previousDeltaSeconds[slot] = 0.0F;
            initialized[slot] = false;
        }
        for (int index = 0;
             index < previousReferenceOrientations.length;
             index++) {
            referenceInitialized[index] = false;
            previousReferenceOrientations[index].identity();
            frameReferenceDeltas[index].identity();
            frameReferenceAbrupt[index] = false;
            frameReferenceGeneration[index] = 0;
        }
        for (Quaternionf orientation : animationOrientations) {
            orientation.identity();
        }
        for (Quaternionf orientation : renderedOrientations) {
            orientation.identity();
        }
        lastVisitedNodeCount = 0;
        lastPeakDeflection = 0.0F;
        lastConstraintProjectionCount = 0;
        lastCollisionProjectionCount = 0;
        referenceGeneration = 0;
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

    public int lastConstraintProjectionCount() {
        return lastConstraintProjectionCount;
    }

    public int lastCollisionProjectionCount() {
        return lastCollisionProjectionCount;
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

    public boolean copyPreviousDirection(int drivenSlot, Vector3f output) {
        if (drivenSlot < 0
                || drivenSlot >= previousDirections.length
                || !initialized[drivenSlot]) {
            output.zero();
            return false;
        }
        output.set(previousDirections[drivenSlot]);
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

    private static void eulerZYXInto(
            Quaternionf rotation,
            Vector3f output
    ) {
        float x = rotation.x;
        float y = rotation.y;
        float z = rotation.z;
        float w = rotation.w;
        output.set(
                (float) Math.atan2(
                        2.0F * (w * x + y * z),
                        1.0F - 2.0F * (x * x + y * y)
                ),
                (float) Math.asin(Mth.clamp(
                        2.0F * (w * y - z * x),
                        -1.0F,
                        1.0F
                )),
                (float) Math.atan2(
                        2.0F * (w * z + x * y),
                        1.0F - 2.0F * (y * y + z * z)
                )
        );
    }
}
