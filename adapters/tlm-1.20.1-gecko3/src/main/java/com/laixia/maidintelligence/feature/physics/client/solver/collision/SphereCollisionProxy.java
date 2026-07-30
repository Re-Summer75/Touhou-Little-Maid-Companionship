package com.laixia.maidintelligence.feature.physics.client.solver.collision;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.PreparedCollisionProxy;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * External sphere collision for a fixed-length endpoint.
 */
final class SphereCollisionProxy implements CollisionProxy {
    private final int referenceNodeIndex;
    private final Vector3f referenceOriginModel;
    private final Vector3f pivotFromReference;
    private final Vector3f centerFromReference;
    private final float radius;
    private final float hitRadius;
    private final float leverArm;

    SphereCollisionProxy(
            int referenceNodeIndex,
            Vector3f pivotFromReference,
            Vector3f centerFromReference,
            float radius,
            float hitRadius,
            float leverArm
    ) {
        this(
                referenceNodeIndex,
                new Vector3f(),
                pivotFromReference,
                centerFromReference,
                radius,
                hitRadius,
                leverArm
        );
    }

    SphereCollisionProxy(
            int referenceNodeIndex,
            Vector3f referenceOriginModel,
            Vector3f pivotFromReference,
            Vector3f centerFromReference,
            float radius,
            float hitRadius,
            float leverArm
    ) {
        this.referenceNodeIndex = referenceNodeIndex;
        this.referenceOriginModel = new Vector3f(referenceOriginModel);
        this.pivotFromReference = new Vector3f(pivotFromReference);
        this.centerFromReference = new Vector3f(centerFromReference);
        this.radius = Math.max(0.0F, radius);
        this.hitRadius = Math.max(0.0F, hitRadius);
        this.leverArm = Math.max(
                CollisionProjectionMath.EPSILON,
                leverArm
        );
    }

    @Override
    public CollisionProxyKind kind() {
        return CollisionProxyKind.SPHERE;
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
                centerFromReference,
                scratch.pointA
        ).add(referenceOriginModel);
        output.setSphere(
                referenceNodeIndex,
                referenceOriginModel,
                scratch.pointA,
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
        return CollisionProjector.projectSphere(
                direction,
                scratch.pivot,
                scratch.pointA,
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
        return CollisionProjector.sphereClearance(
                direction,
                scratch.pivot,
                scratch.pointA,
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
                centerFromReference,
                scratch.pointA
        );
    }
}
