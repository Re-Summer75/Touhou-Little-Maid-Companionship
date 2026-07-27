package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxies;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.PreparedCollisionProxy;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireNear;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireVectorNear;

final class CollisionProxyFrameworkVerification {
    private static final float EPSILON = 1.0E-4F;
    private static final Quaternionf IDENTITY = new Quaternionf();
    private static final Quaternionf[] REFERENCES = {
            new Quaternionf(),
            new Quaternionf()
    };

    private CollisionProxyFrameworkVerification() {
    }

    static void run() {
        CollisionMultiProxyVerification.run();
        verifiesPlaneProjection();
        verifiesSphereProjection();
        verifiesCapsuleProjection();
        verifiesCapsuleAxisCrossing();
        verifiesBoxProjection();
        verifiesBoxCornerProjection();
        verifiesMultipleProxyAssociation();
        verifiesIndependentReferenceOrientation();
        verifiesDegenerateCapsule();
        verifiesPreparedRigidEquivalence();
        verifiesPreparedAffineScaleAndNormal();
    }

    private static void verifiesPlaneProjection() {
        CollisionProxy proxy = CollisionProxies.plane(
                0,
                new Vector3f(0.0F, 0.0F, 2.0F),
                new Vector3f(0.0F, 0.0F, 1.5F),
                new Vector3f(0.0F, 0.0F, 1.0F),
                0.0F,
                1.0F
        );
        verifyProjection(proxy, new Vector3f(0.0F, 0.0F, -1.0F));
    }

    private static void verifiesSphereProjection() {
        CollisionProxy proxy = CollisionProxies.sphere(
                1,
                new Vector3f(0.0F, 0.0F, 2.0F),
                new Vector3f(),
                1.5F,
                0.0F,
                1.0F
        );
        verifyProjection(proxy, new Vector3f(0.0F, 0.0F, -1.0F));
    }

    private static void verifiesCapsuleProjection() {
        CollisionProxy proxy = capsule(0);
        verifyProjection(proxy, new Vector3f(-1.0F, 0.0F, 0.0F));
    }

    private static void verifiesCapsuleAxisCrossing() {
        CollisionProxy proxy = CollisionProxies.capsule(
                0,
                new Vector3f(0.9F, 0.0F, 0.0F),
                new Vector3f(0.0F, -2.0F, 0.0F),
                new Vector3f(0.0F, 2.0F, 0.0F),
                1.0F,
                0.0F,
                1.0F
        );
        verifyProjection(proxy, new Vector3f(-1.0F, 0.0F, 0.0F));
    }

    private static void verifiesBoxProjection() {
        verifyProjection(box(0), new Vector3f(0.0F, 0.0F, -1.0F));
    }

    /**
     * A tip aimed at a corner has to leave along the diagonal, which is the
     * case the plane linearisation has to iterate to solve.
     */
    private static void verifiesBoxCornerProjection() {
        CollisionProxy proxy = CollisionProxies.box(
                0,
                new Vector3f(1.4F, 1.4F, 0.0F),
                new Vector3f(),
                new Vector3f(1.0F, 1.0F, 1.0F),
                0.0F,
                1.0F
        );
        verifyProjection(
                proxy,
                new Vector3f(-1.0F, -1.0F, 0.0F).normalize()
        );
    }

    private static void verifiesMultipleProxyAssociation() {
        CollisionProxySet proxies = new CollisionProxySet(
                CollisionProxies.plane(
                        0,
                        new Vector3f(0.0F, 0.0F, 2.0F),
                        new Vector3f(0.0F, 0.0F, 1.5F),
                        new Vector3f(0.0F, 0.0F, 1.0F),
                        0.0F,
                        1.0F
                ),
                CollisionProxies.sphere(
                        1,
                        new Vector3f(0.0F, 0.0F, 2.0F),
                        new Vector3f(),
                        1.5F,
                        0.0F,
                        1.0F
                ),
                capsule(-1),
                box(1)
        );
        require(proxies.proxyCount() == 4,
                "One bone did not retain all collision proxies");
        for (CollisionProxyKind kind : CollisionProxyKind.values()) {
            require(proxies.hasKind(kind),
                    "Missing collision proxy kind " + kind);
        }
        require(
                proxies.proxy(0).referenceNodeIndex() == 0
                        && proxies.proxy(1).referenceNodeIndex() == 1
                        && proxies.proxy(2).referenceNodeIndex() == -1
                        && proxies.proxy(3).referenceNodeIndex() == 1,
                "Per-proxy reference node indices were not preserved"
        );
    }

