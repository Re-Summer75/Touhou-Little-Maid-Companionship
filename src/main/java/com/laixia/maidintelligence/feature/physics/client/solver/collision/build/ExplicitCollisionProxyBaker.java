package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxies;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySource;
import org.joml.Vector3f;

import java.util.List;

/**
 * Bakes schema-v3 model-space authoring values into reference-local records.
 */
final class ExplicitCollisionProxyBaker {
    private static final float PIXELS_PER_BLOCK = 16.0F;

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
                        vector(plane.point()),
                        vector(plane.normal()),
                        context.hitRadius(spec.hitRadius(), 0.0F)
                )));
            } else if (shape
                    instanceof PhysicsBoneSelectionPlan.CollisionShape.Sphere sphere) {
                float radius = sphere.radius() / PIXELS_PER_BLOCK;
                output.add(explicit(context.sphere(
                        entry.reference(),
                        vector(sphere.center()),
                        radius,
                        context.hitRadius(spec.hitRadius(), radius)
                )));
            } else if (shape
                    instanceof PhysicsBoneSelectionPlan.CollisionShape.Capsule capsule) {
                float radius = capsule.radius() / PIXELS_PER_BLOCK;
                output.add(explicit(context.capsule(
                        entry.reference(),
                        vector(capsule.start()),
                        vector(capsule.end()),
                        radius,
                        context.hitRadius(spec.hitRadius(), radius)
                )));
            }
        }
    }

    private static Vector3f vector(
            PhysicsBoneSelectionPlan.CollisionVector vector
    ) {
        return new Vector3f(
                vector.x() / PIXELS_PER_BLOCK,
                vector.y() / PIXELS_PER_BLOCK,
                vector.z() / PIXELS_PER_BLOCK
        );
    }

    private static CollisionProxy explicit(CollisionProxy proxy) {
        return CollisionProxies.withSource(
                proxy,
                CollisionProxySource.EXPLICIT
        );
    }
}
