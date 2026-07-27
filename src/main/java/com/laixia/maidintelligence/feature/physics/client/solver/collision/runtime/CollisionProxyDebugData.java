package com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySource;
import org.joml.Vector3f;

/**
 * Mutable allocation-free snapshot of one frame-prepared collision proxy.
 */
public final class CollisionProxyDebugData {
    public CollisionProxyKind kind;
    public CollisionProxySource source;
    public int referenceNodeIndex = -1;
    public final Vector3f referenceOrigin = new Vector3f();
    public final Vector3f runtimePivot = new Vector3f();
    public final Vector3f planePoint = new Vector3f();
    public final Vector3f planeNormal = new Vector3f();
    public final Vector3f sphereCenter = new Vector3f();
    public final Vector3f capsuleStart = new Vector3f();
    public final Vector3f capsuleEnd = new Vector3f();
    public final Vector3f boxCenter = new Vector3f();
    public final Vector3f boxHalfExtents = new Vector3f();
    public final Vector3f boxAxisX = new Vector3f();
    public final Vector3f boxAxisY = new Vector3f();
    public final Vector3f boxAxisZ = new Vector3f();
    /** Box axis whose positive face is the only closed one, or {@code -1}. */
    public int boxOpenAxis = -1;
    public float sphereRadius;
    public float capsuleRadius;
    public float scaledHitRadius;
    public float leverArm;
    public float clearance = Float.NaN;
    public boolean penetrating;

    public void reset() {
        kind = null;
        source = null;
        referenceNodeIndex = -1;
        referenceOrigin.zero();
        runtimePivot.zero();
        planePoint.zero();
        planeNormal.zero();
        sphereCenter.zero();
        capsuleStart.zero();
        capsuleEnd.zero();
        boxCenter.zero();
        boxHalfExtents.zero();
        boxAxisX.zero();
        boxAxisY.zero();
        boxAxisZ.zero();
        boxOpenAxis = -1;
        sphereRadius = 0.0F;
        capsuleRadius = 0.0F;
        scaledHitRadius = 0.0F;
        leverArm = 0.0F;
        clearance = Float.NaN;
        penetrating = false;
    }
}