    private static void verifiesDegenerateCapsule() {
        Vector3f point = new Vector3f();
        CollisionProxy proxy = CollisionProxies.capsule(
                0,
                new Vector3f(0.0F, 0.0F, 2.0F),
                point,
                point,
                1.5F,
                0.0F,
                1.0F
        );
        verifyProjection(proxy, new Vector3f(0.0F, 0.0F, -1.0F));
    }

    private static void verifiesIndependentReferenceOrientation() {
        CollisionProxySet proxies = new CollisionProxySet(
                CollisionProxies.sphere(
                        1,
                        new Vector3f(0.0F, 0.0F, 2.0F),
                        new Vector3f(),
                        1.5F,
                        0.0F,
                        1.0F
                )
        );
        Quaternionf[] references = {
                new Quaternionf(),
                new Quaternionf().rotateY((float) Math.toRadians(90.0D))
        };
        Vector3f direction = new Vector3f(-1.0F, 0.0F, 0.0F);
        require(
                proxies.project(
                        direction,
                        references,
                        IDENTITY,
                        new CollisionScratch()
                ),
                "Proxy did not use its own reference-node orientation"
        );
    }

    private static void verifiesPreparedRigidEquivalence() {
        Vector3f origin = new Vector3f(3.0F, -2.0F, 1.0F);
        Vector3f pivot = new Vector3f(0.0F, 0.0F, 2.0F);
        verifyPreparedEquivalent(
                CollisionProxies.plane(
                        0, origin, pivot,
                        new Vector3f(0.0F, 0.0F, 1.5F),
                        new Vector3f(0.0F, 0.0F, 1.0F),
                        0.0F, 1.0F
                ),
                origin, pivot, new Vector3f(0.0F, 0.0F, -1.0F)
        );
        verifyPreparedEquivalent(
                CollisionProxies.sphere(
                        0, origin, pivot, new Vector3f(),
                        1.5F, 0.0F, 1.0F
                ),
                origin, pivot, new Vector3f(0.0F, 0.0F, -1.0F)
        );
        verifyPreparedEquivalent(
                CollisionProxies.capsule(
                        0, origin, new Vector3f(2.0F, 0.0F, 0.0F),
                        new Vector3f(0.0F, -1.0F, 0.0F),
                        new Vector3f(0.0F, 1.0F, 0.0F),
                        1.25F, 0.0F, 1.0F
                ),
                origin,
                new Vector3f(2.0F, 0.0F, 0.0F),
                new Vector3f(-1.0F, 0.0F, 0.0F)
        );
        verifyPreparedEquivalent(
                CollisionProxies.box(
                        0, origin, new Vector3f(0.0F, 0.0F, 1.6F),
                        new Vector3f(),
                        new Vector3f(1.0F, 0.0F, 0.0F),
                        new Vector3f(0.0F, 1.0F, 0.0F),
                        new Vector3f(0.0F, 0.0F, 1.0F),
                        new Vector3f(1.0F, 1.0F, 1.0F),
                        0.0F, 1.0F
                ),
                origin,
                new Vector3f(0.0F, 0.0F, 1.6F),
                new Vector3f(0.0F, 0.0F, -1.0F)
        );
    }

