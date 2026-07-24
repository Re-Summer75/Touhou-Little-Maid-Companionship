package com.laixia.maidintelligence.feature.physics.client.solver.collision;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.PreparedCollisionProxy;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One-sided plane expanded by the endpoint hit radius.
 */
final class PlaneCollisionProxy implements CollisionProxy {
    private final int referenceNodeIndex;
    private final Vector3f referenceOriginModel;
    private final Vector3f pivotFromReference;
    private final Vector3f pointFromReference;
    private final Vector3f normalFromReference;
    private final float hitRadius;
    private final float leverArm;

    PlaneCollisionProxy(
            int referenceNodeIndex,
            Vector3f pivotFromReference,
            Vector3f pointFromReference,
            Vector3f normalFromReference,
            float hitRadius,
            float leverArm
    ) {
        this(
                referenceNodeIndex,
                new Vector3f(),
                pivotFromReference,
                pointFromReference,
                normalFromReference,
                hitRadius,
                leverArm
        );
    }

    PlaneCollisionProxy(
            int referenceNodeIndex,
            Vector3f referenceOriginModel,
            Vector3f pivotFromReference,
            Vector3f pointFromReference,
            Vector3f normalFromReference,
            float hitRadius,
            float leverArm
    ) {
        this.referenceNodeIndex = referenceNodeIndex;
        this.referenceOriginModel = new Vector3f(referenceOriginModel);
        this.pivotFromReference = new Vector3f(pivotFromReference);
        this.pointFromReference = new Vector3f(pointFromReference);
        this.normalFromReference = new Vector3f(normalFromReference);
        float normalLengthSquared =
                this.normalFromReference.lengthSquared();
        if (!Float.isFinite(normalLengthSquared)
                || normalLengthSquared
                <= CollisionProjectionMath.EPSILON
                * CollisionProjectionMath.EPSILON) {
            this.normalFromReference.set(0.0F, 1.0F, 0.0F);
        } else {
            this.normalFromReference.normalize();
        }
        this.hitRadius = Math.max(0.0F, hitRadius);
        this.leverArm = Math.max(
                CollisionProjectionMath.EPSILON,
                leverArm
        );
    }

    @Override
    public CollisionProxyKind kind() {
        return CollisionProxyKind.PLANE;
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
                pointFromReference,
                scratch.pointA
        ).add(referenceOriginModel);
        referenceRestOrientation.transform(
                normalFromReference,
                scratch.normal
        ).normalize();
        output.setPlane(
                referenceNodeIndex,
                referenceOriginModel,
                scratch.pointA,
                scratch.normal,
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
        return CollisionProjector.projectPlane(
                direction,
                scratch.pivot,
                scratch.pointA,
                scratch.normal,
                hitRadius,
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
        return CollisionProjector.planeClearance(
                direction,
                scratch.pivot,
                scratch.pointA,
                scratch.normal,
                hitRadius,
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
                pointFromReference,
                scratch.pointA
        );
        referenceOrientation.transform(
                normalFromReference,
                scratch.normal
        ).normalize();
    }
}
