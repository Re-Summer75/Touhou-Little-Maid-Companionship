package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.collision.MeshCollisionReachabilityCullingVerification;
import com.laixia.maidintelligence.feature.physics.client.collision.MeshCollisionRuntimeProjectionVerification;

/**
 * Covers the mesh-derived collision boxes: which rigid cubes become
 * colliders, that reachable ones are kept in full, and that they actually
 * block a driven endpoint at runtime.
 */
final class MeshCollisionVerification {
    private MeshCollisionVerification() {
    }

    static void run() throws Exception {
        MeshCollisionReachabilityCullingVerification.run();
        MeshCollisionRuntimeProjectionVerification.run();
        MeshCollisionReachabilityCullingVerification.runCubeAudit();
    }
}