    private static void verifyPreparedEquivalent(
            CollisionProxy proxy,
            Vector3f origin,
            Vector3f pivotRelative,
            Vector3f penetratingDirection
    ) {
        Quaternionf rotation = new Quaternionf().rotateXYZ(
                0.31F, -0.47F, 0.22F
        );
        Matrix4f delta = new Matrix4f().rotate(rotation);
        PreparedCollisionProxy prepared = new PreparedCollisionProxy();
        proxy.copyStaticShape(
                new Quaternionf(),
                prepared,
                new CollisionScratch()
        );
        Vector3f pivotModel = delta.transformPosition(
                new Vector3f(origin).add(pivotRelative)
        );
        prepared.prepare(pivotModel, delta, delta.normal(new Matrix3f()), 1.0F);
        Vector3f legacyDirection = rotation.transform(
                penetratingDirection,
                new Vector3f()
        );
        Vector3f preparedDirection = new Vector3f(legacyDirection);
        boolean legacyChanged = proxy.project(
                legacyDirection, rotation, new CollisionScratch()
        );
        boolean preparedChanged = prepared.project(
                preparedDirection, new CollisionScratch()
        );
        require(legacyChanged == preparedChanged,
                "Prepared proxy changed rigid projection state");
        requireVectorNear(preparedDirection, legacyDirection, 1.0E-5F,
                "Prepared rigid projection diverged from legacy");
    }

    private static void verifiesPreparedAffineScaleAndNormal() {
        Matrix4f delta = new Matrix4f().scale(2.0F, 0.5F, 1.0F);
        Matrix3f normal = delta.normal(new Matrix3f());
        PreparedCollisionProxy sphere = new PreparedCollisionProxy();
        sphere.setSphere(
                0, new Vector3f(), new Vector3f(),
                1.0F, 0.0F, 1.0F
        );
        sphere.prepare(
                new Vector3f(3.0F, 0.0F, 0.0F),
                delta, normal, 2.0F
        );
        requireNear(
                sphere.clearance(
                        new Vector3f(-1.0F, 0.0F, 0.0F),
                        new CollisionScratch()
                ),
                -1.0F, EPSILON,
                "Prepared Sphere did not scale collider and segment together"
        );
        PreparedCollisionProxy plane = new PreparedCollisionProxy();
        Vector3f restNormal = new Vector3f(1.0F, 1.0F, 0.0F).normalize();
        plane.setPlane(
                0, new Vector3f(), new Vector3f(),
                restNormal, 0.0F, 1.0F
        );
        plane.prepare(new Vector3f(), delta, normal, 2.0F);
        float expected = normal.transform(
                restNormal,
                new Vector3f()
        ).normalize().x * 2.0F;
        requireNear(
                plane.clearance(
                        new Vector3f(1.0F, 0.0F, 0.0F),
                        new CollisionScratch()
                ),
                expected, EPSILON,
                "Prepared Plane normal skipped inverse-transpose"
        );
    }

    private static CollisionProxy box(int referenceNodeIndex) {
        return CollisionProxies.box(
                referenceNodeIndex,
                new Vector3f(0.0F, 0.0F, 1.6F),
                new Vector3f(),
                new Vector3f(1.0F, 1.0F, 1.0F),
                0.0F,
                1.0F
        );
    }

    private static CollisionProxy capsule(int referenceNodeIndex) {
        return CollisionProxies.capsule(
                referenceNodeIndex,
                new Vector3f(2.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, -1.0F, 0.0F),
                new Vector3f(0.0F, 1.0F, 0.0F),
                1.25F,
                0.0F,
                1.0F
        );
    }

    private static void verifyProjection(
            CollisionProxy proxy,
            Vector3f direction
    ) {
        CollisionProxySet proxies = new CollisionProxySet(proxy);
        CollisionScratch scratch = new CollisionScratch();
        require(
                proxies.project(
                        direction,
                        REFERENCES,
                        IDENTITY,
                        scratch
                ),
                proxy.kind() + " proxy did not project penetration"
        );
        requireNear(
                direction.length(),
                1.0F,
                EPSILON,
                proxy.kind() + " projection changed bone length"
        );
        require(
                proxies.clearance(
                        0,
                        direction,
                        IDENTITY,
                        scratch
                ) >= -EPSILON,
                proxy.kind() + " proxy remained penetrated"
        );
    }
}
