package com.laixia.maidintelligence.feature.physics.client.solver.collision;

/**
 * Supported fixed-length endpoint collision shapes.
 */
public enum CollisionProxyKind {
    PLANE,
    SPHERE,
    CAPSULE,
    /** Oriented box; exact for the cube meshes these models are built from. */
    BOX
}
