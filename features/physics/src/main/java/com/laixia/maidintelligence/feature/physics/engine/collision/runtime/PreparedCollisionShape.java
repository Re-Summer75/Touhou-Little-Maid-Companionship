package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;


import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProjector;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Animated collider geometry, shared by every segment that collides against
 * it. A cube is one shape however many segments reference it, so the frame
 * transform is paid once per collider instead of once per pairing.
 */
public final class PreparedCollisionShape {
    private static final float EPSILON = 1.0E-12F;

    private CollisionProxyKind kind = CollisionProxyKind.SPHERE;
    private int referenceNodeIndex = -1;
    private final Vector3f referenceOriginModel = new Vector3f();
    private final Vector3f restPointA = new Vector3f();
    private final Vector3f restPointB = new Vector3f();
    private final Vector3f restNormal = new Vector3f(0.0F, 1.0F, 0.0F);
    private final Vector3f restAxisX = new Vector3f(1.0F, 0.0F, 0.0F);
    private final Vector3f restAxisY = new Vector3f(0.0F, 1.0F, 0.0F);
    private final Vector3f restAxisZ = new Vector3f(0.0F, 0.0F, 1.0F);
    private final Vector3f restHalfExtents = new Vector3f();
    private float radius;
    private float hitRadius;
    private int openAxis = CollisionProjector.CLOSED_BOX;
    private int hiddenFaces;

    private final Vector3f referenceOrigin = new Vector3f();
    private final Vector3f pointA = new Vector3f();
    private final Vector3f pointB = new Vector3f();
    private final Vector3f normal = new Vector3f(0.0F, 1.0F, 0.0F);
    private final Vector3f axisX = new Vector3f(1.0F, 0.0F, 0.0F);
    private final Vector3f axisY = new Vector3f(0.0F, 1.0F, 0.0F);
    private final Vector3f axisZ = new Vector3f(0.0F, 0.0F, 1.0F);
    private final Vector3f halfExtents = new Vector3f();
    private final Vector3f centerModel = new Vector3f();
    private float scaledRadius;
    private float cullRadius = Float.POSITIVE_INFINITY;

    private final Vector3f previousCenter = new Vector3f();
    private final Vector3f previousCullCenter = new Vector3f();
    private final Vector3f previousHalfX = new Vector3f();
    private final Vector3f previousHalfY = new Vector3f();
    private final Vector3f previousHalfZ = new Vector3f();
    private final Vector3f halfAxis = new Vector3f();
    private final Vector3f sweepCenter = new Vector3f();
    private final Vector3f sweepHalfX = new Vector3f();
    private final Vector3f sweepHalfY = new Vector3f();
    private final Vector3f sweepHalfZ = new Vector3f();
    private float previousRadius;
    private float sweepRadius;
    private boolean posed;
    private boolean sweepAvailable;
    private float motionBound = Float.POSITIVE_INFINITY;
    private float cullMotionBound = Float.POSITIVE_INFINITY;

    void setPlane(
            int referenceIndex,
            Vector3f originModel,
            Vector3f pointModel,
            Vector3f normalModel,
            float endpointRadius
    ) {
        setCommon(CollisionProxyKind.PLANE, referenceIndex, originModel,
                endpointRadius);
        restPointA.set(pointModel);
        restNormal.set(normalModel).normalize();
    }

    void setSphere(
            int referenceIndex,
            Vector3f originModel,
            Vector3f centerModel,
            float colliderRadius,
            float endpointRadius
    ) {
        setCommon(CollisionProxyKind.SPHERE, referenceIndex, originModel,
                endpointRadius);
        restPointA.set(centerModel);
        radius = colliderRadius;
    }

    void setCapsule(
            int referenceIndex,
            Vector3f originModel,
            Vector3f startModel,
            Vector3f endModel,
            float colliderRadius,
            float endpointRadius
    ) {
        setCommon(CollisionProxyKind.CAPSULE, referenceIndex, originModel,
                endpointRadius);
        restPointA.set(startModel);
        restPointB.set(endModel);
        radius = colliderRadius;
    }

    void setBox(
            int referenceIndex,
            Vector3f originModel,
            Vector3f boxCenterModel,
            Vector3f axisXModel,
            Vector3f axisYModel,
            Vector3f axisZModel,
            Vector3f halfExtentsModel,
            float endpointRadius,
            int openBoxAxis
    ) {
        setCommon(CollisionProxyKind.BOX, referenceIndex, originModel,
                endpointRadius);
        restPointA.set(boxCenterModel);
        restAxisX.set(axisXModel);
        restAxisY.set(axisYModel);
        restAxisZ.set(axisZModel);
        restHalfExtents.set(halfExtentsModel);
        openAxis = openBoxAxis;
    }

