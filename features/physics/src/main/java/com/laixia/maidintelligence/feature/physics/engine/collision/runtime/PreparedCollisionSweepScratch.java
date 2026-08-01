package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import org.joml.Vector3f;

/**
 * Reused interpolation vectors for selective swept-contact checks.
 */
final class PreparedCollisionSweepScratch {
    final Vector3f center = new Vector3f();
    final Vector3f pivot = new Vector3f();
    final Vector3f axisX = new Vector3f(1.0F, 0.0F, 0.0F);
    final Vector3f axisY = new Vector3f(0.0F, 1.0F, 0.0F);
    final Vector3f axisZ = new Vector3f(0.0F, 0.0F, 1.0F);
    final Vector3f half = new Vector3f();
    final Vector3f direction = new Vector3f();
    final Vector3f contactStart = new Vector3f();
}
