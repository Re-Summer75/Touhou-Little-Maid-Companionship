package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionScratch;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Solver-owned scratch objects reused by every active node.
 */
final class SpringBoneScratch {
    final Quaternionf rootOrientation = new Quaternionf();
    final Quaternionf localRotation = new Quaternionf();
    final Quaternionf animationRotation = new Quaternionf();
    final Quaternionf physicalDelta = new Quaternionf();
    final Quaternionf referenceInverse = new Quaternionf();
    final Quaternionf referenceTransport = new Quaternionf();

    final Vector3f boneAxis = new Vector3f();
    final Vector3f authoredRestDirection = new Vector3f();
    final Vector3f restDirection = new Vector3f();
    final Vector3f poseDriveTangent = new Vector3f();
    final Vector3f poseDriveBinormal = new Vector3f();
    final Vector3f nextDirection = new Vector3f();
    final Vector3f localDirection = new Vector3f();
    final Vector3f deflectionAxis = new Vector3f();
    final Vector3f rotationEuler = new Vector3f();
    final Vector3f pivotOffset = new Vector3f();
    final Vector3f pivotScratch = new Vector3f();
    final Vector3f endpointScratch = new Vector3f();
    final Vector3f constraintRight = new Vector3f();
    final CollisionScratch collision = new CollisionScratch();
    float runtimeSafetyScale = 1.0F;
}