    void prepareNow(
            Matrix4f affineDelta,
            Matrix3f normalTransform,
            float colliderScale
    ) {
        affineDelta.transformPosition(referenceOriginModel, referenceOrigin);
        affineDelta.transformPosition(restPointA, pointA).sub(referenceOrigin);
        centerModel.set(pointA).add(referenceOrigin);
        if (kind == CollisionProxyKind.CAPSULE) {
            affineDelta.transformPosition(restPointB, pointB)
                    .sub(referenceOrigin);
        } else if (kind == CollisionProxyKind.BOX) {
            // Axis lengths carry non-uniform scale into the half extents so
            // the box keeps tracking the animated limb exactly.
            halfExtents.set(
                    restHalfExtents.x * transformAxis(
                            affineDelta, restAxisX, axisX, 1.0F, 0.0F, 0.0F
                    ),
                    restHalfExtents.y * transformAxis(
                            affineDelta, restAxisY, axisY, 0.0F, 1.0F, 0.0F
                    ),
                    restHalfExtents.z * transformAxis(
                            affineDelta, restAxisZ, axisZ, 0.0F, 0.0F, 1.0F
                    )
            );
        } else if (kind == CollisionProxyKind.PLANE) {
            normalTransform.transform(restNormal, normal);
            float lengthSquared = normal.lengthSquared();
            if (!Float.isFinite(lengthSquared) || lengthSquared <= EPSILON) {
                normal.set(restNormal);
            } else {
                normal.div((float) Math.sqrt(lengthSquared));
            }
        }
        scaledRadius = radius * finiteScale(colliderScale);
        /*
         * A plane and a capsule are not bounded around one point, so they opt
         * out of distance culling instead of reporting a wrong bound.
         */
        cullRadius = switch (kind) {
            case BOX -> halfExtents.length();
            case SPHERE -> scaledRadius;
            default -> Float.POSITIVE_INFINITY;
        };
        measureMotion();
    }

    /**
     * How far any point of this collider's surface can have travelled relative
     * to its reference origin since the previous frame, as an upper bound.
     *
     * <p>Measured rather than assumed, because it is what lets a segment reuse
     * a gap it measured earlier instead of re-deriving it: a gap wider than
     * everything that has moved since cannot have closed. The bound has to be
     * an over-estimate for that to be sound, so a box adds the travel of each
     * half-axis to the travel of its centre, which covers every corner however
     * the limb turned. Shapes with no bounded surface, and the first frame of
     * any shape, report infinity and simply defeat the reuse.
     *
     */
    private void measureMotion() {
        sweepAvailable = posed;
        if (posed) {
            sweepCenter.set(previousCenter);
            sweepHalfX.set(previousHalfX);
            sweepHalfY.set(previousHalfY);
            sweepHalfZ.set(previousHalfZ);
            sweepRadius = previousRadius;
        }
        float surfaceChange = switch (kind) {
            case BOX -> halfAxisTravel(axisX, halfExtents.x, previousHalfX)
                    + halfAxisTravel(axisY, halfExtents.y, previousHalfY)
                    + halfAxisTravel(axisZ, halfExtents.z, previousHalfZ);
            case SPHERE -> Math.abs(scaledRadius - previousRadius);
            default -> Float.POSITIVE_INFINITY;
        };
        float travel = previousCenter.distance(pointA) + surfaceChange;
        float cullTravel = previousCullCenter.distance(centerModel)
                + surfaceChange;
        motionBound = posed ? travel : Float.POSITIVE_INFINITY;
        cullMotionBound = posed ? cullTravel : Float.POSITIVE_INFINITY;
        previousCenter.set(pointA);
        previousCullCenter.set(centerModel);
        previousRadius = scaledRadius;
        posed = true;
    }

    /** Travel of one box half-axis tip, recording it for the next frame. */
    private float halfAxisTravel(
            Vector3f axis,
            float extent,
            Vector3f previous
    ) {
        halfAxis.set(axis).mul(extent);
        float travel = previous.distance(halfAxis);
        previous.set(halfAxis);
        return travel;
    }

    /** @see #measureMotion() */
    float motionBound() {
        return motionBound;
    }

    /** Absolute model-space counterpart used by frame-coherent culling. */
    float cullMotionBound() {
        return cullMotionBound;
    }

    boolean sweepAvailable() {
        return sweepAvailable;
    }

    Vector3f sweepCenter() {
        return sweepCenter;
    }

    Vector3f sweepHalfX() {
        return sweepHalfX;
    }

    Vector3f sweepHalfY() {
        return sweepHalfY;
    }

    Vector3f sweepHalfZ() {
        return sweepHalfZ;
    }

    float sweepRadius() {
        return sweepRadius;
    }

