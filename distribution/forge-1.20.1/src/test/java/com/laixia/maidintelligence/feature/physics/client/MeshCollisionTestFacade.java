package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import org.joml.Vector3f;

/**
 * Narrow bridge from collision scenario packages to package-private test
 * fixtures. Production APIs remain untouched.
 */
public final class MeshCollisionTestFacade {
    private MeshCollisionTestFacade() {
    }

    public static BoneModelSnapshot coreModelFromJson(String json) {
        return BonePhysicsVerificationSupport.coreModelFromJson(json);
    }

    public static BoneModelSnapshot loadCoreModel(String fileName)
            throws Exception {
        return BonePhysicsVerificationSupport.coreModel(
                BonePhysicsVerificationSupport.loadGeoModel(
                        BonePhysicsVerificationSupport.MODEL_DIRECTORY
                                .resolve(fileName)
                )
        );
    }

    public static Vector3f restHalfExtents(CollisionProxy proxy) {
        return MeshCollisionSupport.restHalfExtents(proxy);
    }

    public static void require(boolean condition, String message) {
        BonePhysicsVerificationSupport.require(condition, message);
    }
}
