package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import org.joml.Vector3f;

/**
 * Narrow bridge for wind scenarios that live below the client test package.
 */
public final class EnvironmentalWindVerificationFacade {
    private EnvironmentalWindVerificationFacade() {
    }

    public static BoneModelSnapshot coreModelFromJson(String json) {
        return BonePhysicsVerificationSupport.coreModelFromJson(json);
    }

    public static void require(boolean condition, String message) {
        BonePhysicsVerificationSupport.require(condition, message);
    }

    public static void requireNear(
            float actual,
            float expected,
            float tolerance,
            String message
    ) {
        BonePhysicsVerificationSupport.requireNear(
                actual,
                expected,
                tolerance,
                message
        );
    }

    public static void requireVectorNear(
            Vector3f actual,
            Vector3f expected,
            float tolerance,
            String message
    ) {
        BonePhysicsVerificationSupport.requireVectorNear(
                actual,
                expected,
                tolerance,
                message
        );
    }
}
