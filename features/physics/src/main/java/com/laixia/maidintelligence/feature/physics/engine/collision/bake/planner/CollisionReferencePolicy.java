package com.laixia.maidintelligence.feature.physics.engine.collision.bake.planner;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;

/**
 * Narrow facade over package-private collision-reference safety checks.
 */
public final class CollisionReferencePolicy {
    private CollisionReferencePolicy() {
    }

    public static boolean isSafe(
            PhysicsBoneGeometry.Node reference,
            PhysicsBoneGeometry.Node driven,
            PhysicsBoneSelectionPlan plan,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        return CollisionReferenceSafety.isSafe(
                reference,
                driven,
                plan,
                geometry
        );
    }
}
