package com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProjector;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionScratch;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One segment paired with one collider. The geometry lives in a shared
 * {@link PreparedCollisionShape}; only the pivot, lever arm and the authored
 * rest overlap differ between the segments that share a collider.
 */
public final class PreparedCollisionProxy {
    private PreparedCollisionShape shape = new PreparedCollisionShape();
    private CollisionProxySource source = CollisionProxySource.AUTOMATIC;
    private final Vector3f pivot = new Vector3f();
    private float leverArm = 1.0F;
    private float preparedLeverArm = 1.0F;
    private float preparedRadius;
    private float preparedHitRadius;
    private float projectionRadius;
    private float projectionHitRadius;
    private boolean animationPoseAllowanceEligible;
    private int exitFace = CollisionProjector.NO_FACE;
    private final PreparedCollisionRestAllowance restAllowance =
            new PreparedCollisionRestAllowance();

    public void setPlane(
            int referenceIndex,
            Vector3f originModel,
            Vector3f pointModel,
            Vector3f normalModel,
            float endpointRadius,
            float fixedLeverArm
    ) {
        shape.setPlane(referenceIndex, originModel, pointModel, normalModel,
                endpointRadius);
        setCommon(fixedLeverArm);
    }

    public void setSphere(
            int referenceIndex,
            Vector3f originModel,
            Vector3f centerModel,
            float colliderRadius,
            float endpointRadius,
            float fixedLeverArm
    ) {
        shape.setSphere(referenceIndex, originModel, centerModel,
                colliderRadius, endpointRadius);
        setCommon(fixedLeverArm);
    }

    public void setCapsule(
            int referenceIndex,
            Vector3f originModel,
            Vector3f startModel,
            Vector3f endModel,
            float colliderRadius,
            float endpointRadius,
            float fixedLeverArm
    ) {
        shape.setCapsule(referenceIndex, originModel, startModel, endModel,
                colliderRadius, endpointRadius);
        setCommon(fixedLeverArm);
    }

    public void setBox(
            int referenceIndex,
            Vector3f originModel,
            Vector3f centerModel,
            Vector3f axisXModel,
            Vector3f axisYModel,
            Vector3f axisZModel,
            Vector3f halfExtentsModel,
            float endpointRadius,
            int openAxis,
            float fixedLeverArm
    ) {
        shape.setBox(referenceIndex, originModel, centerModel, axisXModel,
                axisYModel, axisZModel, halfExtentsModel, endpointRadius,
                openAxis);
        setCommon(fixedLeverArm);
    }

    /**
     * Standalone use keeps its own shape; the solver rebinds every pairing of
     * the same collider onto one shared instance.
     */
    public void prepare(
            Vector3f runtimePivotModel,
            Matrix4f affineDelta,
            Matrix3f normalTransform,
            float maxBasisScale
    ) {
        prepare(runtimePivotModel, affineDelta, normalTransform,
                maxBasisScale, maxBasisScale);
    }

    public void prepare(
            Vector3f runtimePivotModel,
            Matrix4f affineDelta,
            Matrix3f normalTransform,
            float colliderScale,
            float endpointScale
    ) {
        prepare(
                runtimePivotModel,
                affineDelta,
                normalTransform,
                colliderScale,
                endpointScale,
                leverArm * finiteScale(endpointScale)
        );
    }

    public void prepare(
            Vector3f runtimePivotModel,
            Matrix4f affineDelta,
            Matrix3f normalTransform,
            float colliderScale,
            float endpointScale,
            float runtimeLeverArm
    ) {
        shape.prepare(affineDelta, normalTransform, colliderScale);
        bindFrame(
                runtimePivotModel,
                colliderScale,
                endpointScale,
                runtimeLeverArm
        );
    }

    /**
     * Second half of {@link #prepare}, for callers whose shape was already
     * transformed once for the whole model this frame.
     */
    void bindFrame(
            Vector3f runtimePivotModel,
            float colliderScale,
            float endpointScale,
            float runtimeLeverArm
    ) {
        pivot.set(runtimePivotModel).sub(shape.referenceOrigin());
        float hitScale = finiteScale(endpointScale);
        preparedHitRadius = shape.hitRadius() * hitScale;
        preparedRadius = shape.scaledRadius() + preparedHitRadius;
        preparedLeverArm = Float.isFinite(runtimeLeverArm)
                ? Math.max(1.0E-6F, runtimeLeverArm)
                : leverArm;
        restAllowance.prepare(
                finiteScale(colliderScale),
                hitScale,
                preparedLeverArm,
                leverArm
        );
        applyRestAllowance();
    }

