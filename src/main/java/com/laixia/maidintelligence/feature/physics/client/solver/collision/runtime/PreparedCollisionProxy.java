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
    /** Stands in for a measured clearance that a reject proved positive. */
    private static final float CLEAR = 1.0F;
    /** Closest to the root a body sample may be resolved, as a fraction. */
    private static final float BODY_MIN = 0.35F;
    /** Past this the body sample has merged with the tip; skip it. */
    private static final float BODY_MAX = 0.90F;
    /** Frame stamp of a pairing the solver is not driving frame by frame. */
    private static final int NO_FRAME = Integer.MIN_VALUE;

    private PreparedCollisionShape shape = new PreparedCollisionShape();
    private CollisionProxySource source = CollisionProxySource.AUTOMATIC;
    private final Vector3f pivot = new Vector3f();
    private float leverArm = 1.0F;
    private float preparedLeverArm = 1.0F;
    private float preparedRadius;
    private float preparedHitRadius;
    private float projectionRadius;
    private float projectionHitRadius;
    private float bodyProjectionRadius;
    private float bodyProjectionHitRadius;
    private boolean animationPoseAllowanceEligible;
    private int exitFace = CollisionProjector.NO_FACE;
    private int bodyExitFace = CollisionProjector.NO_FACE;
    private final PreparedCollisionRestAllowance restAllowance =
            new PreparedCollisionRestAllowance();
    /**
     * The length of a segment lies far deeper inside its neighbours than the
     * tip does — a skirt panel rests against the hip it hangs from — so the two
     * samples need separate allowances. Sharing one would let the body's
     * authored overlap set the threshold for the tip as well, and the segment
     * would stop resisting anything shallower than the depth it is already at.
     */
    private final PreparedCollisionRestAllowance bodyAllowance =
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
    private float tipReserve;
    private float bodyReserve;
    /** Where the samples sat when their reserves were last measured. */
    private final Vector3f reserveDirection = new Vector3f();
    private float reserveBodyArm;
    private boolean reserveValid;
    private int reserveFrame = NO_FRAME;

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
        bindFrame(runtimePivotModel, colliderScale, endpointScale,
                runtimeLeverArm, NO_FRAME);
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
        chargeFrameMotion(runtimePivotModel, nextLeverArm, nextHitRadius,
                frame);
        pivot.set(runtimePivotModel).sub(shape.referenceOrigin());
        preparedHitRadius = nextHitRadius;
        preparedRadius = shape.scaledRadius() + preparedHitRadius;
        preparedLeverArm = nextLeverArm;
        restAllowance.prepare(
                finiteScale(colliderScale),
                hitScale,
                preparedLeverArm,
                leverArm
        );
        bodyAllowance.prepare(
                finiteScale(colliderScale),
                hitScale,
                preparedLeverArm,
                leverArm
        );
        applyRestAllowance();
    }

    /**
     * Charges the reserves for everything that moved between frames while the
     * segment's own direction stood still: the collider under animation, the
     * pivot the segment hangs from, and any change in the scale that places
     * the sample points. Only then can a gap measured last frame still be
     * trusted this frame.
     */
    private void chargeFrameMotion(
            Vector3f runtimePivotModel,
            float nextLeverArm,
            float nextHitRadius,
            int frame
    ) {
        boolean continuous = reserveValid
                && frame != NO_FRAME
                && frame == reserveFrame + 1;
        reserveFrame = frame;
        if (!continuous) {
            invalidateReserve();
            return;
        }
        float px = runtimePivotModel.x - shape.referenceOrigin().x;
        float py = runtimePivotModel.y - shape.referenceOrigin().y;
        float pz = runtimePivotModel.z - shape.referenceOrigin().z;
        float dx = px - pivot.x;
        float dy = py - pivot.y;
        float dz = pz - pivot.z;
        float travel = shape.motionBound()
                + (float) Math.sqrt(dx * dx + dy * dy + dz * dz)
                + Math.abs(nextLeverArm - preparedLeverArm)
                + Math.abs(nextHitRadius - preparedHitRadius);
        tipReserve -= travel;
        bodyReserve -= travel;
    }

    /**
     * Drops the measured gaps. Called wherever the pairing stops being
     * updated every frame, since a reserve is only sound while everything
     * that could close it is being charged against it.
     */
    void invalidateReserve() {
        reserveValid = false;
        tipReserve = 0.0F;
        bodyReserve = 0.0F;
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
        restAllowance.calibrate(clearance(
                restDirection, preparedLeverArm, preparedRadius,
                preparedHitRadius, scratch
        ));
        float bodyArm = bodyLeverArm(restDirection);
        bodyAllowance.calibrate(bodyArm <= 0.0F ? CLEAR : clearance(
                restDirection, bodyArm, preparedRadius, preparedHitRadius,
                scratch
        ));
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
        /*
         * The authored depth only needs measuring where the authored pose can
         * actually reach the collider, and usually it cannot: ranking keeps
         * the colliders nearest the deflected endpoint, which is not where the
         * rest pose points. One bounding-sphere test answers those, and it has
         * to, because this runs for every surviving pairing on every driven
         * segment on every frame while a full clearance does not.
         */
        boolean changed = restAllowance.trackAnimationPose(
                separated(restDirection, preparedLeverArm, preparedHitRadius)
                        ? CLEAR
                        : clearance(
                                restDirection, preparedLeverArm,
                                preparedRadius, preparedHitRadius, scratch
                        ),
                poseTime
        );
        float bodyArm = bodyLeverArm(restDirection);
        changed |= bodyAllowance.trackAnimationPose(
                bodyArm <= 0.0F
                        || separated(restDirection, bodyArm, preparedHitRadius)
                        ? CLEAR
                        : clearance(
                                restDirection, bodyArm, preparedRadius,
                                preparedHitRadius, scratch
                        ),
                poseTime
        );
        if (changed) {
            applyRestAllowance();
        }
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
        float bodyArm = bodyLeverArm(direction);
        spendReserve(direction, bodyArm);
        boolean moved = tipReserve > 0.0F
                ? false
                : sample(
                        direction, preparedLeverArm, projectionRadius,
                        projectionHitRadius, true, scratch
                );
        if (bodyArm <= 0.0F) {
            bodyExitFace = CollisionProjector.NO_FACE;
            /*
             * No sample was taken, so nothing was learned about this end of
             * the segment and there is no distance to spend next pass.
             */
            bodyReserve = 0.0F;
            return moved;
        }
        if (bodyReserve > 0.0F) {
            return moved;
        }
        return sample(
                direction, bodyArm, bodyProjectionRadius,
                bodyProjectionHitRadius, false, scratch
        ) || moved;
    }

    /**
     * Resolves one sample and records how much room it found, so the next
     * passes can skip it while the room lasts.
     */
    private boolean sample(
            Vector3f direction,
            float arm,
            float radius,
            float hitRadius,
            boolean tip,
            CollisionScratch scratch
    ) {
        float separation = separation(direction, arm, hitRadius);
        if (separation > 0.0F) {
            if (tip) {
                exitFace = CollisionProjector.NO_FACE;
                tipReserve = separation;
            } else {
                bodyExitFace = CollisionProjector.NO_FACE;
                bodyReserve = separation;
            }
            return false;
        }
        scratch.clearMeasuredClearance();
        boolean moved = project(direction, arm, radius, hitRadius, tip,
                scratch);
        /*
         * A push invalidates the gap it was measured from, and a shape that
         * reports no gap leaves the sentinel behind; both land on zero, which
         * simply means measure again.
         */
        float measured = moved
                ? 0.0F
                : Math.max(0.0F, scratch.measuredClearance());
        if (tip) {
            tipReserve = measured;
        } else {
            bodyReserve = measured;
        }
        return moved;
    }

    /**
     * Charges both reserves for the segment's own movement since they were
     * measured. The tip travels its arm times the turn; the body sample also
     * slides along the segment as the collider's bearing changes, so the
     * change in its arm is charged on top.
     */
    private void spendReserve(Vector3f direction, float bodyArm) {
        float arm = Math.max(0.0F, bodyArm);
        if (!reserveValid) {
            tipReserve = 0.0F;
            bodyReserve = 0.0F;
            reserveValid = true;
        } else {
            float travel = reserveDirection.distance(direction);
            tipReserve -= preparedLeverArm * travel;
            bodyReserve -= reserveBodyArm * travel
                    + Math.abs(arm - reserveBodyArm);
        }
        reserveDirection.set(direction);
        reserveBodyArm = arm;
    }

    private boolean project(
            Vector3f direction,
            float arm,
            float radius,
            float hitRadius,
            boolean tip,
            CollisionScratch scratch
    ) {
        return switch (shape.kind()) {
            case PLANE -> CollisionProjector.projectPlane(
                    direction, pivot, shape.pointA(), shape.normal(),
                    hitRadius, arm, scratch
            );
            case SPHERE -> CollisionProjector.projectSphere(
                    direction, pivot, shape.pointA(), radius, arm, scratch
            );
            case CAPSULE -> CollisionProjector.projectCapsule(
                    direction, pivot, shape.pointA(), shape.pointB(),
                    radius, arm, scratch
            );
            case BOX -> projectBox(direction, arm, hitRadius, tip, scratch);
        };
    }

    /**
     * Where along the segment to take the second sample, as a lever arm, or a
     * non-positive value when the tip sample already covers it.
     *
     * <p>The point chosen is the one on the segment the collider sits squarest
     * in front of, which is where it will bite first and deepest. Sampling a
     * fixed ladder of positions instead would cost a projection per rung to
     * find the same place. Contacts nearer the root than {@link #BODY_MIN} are
     * pulled out to it rather than resolved where they are: the segment turns
     * about its root, so a point that close barely moves however far it turns,
     * and asking for the turn that would clear it throws the rest of the
     * segment across the model.
     */
    private float bodyLeverArm(Vector3f direction) {
        /*
         * A plane has no centre to sit in front of, and being unbounded it
         * already constrains the whole segment through the tip: a straight
         * segment whose tip is clear of a half-space is clear of it along its
         * entire length.
         */
        if (shape.kind() == CollisionProxyKind.PLANE) {
            return -1.0F;
        }
        Vector3f center = shape.pointA();
        float cx = center.x;
        float cy = center.y;
        float cz = center.z;
        if (shape.kind() == CollisionProxyKind.CAPSULE) {
            Vector3f end = shape.pointB();
            cx = 0.5F * (cx + end.x);
            cy = 0.5F * (cy + end.y);
            cz = 0.5F * (cz + end.z);
        }
        float along = (cx - pivot.x) * direction.x
                + (cy - pivot.y) * direction.y
                + (cz - pivot.z) * direction.z;
        float fraction = along / preparedLeverArm;
        if (fraction <= 0.0F || fraction >= BODY_MAX) {
            return -1.0F;
        }
        return Math.max(BODY_MIN, fraction) * preparedLeverArm;
    }

    /**
     * Carries the escape face across frames. The choice is this proxy's, not
     * the scratch's: one endpoint meets many boxes per frame and each has to
     * remember the face it entered through separately. Tip and body samples
     * keep separate faces, since they can be inside a box on opposite sides.
     */
    private boolean projectBox(
            Vector3f direction,
            float arm,
            float hitRadius,
            boolean tip,
            CollisionScratch scratch
    ) {
        scratch.setExitFace(tip ? exitFace : bodyExitFace);
        boolean moved = CollisionProjector.projectBox(
                direction, pivot, shape.pointA(), shape.axisX(),
                shape.axisY(), shape.axisZ(), shape.halfExtents(),
                hitRadius, shape.openAxis(), arm, scratch
        );
        if (tip) {
            exitFace = scratch.exitFace();
        } else {
            bodyExitFace = scratch.exitFace();
        }
        return moved;
    }

    /**
     * Bounding-sphere reject for the point {@code arm} places on the segment.
     * Most colliders a segment carries are near but not under that point on
     * any given pass, and this answers those without entering the box's local
     * frame. An unbounded shape reports a non-finite radius and is never
     * separated.
     */
    private boolean separated(
            Vector3f direction,
            float arm,
            float hitRadius
    ) {
        return separation(direction, arm, hitRadius) > 0.0F;
    }

    /**
     * Distance from the sample point to the collider's bounding sphere, which
     * is a lower bound on the distance to the collider itself. Positive means
     * out of contact, and by at least this much — enough to stand in for a
     * measured gap when the reject fires.
     */
    private float separation(
            Vector3f direction,
            float arm,
            float hitRadius
    ) {
        Vector3f center = shape.pointA();
        float dx = pivot.x + direction.x * arm - center.x;
        float dy = pivot.y + direction.y * arm - center.y;
        float dz = pivot.z + direction.z * arm - center.z;
        float reach = shape.cullRadius() + hitRadius;
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared <= reach * reach) {
            return -1.0F;
        }
        return (float) Math.sqrt(distanceSquared) - reach;
    }

    /**
     * Nearest approach anywhere along the segment, which is what ranking has
     * to compare. Measuring the tip alone would rank a leg swinging into the
     * middle of a panel as far away, and the slot limit would then drop it in
     * favour of the torso the panel merely hangs near — losing exactly the
     * contact that matters.
     */
    public float clearance(Vector3f direction, CollisionScratch scratch) {
        float tip = clearance(
                direction, preparedLeverArm, projectionRadius,
                projectionHitRadius, scratch
        );
        float bodyArm = bodyLeverArm(direction);
        if (bodyArm <= 0.0F
                || separated(direction, bodyArm, bodyProjectionHitRadius)) {
            return tip;
        }
        return Math.min(tip, clearance(
                direction, bodyArm, bodyProjectionRadius,
                bodyProjectionHitRadius, scratch
        ));
    }

    private float clearance(
            Vector3f direction,
            float arm,
            float radius,
            float hitRadius,
            CollisionScratch scratch
    ) {
        return switch (shape.kind()) {
            case PLANE -> CollisionProjector.planeClearance(
                    direction, pivot, shape.pointA(), shape.normal(),
                    hitRadius, arm, scratch
            );
            case SPHERE -> CollisionProjector.sphereClearance(
                    direction, pivot, shape.pointA(), radius, arm, scratch
            );
            case CAPSULE -> CollisionProjector.capsuleClearance(
                    direction, pivot, shape.pointA(), shape.pointB(),
                    radius, arm, scratch
            );
            case BOX -> CollisionProjector.boxClearance(
                    direction, pivot, shape.pointA(), shape.axisX(),
                    shape.axisY(), shape.axisZ(), shape.halfExtents(),
                    hitRadius, shape.openAxis(), arm, scratch
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
        bodyAllowance.reset();
        exitFace = CollisionProjector.NO_FACE;
        bodyExitFace = CollisionProjector.NO_FACE;
        invalidateReserve();
        applyRestAllowance();
    }

    private static float finiteScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.0F, scale) : 1.0F;
    }

    private void setCommon(float fixedLeverArm) {
        leverArm = Math.max(1.0E-6F, fixedLeverArm);
        preparedLeverArm = leverArm;
        restAllowance.reset();
        bodyAllowance.reset();
        applyRestAllowance();
    }

    /**
     * The allowance shrinks the radii a sample has to clear, and it releases
     * over time, so those radii grow back. Growth eats into a gap measured
     * before it, which is why the reserves are charged for it here rather
     * than left to notice a contact they were told could not happen yet.
     */
    private void applyRestAllowance() {
        float tipBefore = Math.max(projectionRadius, projectionHitRadius);
        float bodyBefore = Math.max(
                bodyProjectionRadius,
                bodyProjectionHitRadius
        );
        projectionHitRadius = restAllowance.threshold(preparedHitRadius);
        projectionRadius = Math.max(
                0.0F,
                restAllowance.threshold(preparedRadius)
        );
        bodyProjectionHitRadius = bodyAllowance.threshold(preparedHitRadius);
        bodyProjectionRadius = Math.max(
                0.0F,
                bodyAllowance.threshold(preparedRadius)
        );
        tipReserve -= Math.max(
                0.0F,
                Math.max(projectionRadius, projectionHitRadius) - tipBefore
        );
        bodyReserve -= Math.max(
                0.0F,
                Math.max(bodyProjectionRadius, bodyProjectionHitRadius)
                        - bodyBefore
        );
    }
}