    float sweepThickness() {
        return switch (kind) {
            case BOX -> 2.0F * Math.min(
                    Math.min(halfExtents.x, halfExtents.y),
                    halfExtents.z
            );
            case SPHERE -> 2.0F * scaledRadius;
            default -> Float.POSITIVE_INFINITY;
        };
    }

    void resetMotion() {
        posed = false;
        sweepAvailable = false;
        motionBound = Float.POSITIVE_INFINITY;
        cullMotionBound = Float.POSITIVE_INFINITY;
    }

    CollisionProxyKind kind() {
        return kind;
    }

    Vector3f restCenter() {
        return restPointA;
    }

    /** Rest-pose counterpart of {@link #cullRadius()}. */
    float restCullRadius() {
        return switch (kind) {
            case BOX -> restHalfExtents.length();
            case SPHERE -> radius;
            default -> Float.POSITIVE_INFINITY;
        };
    }

    int referenceNodeIndex() {
        return referenceNodeIndex;
    }

    float hitRadius() {
        return hitRadius;
    }

    /**
     * Local axis this box is closed on, or
     * {@link CollisionProjector#CLOSED_BOX}. Culling still uses the drawn
     * extents: the open side exists to survive a frame's worth of travel, not
     * to reach across the model.
     */
    int openAxis() {
        return openAxis;
    }

    int hiddenFaces() {
        return hiddenFaces;
    }

    float scaledRadius() {
        return scaledRadius;
    }

    /**
     * Distance from {@link #centerModel()} that this collider can occupy,
     * excluding the endpoint margin. Infinite where culling does not apply.
     */
    float cullRadius() {
        return cullRadius;
    }

    Vector3f centerModel() {
        return centerModel;
    }

    Vector3f referenceOrigin() {
        return referenceOrigin;
    }

    Vector3f pointA() {
        return pointA;
    }

    Vector3f pointB() {
        return pointB;
    }

    Vector3f normal() {
        return normal;
    }

    Vector3f axisX() {
        return axisX;
    }

    Vector3f axisY() {
        return axisY;
    }

    Vector3f axisZ() {
        return axisZ;
    }

    Vector3f halfExtents() {
        return halfExtents;
    }

    Vector3f restAxisX() {
        return restAxisX;
    }

    Vector3f restAxisY() {
        return restAxisY;
    }

    Vector3f restAxisZ() {
        return restAxisZ;
    }

    Vector3f restHalfExtents() {
        return restHalfExtents;
    }

    void hideFace(int face) {
        if (face >= 0 && face < 6) {
            hiddenFaces |= 1 << face;
        }
    }

    /** Identity of the baked geometry, used to share one shape per collider. */
    Key key() {
        return new Key(
                kind,
                referenceNodeIndex,
                new Vector3f(referenceOriginModel),
                new Vector3f(restPointA),
                new Vector3f(restPointB),
                new Vector3f(restNormal),
                new Vector3f(restAxisX),
                new Vector3f(restAxisY),
                new Vector3f(restAxisZ),
                new Vector3f(restHalfExtents),
                radius,
                hitRadius,
                openAxis
        );
    }

    private void setCommon(
            CollisionProxyKind shapeKind,
            int referenceIndex,
            Vector3f originModel,
            float endpointRadius
    ) {
        kind = shapeKind;
        referenceNodeIndex = referenceIndex;
        referenceOriginModel.set(originModel);
        restPointA.zero();
        restPointB.zero();
        restNormal.set(0.0F, 1.0F, 0.0F);
        restAxisX.set(1.0F, 0.0F, 0.0F);
        restAxisY.set(0.0F, 1.0F, 0.0F);
        restAxisZ.set(0.0F, 0.0F, 1.0F);
        restHalfExtents.zero();
        radius = 0.0F;
        hitRadius = Math.max(0.0F, endpointRadius);
        openAxis = CollisionProjector.CLOSED_BOX;
        hiddenFaces = 0;
    }

    private static float finiteScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.0F, scale) : 1.0F;
    }

    /**
     * Transforms one box axis and returns the scale it picked up.
     */
    private static float transformAxis(
            Matrix4f affineDelta,
            Vector3f restAxis,
            Vector3f output,
            float fallbackX,
            float fallbackY,
            float fallbackZ
    ) {
        affineDelta.transformDirection(restAxis, output);
        float lengthSquared = output.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared <= EPSILON) {
            output.set(fallbackX, fallbackY, fallbackZ);
            return 1.0F;
        }
        float length = (float) Math.sqrt(lengthSquared);
        output.div(length);
        return length;
    }

    record Key(
            CollisionProxyKind kind,
            int referenceNodeIndex,
            Vector3f referenceOrigin,
            Vector3f pointA,
            Vector3f pointB,
            Vector3f normal,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ,
            Vector3f halfExtents,
            float radius,
            float hitRadius,
            int openAxis
    ) {
    }
}