    /**
     * Whether this segment's endpoint sweep can still reach the collider. The
     * endpoint always sits on a sphere of {@code leverArm} around the pivot,
     * so a collider that misses that shell by more than its own extent cannot
     * be touched however the segment turns. Runs before {@link #bindFrame} so
     * an out-of-reach collider costs one distance and nothing else.
     */
    float slack(
            Vector3f runtimePivotModel,
            float runtimeLeverArm,
            float endpointScale,
            SwingCone cone
    ) {
        float bound = shape.cullRadius();
        if (!Float.isFinite(bound)) {
            return Float.NEGATIVE_INFINITY;
        }
        return cone.slackToSweep(
                runtimePivotModel,
                shape.centerModel(),
                runtimeLeverArm,
                bound + shape.hitRadius() * Math.max(0.0F, endpointScale)
        );
    }

    void bind(PreparedCollisionShape sharedShape) {
        shape = sharedShape;
    }

    float restHitRadius() {
        return shape.hitRadius();
    }

    PreparedCollisionShape shape() {
        return shape;
    }

    void allowInitialRestPose(
            Vector3f restDirection,
            CollisionScratch scratch
    ) {
        if (!restAllowance.needsCalibration(source)) {
            return;
        }
        restAllowance.calibrate(unadjustedClearance(restDirection, scratch));
        applyRestAllowance();
    }

    boolean needsCalibration() {
        return restAllowance.needsCalibration(source);
    }

    /**
     * Re-measures how deep the animation alone reaches into this collider and
     * moves the allowance to match, so the projection below only rejects the
     * depth secondary motion adds on top of the authored pose.
     */
    void trackAnimationPose(
            Vector3f restDirection,
            double poseTime,
            CollisionScratch scratch
    ) {
        if (!animationPoseAllowanceEligible
                || source == CollisionProxySource.EXPLICIT) {
            return;
        }
        if (restAllowance.trackAnimationPose(
                unadjustedClearance(restDirection, scratch),
                poseTime
        )) {
            applyRestAllowance();
        }
    }

    public boolean project(Vector3f direction, CollisionScratch scratch) {
        return switch (shape.kind()) {
            case PLANE -> CollisionProjector.projectPlane(
                    direction, pivot, shape.pointA(), shape.normal(),
                    projectionHitRadius, preparedLeverArm, scratch
            );
            case SPHERE -> CollisionProjector.projectSphere(
                    direction, pivot, shape.pointA(), projectionRadius,
                    preparedLeverArm, scratch
            );
            case CAPSULE -> CollisionProjector.projectCapsule(
                    direction, pivot, shape.pointA(), shape.pointB(),
                    projectionRadius, preparedLeverArm, scratch
            );
            case BOX -> projectBox(direction, scratch);
        };
    }

    /**
     * Carries the escape face across frames. The choice is this proxy's, not
     * the scratch's: one endpoint meets many boxes per frame and each has to
     * remember the face it entered through separately.
     */
    private boolean projectBox(Vector3f direction, CollisionScratch scratch) {
        if (!touching(direction)) {
            exitFace = CollisionProjector.NO_FACE;
            return false;
        }
        scratch.setExitFace(exitFace);
        boolean moved = CollisionProjector.projectBox(
                direction, pivot, shape.pointA(), shape.axisX(),
                shape.axisY(), shape.axisZ(), shape.halfExtents(),
                projectionHitRadius, shape.openAxis(), preparedLeverArm,
                scratch
        );
        exitFace = scratch.exitFace();
        return moved;
    }

