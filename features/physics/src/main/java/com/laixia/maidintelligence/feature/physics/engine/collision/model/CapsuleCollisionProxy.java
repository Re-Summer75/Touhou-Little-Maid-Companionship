package com.laixia.maidintelligence.feature.physics.engine.collision.model;


import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.PreparedCollisionProxy;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * External capsule collision using iterative fixed-length half-space projection.
 */
final class CapsuleCollisionProxy implements CollisionProxy {
    private final int referenceNodeIndex;
    private final Vector3f referenceOriginModel;
    private final Vector3f pivotFromReference;
    private final Vector3f startFromReference;
    private final Vector3f endFromReference;
    private final float radius;
    private final float hitRadius;
    private final float leverArm;

    CapsuleCollisionProxy(
            int referenceNodeIndex,
            Vector3f pivotFromReference,
            Vector3f startFromReference,
            Vector3f endFromReference,
            float radius,
            float hitRadius,
            float leverArm
    ) {
        this(
                referenceNodeIndex,
                new Vector3f(),
                pivotFromReference,
                startFromReference,
                endFromReference,
                radius,
                hitRadius,
                leverArm
        );
    }

    CapsuleCollisionProxy(
            int referenceNodeIndex,
            Vector3f referenceOriginModel,
            Vector3f pivotFromReference,
            Vector3f startFromReference,
            Vector3f endFromReference,
            float radius,
            float hitRadius,
            float leverArm
    ) {
        this.referenceNodeIndex = referenceNodeIndex;
        this.referenceOriginModel = new Vector3f(referenceOriginModel);
        this.pivotFromReference = new Vector3f(pivotFromReference);
        this.startFromReference = new Vector3f(startFromReference);
        this.endFromReference = new Vector3f(endFromReference);
        this.radius = Math.max(0.0F, radius);
        this.hitRadius = Math.max(0.0F, hitRadius);
        this.leverArm = Math.max(
                CollisionProjectionMath.EPSILON,
                leverArm
        );
    }

    @Override
    public CollisionProxyKind kind() {
        return CollisionProxyKind.CAPSULE;
    }

    @Override
    public int referenceNodeIndex() {
        return referenceNodeIndex;
    }

    @Override
    public Vector3f copyReferenceOrigin(Vector3f output) {
        return output.set(referenceOriginModel);
    }

    @Override
    public float hitRadius() {
        return hitRadius;
    }

    @Override
    public float leverArm() {
        return leverArm;
    }

    @Override
    public void copyStaticShape(
            Quaternionf referenceRestOrientation,
            PreparedCollisionProxy output,
            CollisionScratch scratch
    ) {
        referenceRestOrientation.transform(
                startFromReference,
                scratch.pointA
        ).add(referenceOriginModel);
        referenceRestOrientation.transform(
                endFromReference,
                scratch.pointB
        ).add(referenceOriginModel);
        output.setCapsule(
                referenceNodeIndex,
                referenceOriginModel,
                scratch.pointA,
                scratch.pointB,
                radius,
                hitRadius,
                leverArm
        );
    }

    @Override
    public boolean project(
            Vector3f direction,
            Quaternionf referenceOrientation,
            CollisionScratch scratch
    ) {
        transform(referenceOrientation, scratch);
        return CollisionProjector.projectCapsule(
                direction,
                scratch.pivot,
                scratch.pointA,
                scratch.pointB,
                radius + hitRadius,
                leverArm,
                scratch
        );
    }

    @Override
    public float clearance(
            Vector3f direction,
            Quaternionf referenceOrientation,
            CollisionScratch scratch
    ) {
        transform(referenceOrientation, scratch);
        return CollisionProjector.capsuleClearance(
                direction,
                scratch.pivot,
                scratch.pointA,
                scratch.pointB,
                radius + hitRadius,
                leverArm,
                scratch
        );
    }

    private void transform(
            Quaternionf referenceOrientation,
            CollisionScratch scratch
    ) {
        referenceOrientation.transform(
                pivotFromReference,
                scratch.pivot
        );
        referenceOrientation.transform(
                startFromReference,
                scratch.pointA
        );
        referenceOrientation.transform(
                endFromReference,
                scratch.pointB
        );
    }
}
