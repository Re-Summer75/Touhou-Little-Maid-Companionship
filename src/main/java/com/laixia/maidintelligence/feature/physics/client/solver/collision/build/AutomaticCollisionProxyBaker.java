package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxy;
import org.joml.Vector3f;

import java.util.List;

/**
 * Bakes the conservative whitelist of automatic collision proxies.
 */
final class AutomaticCollisionProxyBaker {
    private static final float EPSILON = 1.0E-6F;

    private AutomaticCollisionProxyBaker() {
    }

    static void append(
            CollisionProxyPlan plan,
            CollisionBakeContext context,
            BodyCollisionGeometry bodyGeometry,
            List<CollisionProxy> output
    ) {
        switch (plan.automatic()) {
            case BODY -> appendBody(
                    plan.body(),
                    bodyGeometry.bodyBounds(),
                    context,
                    output
            );
            case CAPE -> appendCape(
                    plan,
                    context,
                    bodyGeometry,
                    output
            );
            case NONE -> {
            }
        }
    }

    private static void appendCape(
            CollisionProxyPlan plan,
            CollisionBakeContext context,
            BodyCollisionGeometry geometry,
            List<CollisionProxy> output
    ) {
        appendBody(plan.body(), geometry.bodyBounds(), context, output);
        if (plan.body() == null || geometry.backPlane().isEmpty()) {
            return;
        }
        BodyCollisionGeometry.BackPlane plane =
                geometry.backPlane().orElseThrow();
        CapsuleFit fit = CapsuleFit.fromBounds(geometry.bodyBounds());
        output.add(context.plane(
                plan.body(),
                new Vector3f(
                        (float) plane.pointX(),
                        (float) plane.pointY(),
                        (float) plane.pointZ()
                ),
                new Vector3f(
                        (float) plane.normalX(),
                        (float) plane.normalY(),
                        (float) plane.normalZ()
                ),
                context.hitRadius(fit.radius())
        ));
    }

    private static void appendBody(
            CollisionReference reference,
            PhysicsBoneGeometry.Bounds bounds,
            CollisionBakeContext context,
            List<CollisionProxy> output
    ) {
        if (reference == null) {
            return;
        }
        CapsuleFit fit = CapsuleFit.fromBounds(bounds);
        if (fit.radius() <= EPSILON) {
            return;
        }
        output.add(context.capsule(
                reference,
                fit.start(),
                fit.end(),
                fit.radius(),
                context.hitRadius(fit.radius())
        ));
    }
}
