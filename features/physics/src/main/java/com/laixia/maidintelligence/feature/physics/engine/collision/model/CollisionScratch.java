package com.laixia.maidintelligence.feature.physics.engine.collision.model;


import com.laixia.maidintelligence.feature.physics.engine.collision.model.projection.CollisionProjectionPrimitives;
import org.joml.Vector3f;

/**
 * Solver-owned vectors shared by all proxy projections.
 */
public final class CollisionScratch {
    final Vector3f pivot = new Vector3f();
    final Vector3f tip = new Vector3f();
    final Vector3f pointA = new Vector3f();
    final Vector3f pointB = new Vector3f();
    final Vector3f normal = new Vector3f();
    final Vector3f tangent = new Vector3f();
    final Vector3f segment = new Vector3f();
    final Vector3f closest = new Vector3f();
    final Vector3f passStart = new Vector3f();
    final Vector3f axisX = new Vector3f(1.0F, 0.0F, 0.0F);
    final Vector3f axisY = new Vector3f(0.0F, 1.0F, 0.0F);
    final Vector3f axisZ = new Vector3f(0.0F, 0.0F, 1.0F);
    final Vector3f half = new Vector3f();
    final Vector3f local = new Vector3f();
    /**
     * Half extents of the driven sheet in its own frame, and that frame, so a
     * projection can ask how far the sheet reaches along the contact normal it
     * has just computed.
     *
     * <p>This is the part a scalar hit radius cannot express. Padding the radius
     * holds the endpoint off every collider at once and closes the gaps a
     * segment needs to sit between them; asking for the extent along one normal
     * pads only the direction the contact is actually on.
     */
    final Vector3f meshHalf = new Vector3f();
    final Vector3f meshAxisX = new Vector3f(1.0F, 0.0F, 0.0F);
    final Vector3f meshAxisY = new Vector3f(0.0F, 1.0F, 0.0F);
    final Vector3f meshAxisZ = new Vector3f(0.0F, 0.0F, 1.0F);
    /** Face a buried endpoint left through last time, or {@code -1}. */
    int exitFace = CollisionProjector.NO_FACE;
    /**
     * Gap the last projection measured on its way to deciding it had nothing
     * to push, or a negative value when it pushed. A projection has to find
     * this distance before it can act on it, so reading it back costs nothing
     * and tells the caller how long the answer stays true.
     */
    float measuredClearance = -1.0F;
    /**
     * Whether the last relaxation ran out of passes while still moving the
     * segment, which is what colliders demanding opposite things looks like
     * from the inside: each answers in turn, each undoes the last, and no
     * number of passes converges because no legal pose exists.
     */
    private boolean unresolved;
    /**
     * How near the segment sits to a collider, measured apart from whether one
     * pushed it. A segment settled exactly on a surface is pushed by nothing, so
     * the push is no evidence the surface is gone.
     */
    private float restClearance = Float.MAX_VALUE;

    public CollisionScratch() {
    }

    public boolean unresolved() {
        return unresolved;
    }

    public void setUnresolved(boolean value) {
        unresolved = value;
    }

    /**
     * Clearance to the nearest live collider after the pass, in blocks.
     *
     * <p>Reported as a distance rather than a touching/not verdict because the
     * threshold is not one value. Support has to be held across a wide band, since
     * a grazing contact reads clear for a frame at a time while still resting, and
     * earned across a narrow one, since a segment granted support before it
     * arrives is held short of the surface it was falling onto. One boolean forced
     * both to share a band and left a hair strand hanging 0.089 blocks off a mesh.
     */
    public float restClearance() {
        return restClearance;
    }

    public void setRestClearance(float value) {
        restClearance = value;
    }

    public int exitFace() {
        return exitFace;
    }

    public void setExitFace(int face) {
        exitFace = face;
    }

    public float measuredClearance() {
        return measuredClearance;
    }

    /**
     * Marks the gap unknown, so a shape whose projection does not report one
     * cannot be read as having measured a gap the previous shape left behind.
     */
    public void clearMeasuredClearance() {
        measuredClearance = -1.0F;
    }

    /**
     * Describes the driven sheet to the projections. Half extents of zero mean
     * an endpoint with no width, which is the previous behaviour exactly.
     */
    public void setMeshExtent(
            Vector3f half,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ
    ) {
        meshHalf.set(half);
        meshAxisX.set(axisX);
        meshAxisY.set(axisY);
        meshAxisZ.set(axisZ);
    }

    public void clearMeshExtent() {
        meshHalf.zero();
    }

    /**
     * How far the sheet reaches from its axis along {@code normal}: the support
     * function of an oriented box, which is the sum of each half extent
     * projected onto that direction.
     */
    float meshReach(Vector3f normal) {
        if (meshHalf.lengthSquared()
                <= CollisionProjectionPrimitives.EPSILON) {
            return 0.0F;
        }
        return Math.abs(meshAxisX.dot(normal)) * meshHalf.x
                + Math.abs(meshAxisY.dot(normal)) * meshHalf.y
                + Math.abs(meshAxisZ.dot(normal)) * meshHalf.z;
    }
}
