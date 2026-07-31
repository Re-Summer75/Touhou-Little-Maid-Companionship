package com.laixia.maidintelligence.feature.physics.engine.collision.bake.explicit;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import org.joml.Vector3f;

/**
 * Converts authored Gecko-pixel collision coordinates into solver units.
 */
public final class ExplicitCollisionCoordinates {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private ExplicitCollisionCoordinates() {
    }

    public static Vector3f toModelUnits(
            PhysicsBoneSelectionPlan.CollisionVector vector
    ) {
        return new Vector3f(
                vector.x() / PIXELS_PER_BLOCK,
                vector.y() / PIXELS_PER_BLOCK,
                vector.z() / PIXELS_PER_BLOCK
        );
    }

    public static float toModelUnits(float pixels) {
        return pixels / PIXELS_PER_BLOCK;
    }
}
