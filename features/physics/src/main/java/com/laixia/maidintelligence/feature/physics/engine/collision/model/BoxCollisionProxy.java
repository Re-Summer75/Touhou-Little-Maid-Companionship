package com.laixia.maidintelligence.feature.physics.engine.collision.model;


import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.PreparedCollisionProxy;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Oriented box collision. Cube meshes are boxes, so a fitted box is an exact
 * collider rather than an approximation, and it needs no iterative search.
 *
 * <p>An {@code openAxis} other than {@link CollisionProjector#CLOSED_BOX}
 * turns the box into a half-open prism, closed only on the positive face of
 * that axis. The drawn and tested extents stay the mesh's own, which a solid
 * box thick enough to stop a fast endpoint could not do.
 */
final class BoxCollisionProxy implements CollisionProxy {
    private final int referenceNodeIndex;
    private final Vector3f referenceOriginModel;
    private final Vector3f pivotFromReference;
    private final Vector3f centerFromReference;
    private final Vector3f axisXFromReference;
    private final Vector3f axisYFromReference;
    private final Vector3f axisZFromReference;
    private final Vector3f halfExtents;
    private final float hitRadius;
    private final int openAxis;
    private final float leverArm;

    BoxCollisionProxy(
            int referenceNodeIndex,
            Vector3f referenceOriginModel,
            Vector3f pivotFromReference,
            Vector3f centerFromReference,
            Vector3f axisXFromReference,
            Vector3f axisYFromReference,
            Vector3f axisZFromReference,
            Vector3f halfExtents,
            float hitRadius,
            int openAxis,
            float leverArm
    ) {
        this.referenceNodeIndex = referenceNodeIndex;
        this.referenceOriginModel = new Vector3f(referenceOriginModel);
        this.pivotFromReference = new Vector3f(pivotFromReference);
        this.centerFromReference = new Vector3f(centerFromReference);
        this.axisXFromReference = normalized(axisXFromReference, 1.0F, 0.0F, 0.0F);
        this.axisYFromReference = normalized(axisYFromReference, 0.0F, 1.0F, 0.0F);
        this.axisZFromReference = normalized(axisZFromReference, 0.0F, 0.0F, 1.0F);
        this.halfExtents = new Vector3f(
                Math.max(0.0F, halfExtents.x),
                Math.max(0.0F, halfExtents.y),
                Math.max(0.0F, halfExtents.z)
        );
        this.hitRadius = Math.max(0.0F, hitRadius);
        this.openAxis = openAxis >= 0 && openAxis <= 2
                ? openAxis
                : CollisionProjector.CLOSED_BOX;
        this.leverArm = Math.max(CollisionProjectionMath.EPSILON, leverArm);
    }

    @Override
    public CollisionProxyKind kind() {
        return CollisionProxyKind.BOX;
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
        transformAxes(referenceRestOrientation, scratch);
        output.setBox(
                referenceNodeIndex,
                referenceOriginModel,
                scratch.pointA,
                scratch.axisX,
                scratch.axisY,
                scratch.axisZ,
                halfExtents,
                hitRadius,
                openAxis,
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
        return CollisionProjector.projectBox(
                direction,
                scratch.pivot,
                scratch.pointA,
                scratch.axisX,
                scratch.axisY,
                scratch.axisZ,
                halfExtents,
                hitRadius,
                openAxis,
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
        return CollisionProjector.boxClearance(
                direction,
                scratch.pivot,
                scratch.pointA,
                scratch.axisX,
                scratch.axisY,
                scratch.axisZ,
                halfExtents,
                hitRadius,
                openAxis,
                leverArm,
                scratch
        );
    }

    private void transform(
            Quaternionf referenceOrientation,
            CollisionScratch scratch
    ) {
        referenceOrientation.transform(pivotFromReference, scratch.pivot);
        referenceOrientation.transform(centerFromReference, scratch.pointA);
        transformAxes(referenceOrientation, scratch);
    }

    private void transformAxes(
            Quaternionf orientation,
            CollisionScratch scratch
    ) {
        orientation.transform(axisXFromReference, scratch.axisX);
        orientation.transform(axisYFromReference, scratch.axisY);
        orientation.transform(axisZFromReference, scratch.axisZ);
    }

    private static Vector3f normalized(
            Vector3f axis,
            float fallbackX,
            float fallbackY,
            float fallbackZ
    ) {
        Vector3f copy = new Vector3f(axis);
        float lengthSquared = copy.lengthSquared();
        if (!Float.isFinite(lengthSquared)
                || lengthSquared <= CollisionProjectionMath.EPSILON) {
            return new Vector3f(fallbackX, fallbackY, fallbackZ);
        }
        return copy.div((float) Math.sqrt(lengthSquared));
    }
}
