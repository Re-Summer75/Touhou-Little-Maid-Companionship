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

    public CollisionScratch() {
    }
}
