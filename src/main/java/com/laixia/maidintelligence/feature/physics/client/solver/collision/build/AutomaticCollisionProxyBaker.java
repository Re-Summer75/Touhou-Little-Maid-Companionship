package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.HeadCollisionProxyBuilder;
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
            PhysicsBoneGeometry.Analysis geometry,
            BodyCollisionGeometry bodyGeometry,
            List<CollisionProxy> output
    ) {
        switch (plan.automatic()) {
            case HEAD -> appendHead(plan, context, geometry, output);
            case BODY -> appendBody(
                    plan.body(),
                    bodyGeometry.bodyBounds(),
                    context,
                    output
            );
            case SKIRT -> appendSkirt(
                    plan,
                    context,
                    bodyGeometry,
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

    private static void appendHead(
            CollisionProxyPlan plan,
            CollisionBakeContext context,
            PhysicsBoneGeometry.Analysis geometry,
            List<CollisionProxy> output
    ) {
        CollisionReference reference = plan.head();
        if (reference == null) {
            return;
        }
        CollisionProxySet proxies = HeadCollisionProxyBuilder.build(
                context.node(),
                context.referenceIndex(reference),
                reference.bone(),
                context.restPose(reference),
                geometry,
                context.boneRestPose(),
                context.copyPivot(new Vector3f())
        );
        for (int index = 0; index < proxies.proxyCount(); index++) {
            output.add(proxies.proxy(index));
        }
    }

    private static void appendSkirt(
            CollisionProxyPlan plan,
            CollisionBakeContext context,
            BodyCollisionGeometry geometry,
            List<CollisionProxy> output
    ) {
        appendBody(plan.body(), geometry.bodyBounds(), context, output);
        if (geometry.legs().isEmpty()) {
            return;
        }
        BodyCollisionGeometry.LegPair pair = geometry.legs().orElseThrow();
        appendBody(
                plan.leftLeg(),
                pair.left().bounds(),
                context,
                output
        );
        appendBody(
                plan.rightLeg(),
                pair.right().bounds(),
                context,
                output
        );
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
