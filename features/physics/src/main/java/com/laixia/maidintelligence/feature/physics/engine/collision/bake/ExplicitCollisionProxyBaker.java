package com.laixia.maidintelligence.feature.physics.engine.collision.bake;


import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.explicit.ExplicitCollisionCoordinates;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxies;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;

import java.util.List;

/**
 * Bakes schema-v3 model-space authoring values into reference-local records.
 */
final class ExplicitCollisionProxyBaker {
    private ExplicitCollisionProxyBaker() {
    }

    static void append(
            CollisionProxyPlan plan,
            CollisionBakeContext context,
            List<CollisionProxy> output
    ) {
        for (CollisionProxyPlan.Explicit entry : plan.explicit()) {
            PhysicsBoneSelectionPlan.CollisionProxySpec spec = entry.spec();
            PhysicsBoneSelectionPlan.CollisionShape shape = spec.shape();
            if (shape instanceof PhysicsBoneSelectionPlan.CollisionShape.Plane plane) {
                output.add(explicit(context.plane(
                        entry.reference(),
                        ExplicitCollisionCoordinates.toModelUnits(plane.point()),
                        ExplicitCollisionCoordinates.toModelUnits(plane.normal()),
                        context.hitRadius(spec.hitRadius(), 0.0F)
                )));
            } else if (shape
                    instanceof PhysicsBoneSelectionPlan.CollisionShape.Sphere sphere) {
                float radius = ExplicitCollisionCoordinates.toModelUnits(
                        sphere.radius()
                );
                output.add(explicit(context.sphere(
                        entry.reference(),
                        ExplicitCollisionCoordinates.toModelUnits(
                                sphere.center()
                        ),
                        radius,
                        context.hitRadius(spec.hitRadius(), radius)
                )));
            } else if (shape
                    instanceof PhysicsBoneSelectionPlan.CollisionShape.Capsule capsule) {
                float radius = ExplicitCollisionCoordinates.toModelUnits(
                        capsule.radius()
                );
                output.add(explicit(context.capsule(
                        entry.reference(),
                        ExplicitCollisionCoordinates.toModelUnits(
                                capsule.start()
                        ),
                        ExplicitCollisionCoordinates.toModelUnits(capsule.end()),
                        radius,
                        context.hitRadius(spec.hitRadius(), radius)
                )));
            }
        }
    }

    private static CollisionProxy explicit(CollisionProxy proxy) {
        return CollisionProxies.withSource(
                proxy,
                CollisionProxySource.EXPLICIT
        );
    }
}
