package com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProjector;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionScratch;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PreparedCollisionProxy {
    private static final float EPSILON = 1.0E-12F;
    private CollisionProxyKind kind = CollisionProxyKind.SPHERE;
    private CollisionProxySource source = CollisionProxySource.AUTOMATIC;
    private int referenceNodeIndex = -1;
    private final Vector3f referenceOriginModel = new Vector3f();
    private final Vector3f restPointA = new Vector3f();
    private final Vector3f restPointB = new Vector3f();
    private final Vector3f restNormal = new Vector3f(0.0F, 1.0F, 0.0F);
    private final Vector3f referenceOrigin = new Vector3f();
    private final Vector3f pivot = new Vector3f();
    private final Vector3f pointA = new Vector3f();
    private final Vector3f pointB = new Vector3f();
    private final Vector3f normal = new Vector3f(0.0F, 1.0F, 0.0F);
    private float radius;
    private float hitRadius;
    private float leverArm = 1.0F;
    private float preparedLeverArm = 1.0F;
    private float preparedRadius;
    private float preparedHitRadius;

    public void setPlane(
            int referenceIndex,
            Vector3f originModel,
            Vector3f pointModel,
            Vector3f normalModel,
            float endpointRadius,
            float fixedLeverArm
    ) {
        setCommon(CollisionProxyKind.PLANE, referenceIndex, originModel,
                endpointRadius, fixedLeverArm);
        restPointA.set(pointModel);
        restNormal.set(normalModel).normalize();
    }

    public void setSphere(
            int referenceIndex,
            Vector3f originModel,
            Vector3f centerModel,
            float colliderRadius,
            float endpointRadius,
            float fixedLeverArm
    ) {
        setCommon(CollisionProxyKind.SPHERE, referenceIndex, originModel,
                endpointRadius, fixedLeverArm);
        restPointA.set(centerModel);
        radius = colliderRadius;
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
        setCommon(CollisionProxyKind.CAPSULE, referenceIndex, originModel,
                endpointRadius, fixedLeverArm);
        restPointA.set(startModel);
        restPointB.set(endModel);
        radius = colliderRadius;
    }

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
        affineDelta.transformPosition(referenceOriginModel, referenceOrigin);
        pivot.set(runtimePivotModel).sub(referenceOrigin);
        affineDelta.transformPosition(restPointA, pointA).sub(referenceOrigin);
        if (kind == CollisionProxyKind.CAPSULE) {
            affineDelta.transformPosition(restPointB, pointB)
                    .sub(referenceOrigin);
        } else if (kind == CollisionProxyKind.PLANE) {
            normalTransform.transform(restNormal, normal);
            float lengthSquared = normal.lengthSquared();
            if (!Float.isFinite(lengthSquared) || lengthSquared <= EPSILON) {
                normal.set(restNormal);
            } else {
                normal.div((float) Math.sqrt(lengthSquared));
            }
        }
        float shapeScale = finiteScale(colliderScale);
        float hitScale = finiteScale(endpointScale);
        preparedHitRadius = hitRadius * hitScale;
        preparedRadius = radius * shapeScale + preparedHitRadius;
        preparedLeverArm = Float.isFinite(runtimeLeverArm)
                ? Math.max(1.0E-6F, runtimeLeverArm)
                : leverArm;
    }

    public boolean project(Vector3f direction, CollisionScratch scratch) {
        return switch (kind) {
            case PLANE -> CollisionProjector.projectPlane(
                    direction, pivot, pointA, normal,
                    preparedHitRadius, preparedLeverArm, scratch
            );
            case SPHERE -> CollisionProjector.projectSphere(
                    direction, pivot, pointA, preparedRadius,
                    preparedLeverArm, scratch
            );
            case CAPSULE -> CollisionProjector.projectCapsule(
                    direction, pivot, pointA, pointB, preparedRadius,
                    preparedLeverArm, scratch
            );
        };
    }

    public float clearance(Vector3f direction, CollisionScratch scratch) {
        return switch (kind) {
            case PLANE -> CollisionProjector.planeClearance(
                    direction, pivot, pointA, normal,
                    preparedHitRadius, preparedLeverArm, scratch
            );
            case SPHERE -> CollisionProjector.sphereClearance(
                    direction, pivot, pointA, preparedRadius,
                    preparedLeverArm, scratch
            );
            case CAPSULE -> CollisionProjector.capsuleClearance(
                    direction, pivot, pointA, pointB, preparedRadius,
                    preparedLeverArm, scratch
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
        return referenceNodeIndex;
    }

    public CollisionProxyKind kind() {
        return kind;
    }

    void setSource(CollisionProxySource value) { source = value; }
    CollisionProxySource source() { return source; }
    Vector3f referenceOrigin() { return referenceOrigin; }
    Vector3f pivot() { return pivot; }
    Vector3f pointA() { return pointA; }
    Vector3f pointB() { return pointB; }
    Vector3f normal() { return normal; }
    float preparedRadius() { return preparedRadius; }
    float preparedHitRadius() { return preparedHitRadius; }
    float preparedLeverArm() { return preparedLeverArm; }

    private static float finiteScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.0F, scale) : 1.0F;
    }

    private void setCommon(
            CollisionProxyKind proxyKind,
            int referenceIndex,
            Vector3f originModel,
            float endpointRadius,
            float fixedLeverArm
    ) {
        kind = proxyKind;
        referenceNodeIndex = referenceIndex;
        referenceOriginModel.set(originModel);
        restPointA.zero();
        restPointB.zero();
        restNormal.set(0.0F, 1.0F, 0.0F);
        radius = 0.0F;
        hitRadius = Math.max(0.0F, endpointRadius);
        leverArm = Math.max(1.0E-6F, fixedLeverArm);
        preparedLeverArm = leverArm;
    }
}
