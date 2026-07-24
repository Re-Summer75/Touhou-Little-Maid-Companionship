package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxies;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.PreparedCollisionProxy;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.PreparedCollisionProxySet;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class CollisionMultiProxyVerification {
    private static final float EPSILON = 1.0E-4F;

    private CollisionMultiProxyVerification() {
    }

    static void run() {
        Vector3f normalA = new Vector3f(0.79F, 0.59F, 0.15F)
                .normalize();
        Vector3f normalB = new Vector3f(-0.55F, -0.56F, 0.62F)
                .normalize();
        CollisionProxySet proxies = new CollisionProxySet(
                CollisionProxies.plane(
                        -1,
                        new Vector3f(),
                        new Vector3f(normalA).mul(0.14F),
                        normalA,
                        0.0F,
                        1.0F
                ),
                CollisionProxies.plane(
                        -1,
                        new Vector3f(),
                        new Vector3f(normalB).mul(0.61F),
                        normalB,
                        0.0F,
                        1.0F
                )
        );
        Vector3f direction = new Vector3f(1.0F, 0.0F, 0.0F);
        Quaternionf identity = new Quaternionf();
        CollisionScratch scratch = new CollisionScratch();
        require(
                proxies.project(
                        direction,
                        new Quaternionf[0],
                        identity,
                        scratch
                ),
                "Overlapping proxy set did not project"
        );
        require(
                proxies.clearance(0, direction, identity, scratch)
                        >= -EPSILON,
                "Later proxy left the first Plane penetrated"
        );
        require(
                proxies.clearance(1, direction, identity, scratch)
                        >= -EPSILON,
                "Earlier proxy left the second Plane penetrated"
        );
        verifiesNarrowPreparedPlanePair();
        verifiesNearOppositePreparedPlanePair();
        verifiesNearAxisCapsule(identity, scratch);
    }

    private static void verifiesNarrowPreparedPlanePair() {
        Vector3f normalA = new Vector3f(4.0F, -5.0F, 1.0F)
                .normalize();
        Vector3f normalB = new Vector3f(-5.0F, 5.0F, 1.0F)
                .normalize();
        PreparedCollisionProxySet set =
                new PreparedCollisionProxySet(2);
        configurePlane(set.proxy(0), normalA, 0.14F);
        configurePlane(set.proxy(1), normalB, 0.13F);
        Vector3f direction = new Vector3f(1.0F, 0.0F, 0.0F);
        CollisionScratch scratch = new CollisionScratch();
        require(set.project(direction, scratch, 24),
                "Narrow prepared Plane pair did not project");
        require(set.clearance(0, direction, scratch) >= -EPSILON
                        && set.clearance(1, direction, scratch) >= -EPSILON,
                "Active Plane pair solver retained a feasible penetration");
    }

    private static void configurePlane(
            PreparedCollisionProxy proxy,
            Vector3f normal,
            float distance
    ) {
        proxy.setPlane(
                -1,
                new Vector3f(),
                new Vector3f(normal).mul(distance),
                normal,
                0.0F,
                1.0F
        );
        proxy.prepare(
                new Vector3f(),
                new Matrix4f(),
                new Matrix3f(),
                1.0F,
                1.0F
        );
    }

    private static void verifiesNearOppositePreparedPlanePair() {
        Vector3f normalA = new Vector3f(1.0F, 0.0F, 0.0004F)
                .normalize();
        Vector3f normalB = new Vector3f(-1.0F, 0.0F, 0.0004F)
                .normalize();
        PreparedCollisionProxySet set =
                new PreparedCollisionProxySet(2);
        configurePlane(set.proxy(0), normalA, 0.0003F);
        configurePlane(set.proxy(1), normalB, 0.0003F);
        Vector3f direction = new Vector3f(1.0F, 0.0F, -0.01F)
                .normalize();
        CollisionScratch scratch = new CollisionScratch();
        require(set.project(direction, scratch, 24),
                "Near-opposite Plane pair did not project");
        require(set.clearance(0, direction, scratch) >= -EPSILON
                        && set.clearance(1, direction, scratch) >= -EPSILON,
                "Near-opposite feasible Plane pair retained penetration");
    }

    private static void verifiesNearAxisCapsule(
            Quaternionf identity,
            CollisionScratch scratch
    ) {
        CollisionProxySet capsule = new CollisionProxySet(
                CollisionProxies.capsule(
                        -1,
                        new Vector3f(),
                        new Vector3f(0.0F, -2.0F, 0.0F),
                        new Vector3f(0.0F, 2.0F, 0.0F),
                        0.99F,
                        0.0F,
                        1.0F
                )
        );
        Vector3f direction = new Vector3f(0.0F, 1.0F, 0.0F);
        require(
                capsule.project(
                        direction,
                        new Quaternionf[0],
                        identity,
                        scratch
                ),
                "Near-axis Capsule did not project"
        );
        require(
                capsule.clearance(0, direction, identity, scratch)
                        >= -EPSILON,
                "Near-axis Capsule remained penetrated"
        );
    }
}
