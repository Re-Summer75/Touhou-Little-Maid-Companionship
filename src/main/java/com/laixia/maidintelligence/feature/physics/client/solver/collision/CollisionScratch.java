package com.laixia.maidintelligence.feature.physics.client.solver.collision;

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
    /** Face a buried endpoint left through last time, or {@code -1}. */
    int exitFace = CollisionProjector.NO_FACE;

    public CollisionScratch() {
    }

    public int exitFace() {
        return exitFace;
    }

    public void setExitFace(int face) {
        exitFace = face;
    }
}
