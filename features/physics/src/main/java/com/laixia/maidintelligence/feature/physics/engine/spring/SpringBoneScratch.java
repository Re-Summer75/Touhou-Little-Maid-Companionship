package com.laixia.maidintelligence.feature.physics.engine.spring;


import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
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
    final Vector3f poseDriveBias = new Vector3f();
    final Vector3f nextDirection = new Vector3f();
    /** Pose entering constraint projection, kept to measure what it changed. */
    final Vector3f projectionStart = new Vector3f();
    /** Undamped projection result, retained to identify competing overlaps. */
    final Vector3f projectedDirection = new Vector3f();
    /** Pose entering the last collision pass, to isolate what that pass moved. */
    final Vector3f collisionStart = new Vector3f();
    /** Sum of what the collision passes moved, which points along the surface. */
    final Vector3f collisionNormal = new Vector3f();
    /**
     * Whether collision, specifically, moved the pose this frame. A swing limit
     * moves it too, and only a collider stands for a surface a part can rest on.
     */
    boolean collisionCorrected;
    /**
     * Whether a swing limit moved the pose this frame, kept beside the collision
     * flag so a diagnostic can tell which bound a segment is being pushed by. The
     * projection counters cannot: they are totalled across the whole model, so a
     * single segment's trace reads the entire skirt's activity.
     */
    boolean swingCorrected;
    /** External acceleration for the step, before contact support trims it. */
    final Vector3f appliedForce = new Vector3f();
    /** Velocity carried from the previous step, before support trims it. */
    final Vector3f carriedVelocity = new Vector3f();
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