    /**
     * Bounding-sphere reject for the current endpoint. Most colliders a
     * segment carries are near but not under the tip on any given pass, and
     * this answers those without entering the box's local frame.
     */
    private boolean touching(Vector3f direction) {
        Vector3f center = shape.pointA();
        float dx = pivot.x + direction.x * preparedLeverArm - center.x;
        float dy = pivot.y + direction.y * preparedLeverArm - center.y;
        float dz = pivot.z + direction.z * preparedLeverArm - center.z;
        float reach = shape.cullRadius() + projectionHitRadius;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    public float clearance(Vector3f direction, CollisionScratch scratch) {
        return switch (shape.kind()) {
            case PLANE -> CollisionProjector.planeClearance(
                    direction, pivot, shape.pointA(), shape.normal(),
                    projectionHitRadius, preparedLeverArm, scratch
            );
            case SPHERE -> CollisionProjector.sphereClearance(
                    direction, pivot, shape.pointA(), projectionRadius,
                    preparedLeverArm, scratch
            );
            case CAPSULE -> CollisionProjector.capsuleClearance(
                    direction, pivot, shape.pointA(), shape.pointB(),
                    projectionRadius, preparedLeverArm, scratch
            );
            case BOX -> CollisionProjector.boxClearance(
                    direction, pivot, shape.pointA(), shape.axisX(),
                    shape.axisY(), shape.axisZ(), shape.halfExtents(),
                    projectionHitRadius, shape.openAxis(), preparedLeverArm,
                    scratch
            );
        };
    }

    public void copyDebugData(
            Vector3f currentDirection,
            CollisionProxyDebugData output,
            CollisionScratch scratch
    ) {
        PreparedCollisionDebugCopier.copy(
                this, currentDirection, output, scratch
        );
    }

    public int referenceNodeIndex() {
        return shape.referenceNodeIndex();
    }

    public CollisionProxyKind kind() {
        return shape.kind();
    }

    void setSource(CollisionProxySource value) { source = value; }
    void setAnimationPoseAllowanceEligible(boolean value) {
        animationPoseAllowanceEligible = value;
    }
    CollisionProxySource source() { return source; }
    Vector3f referenceOrigin() { return shape.referenceOrigin(); }
    Vector3f pivot() { return pivot; }
    Vector3f pointA() { return shape.pointA(); }
    Vector3f pointB() { return shape.pointB(); }
    Vector3f normal() { return shape.normal(); }
    Vector3f axisX() { return shape.axisX(); }
    Vector3f axisY() { return shape.axisY(); }
    Vector3f axisZ() { return shape.axisZ(); }
    Vector3f halfExtents() { return shape.halfExtents(); }
    int openAxis() { return shape.openAxis(); }
    float preparedRadius() { return preparedRadius; }
    float preparedHitRadius() { return preparedHitRadius; }
    float projectionHitRadius() { return projectionHitRadius; }
    float preparedLeverArm() { return preparedLeverArm; }

    void resetRestAllowance() {
        restAllowance.reset();
        exitFace = CollisionProjector.NO_FACE;
        applyRestAllowance();
    }

    private static float finiteScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.0F, scale) : 1.0F;
    }

    private void setCommon(float fixedLeverArm) {
        leverArm = Math.max(1.0E-6F, fixedLeverArm);
        preparedLeverArm = leverArm;
        restAllowance.reset();
        applyRestAllowance();
    }

    private float unadjustedClearance(
            Vector3f direction,
            CollisionScratch scratch
    ) {
        return switch (shape.kind()) {
            case PLANE -> CollisionProjector.planeClearance(
                    direction, pivot, shape.pointA(), shape.normal(),
                    preparedHitRadius, preparedLeverArm, scratch
            );
            case SPHERE -> CollisionProjector.sphereClearance(
                    direction, pivot, shape.pointA(), preparedRadius,
                    preparedLeverArm, scratch
            );
            case CAPSULE -> CollisionProjector.capsuleClearance(
                    direction, pivot, shape.pointA(), shape.pointB(),
                    preparedRadius, preparedLeverArm, scratch
            );
            case BOX -> CollisionProjector.boxClearance(
                    direction, pivot, shape.pointA(), shape.axisX(),
                    shape.axisY(), shape.axisZ(), shape.halfExtents(),
                    preparedHitRadius, shape.openAxis(), preparedLeverArm,
                    scratch
            );
        };
    }

    private void applyRestAllowance() {
        projectionHitRadius = restAllowance.threshold(preparedHitRadius);
        projectionRadius = Math.max(
                0.0F,
                restAllowance.threshold(preparedRadius)
        );
    }
}
