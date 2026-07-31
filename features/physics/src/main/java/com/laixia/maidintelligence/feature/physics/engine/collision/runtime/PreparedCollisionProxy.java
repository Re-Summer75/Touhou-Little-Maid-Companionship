package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProjector;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One segment paired with one collider. The geometry lives in a shared
 * {@link PreparedCollisionShape}; only the pivot, lever arm and the authored
 * rest overlap differ between the segments that share a collider.
 */
public final class PreparedCollisionProxy {
    /*
     * Mutable state stays on the facade. Package-private visibility is limited
     * to the allocation-free lifecycle helpers in this runtime package.
     */
    PreparedCollisionShape shape = new PreparedCollisionShape();
    CollisionProxySource source = CollisionProxySource.AUTOMATIC;
    final Vector3f pivot = new Vector3f();
    float leverArm = 1.0F;
    float preparedLeverArm = 1.0F;
    float preparedRadius;
    float preparedHitRadius;
    float projectionRadius;
    float projectionHitRadius;
    /** Largest the driven sheet reaches in any direction, for sphere tests. */
    float meshCullReach;
    float bodyProjectionRadius;
    float bodyProjectionHitRadius;
    boolean animationPoseAllowanceEligible;
    int exitFace = CollisionProjector.NO_FACE;
    int bodyExitFace = CollisionProjector.NO_FACE;
    final PreparedCollisionRestAllowance restAllowance =
            new PreparedCollisionRestAllowance();
    /**
     * The length of a segment lies far deeper inside its neighbours than the
     * tip does — a skirt panel rests against the hip it hangs from — so the two
     * samples need separate allowances. Sharing one would let the body's
     * authored overlap set the threshold for the tip as well, and the segment
     * would stop resisting anything shallower than the depth it is already at.
     */
    final PreparedCollisionRestAllowance bodyAllowance =
            new PreparedCollisionRestAllowance();
    /**
     * Gap last measured at each sample, less everything that has moved since.
     *
     * <p>A projection that finds nothing to push has still established how far
     * away the collider was, and nothing can close that gap faster than the
     * two sides travel towards each other. Subtracting that travel turns one
     * measurement into an answer good for several passes and often several
     * frames, which is what the relaxation loop and a resting skirt spend most
     * of their collision budget re-deriving. Non-positive means the reserve is
     * used up and the geometry has to be entered again.
     */
    float tipReserve;
    float bodyReserve;
    /** Where the samples sat when their reserves were last measured. */
    final Vector3f reserveDirection = new Vector3f();
    float reserveBodyArm;
    boolean reserveValid;
    int reserveFrame = PreparedCollisionProxyMotion.NO_FRAME;
    /**
     * Suppressed until the segment leaves this collider entirely.
     *
     * <p>What resolving one surface does to the others. Pushing a hem clear of a
     * thigh drives it into the hip behind, and answering that too only sends it
     * back — the pair take turns and the segment shakes between them. So the
     * collider it was driven into stops asking for anything at all: the overlap
     * stays, deep and steady, which is what a seated pose looks like in an
     * authored model anyway, and a steady overlap reads as cloth lying against a
     * body while the same depth alternating reads as a shake.
     *
     * <p>Held until the overlap is gone rather than for a set time, because the
     * segment is inside the geometry for as long as the pose holds it there and
     * any deadline would restore the fight while the cause was still present.
     */
    boolean suppressed;

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
        shape.prepareNow(affineDelta, normalTransform, colliderScale);
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
        bindFrame(runtimePivotModel, colliderScale, endpointScale,
                runtimeLeverArm, PreparedCollisionProxyMotion.NO_FRAME);
    }

    void bindFrame(
            Vector3f runtimePivotModel,
            float colliderScale,
            float endpointScale,
            float runtimeLeverArm,
            int frame
    ) {
        float hitScale = finiteScale(endpointScale);
        float nextHitRadius = shape.hitRadius() * hitScale;
        float nextLeverArm = Float.isFinite(runtimeLeverArm)
                ? Math.max(1.0E-6F, runtimeLeverArm)
                : leverArm;
        PreparedCollisionProxyMotion.chargeFrameMotion(
                this, runtimePivotModel, nextLeverArm, nextHitRadius, frame
        );
        pivot.set(runtimePivotModel).sub(shape.referenceOrigin());
        preparedHitRadius = nextHitRadius;
        preparedRadius = shape.scaledRadius() + preparedHitRadius;
        preparedLeverArm = nextLeverArm;
        PreparedCollisionProxyRestLifecycle.prepareFrame(
                this, colliderScale, hitScale
        );
    }

    /**
     * Drops the measured gaps. Called wherever the pairing stops being
     * updated every frame, since a reserve is only sound while everything
     * that could close it is being charged against it.
     */
    void invalidateReserve() {
        PreparedCollisionProxyMotion.invalidate(this);
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
        PreparedCollisionProxyRestLifecycle.allowInitialRestPose(
                this, restDirection, scratch
        );
    }

    boolean needsCalibration() {
        return PreparedCollisionProxyRestLifecycle.needsCalibration(this);
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
        PreparedCollisionProxyRestLifecycle.trackAnimationPose(
                this, restDirection, poseTime, scratch
        );
    }

    /**
     * Resolves the collider against the tip, and then against whatever part of
     * the segment's own length the collider is actually in front of.
     *
     * <p>A segment is a strip of cloth, not a bead on the end of a stick, but
     * only the tip used to be tested. A leg swinging into the middle of a skirt
     * panel therefore met nothing at all and passed straight through, while the
     * tip below it reported itself perfectly clear. Sampling the length fixes
     * that without new geometry: the projection already takes the lever arm
     * that places its sample point and divides the correction by it, so calling
     * it with a shortened arm resolves a point further up the segment and
     * converts the push into the smaller turn that arm needs.
     */
    public boolean project(Vector3f direction, CollisionScratch scratch) {
        return PreparedCollisionProxyProjection.project(
                this, direction, scratch
        );
    }

    /** Stops this collider asking for anything until the overlap is gone. */
    void suppress() {
        PreparedCollisionProxyRestLifecycle.suppress(this);
    }

    boolean isSuppressed() {
        return PreparedCollisionProxyRestLifecycle.isSuppressed(this);
    }

    /**
     * Lifts the suppression once the segment is clear of the geometry.
     *
     * <p>Answered from the raw overlap rather than from the reserve machinery,
     * which a suppressed collider stops maintaining because it takes no samples.
     * Reports whether the collider is still suppressed afterwards.
     */
    boolean holdSuppression(Vector3f direction, CollisionScratch scratch) {
        return PreparedCollisionProxyRestLifecycle.holdSuppression(
                this, direction, scratch
        );
    }

    public float clearance(Vector3f direction, CollisionScratch scratch) {
        return PreparedCollisionProxyProjection.clearance(
                this, direction, scratch
        );
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

    void setMeshCullReach(float value) {
        meshCullReach = Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
    }
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
        PreparedCollisionProxyRestLifecycle.reset(this);
    }

    private static float finiteScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.0F, scale) : 1.0F;
    }

    private void setCommon(float fixedLeverArm) {
        leverArm = Math.max(1.0E-6F, fixedLeverArm);
        preparedLeverArm = leverArm;
        PreparedCollisionProxyRestLifecycle.resetAllowances(this);
        PreparedCollisionProxyRestLifecycle.applyAllowance(this);
    }
}
