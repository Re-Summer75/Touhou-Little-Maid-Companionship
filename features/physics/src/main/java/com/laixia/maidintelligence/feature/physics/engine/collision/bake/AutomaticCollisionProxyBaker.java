package com.laixia.maidintelligence.feature.physics.engine.collision.bake;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.automatic.CapsuleFit;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.planner.BodyCollisionGeometry;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxies;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;
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
        appendMesh(
                plan.mesh(),
                CollisionProxySource.AUTOMATIC,
                context,
                output
        );
        appendMesh(
                plan.layers(),
                CollisionProxySource.LAYER,
                context,
                output
        );
        // Mesh boxes already are the torso, so the fitted capsule is only a
        // fallback for models that expose no usable rigid mesh. Layer boxes
        // are other cloth and never stand in for the body.
        boolean fitted = plan.mesh().isEmpty();
        switch (plan.automatic()) {
            case BODY -> {
                if (fitted) {
                    appendBody(
                            plan.body(),
                            bodyGeometry.bodyBounds(),
                            context,
                            output
                    );
                }
            }
            case CAPE -> appendCape(
                    plan,
                    context,
                    bodyGeometry,
                    fitted,
                    output
            );
            case NONE -> {
            }
        }
    }

    private static void appendMesh(
            List<CollisionProxyPlan.MeshCollider> colliders,
            CollisionProxySource source,
            CollisionBakeContext context,
            List<CollisionProxy> output
    ) {
        for (CollisionProxyPlan.MeshCollider collider : colliders) {
            Vector3f half = collider.halfExtents();
            if (Math.min(half.x, Math.min(half.y, half.z)) <= EPSILON) {
                continue;
            }
            output.add(CollisionProxies.withSource(
                    context.box(
                            collider.reference(),
                            collider.centerModel(),
                            collider.axisXModel(),
                            collider.axisYModel(),
                            collider.axisZModel(),
                            half,
                            context.hitRadius(
                                    collider.endpointRadius(),
                                    collider.endpointClamp()
                            ),
                            collider.openAxis()
                    ),
                    source
            ));
        }
    }

    private static void appendCape(
            CollisionProxyPlan plan,
            CollisionBakeContext context,
            BodyCollisionGeometry geometry,
            boolean fitted,
            List<CollisionProxy> output
    ) {
        if (fitted) {
            appendBody(plan.body(), geometry.bodyBounds(), context, output);
        }
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
