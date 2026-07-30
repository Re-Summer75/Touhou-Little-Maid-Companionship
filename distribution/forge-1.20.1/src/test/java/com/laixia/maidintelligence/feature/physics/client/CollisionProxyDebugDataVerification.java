package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.PreparedCollisionProxy;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireNear;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireVectorNear;

final class CollisionProxyDebugDataVerification {
    private static final float EPSILON = 1.0E-5F;

    private CollisionProxyDebugDataVerification() {
    }

    static void run() {
        verifiesPreparedShapeFieldsAndPenetration();
        verifiesSolverResetInvalidatesPreparedCopies();
    }

    private static void verifiesPreparedShapeFieldsAndPenetration() {
        CollisionScratch scratch = new CollisionScratch();
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        PreparedCollisionProxy plane = new PreparedCollisionProxy();
        plane.setPlane(
                7,
                new Vector3f(1.0F, 2.0F, 3.0F),
                new Vector3f(1.0F, 2.0F, 3.0F),
                new Vector3f(0.0F, 1.0F, 0.0F),
                0.25F,
                2.0F
        );
        plane.prepare(
                new Vector3f(5.0F, 2.0F, 3.0F),
                new Matrix4f().translate(4.0F, 0.0F, 0.0F),
                new Matrix3f(),
                2.0F
        );
        plane.copyDebugData(new Vector3f(0.0F, -1.0F, 0.0F), data, scratch);
        require(data.kind == CollisionProxyKind.PLANE
                        && data.referenceNodeIndex == 7,
                "Plane debug identity fields were not copied");
        requireVectorNear(data.referenceOrigin, new Vector3f(5.0F, 2.0F, 3.0F),
                EPSILON, "Plane dynamic reference origin was not copied");
        requireVectorNear(data.runtimePivot, new Vector3f(5.0F, 2.0F, 3.0F),
                EPSILON, "Plane runtime pivot was not copied");
        requireVectorNear(data.planePoint, new Vector3f(5.0F, 2.0F, 3.0F),
                EPSILON, "Plane point was not copied");
        requireVectorNear(data.planeNormal, new Vector3f(0.0F, 1.0F, 0.0F),
                EPSILON, "Plane normal was not copied");
        requireNear(data.scaledHitRadius, 0.5F, EPSILON,
                "Plane scaled hit radius was not copied");
        require(data.clearance < 0.0F && data.penetrating,
                "Negative Plane clearance was not marked penetrating");

        PreparedCollisionProxy sphere = new PreparedCollisionProxy();
        sphere.setSphere(
                3, new Vector3f(), new Vector3f(1.0F, 2.0F, 3.0F),
                0.75F, 0.25F, 1.5F
        );
        sphere.prepare(
                new Vector3f(), new Matrix4f(), new Matrix3f(), 2.0F
        );
        sphere.copyDebugData(new Vector3f(1.0F, 0.0F, 0.0F), data, scratch);
        require(data.kind == CollisionProxyKind.SPHERE,
                "Sphere debug kind was not copied");
        requireVectorNear(data.sphereCenter, new Vector3f(1.0F, 2.0F, 3.0F),
                EPSILON, "Sphere center was not copied");
        requireNear(data.sphereRadius, 1.5F, EPSILON,
                "Sphere scaled collider radius was not copied");
        requireNear(data.scaledHitRadius, 0.5F, EPSILON,
                "Sphere scaled hit radius was not copied");

        PreparedCollisionProxy capsule = new PreparedCollisionProxy();
        capsule.setCapsule(
                -1, new Vector3f(), new Vector3f(-1.0F, 0.0F, 0.0F),
                new Vector3f(1.0F, 0.0F, 0.0F),
                0.4F, 0.1F, 1.0F
        );
        capsule.prepare(
                new Vector3f(), new Matrix4f(), new Matrix3f(), 1.5F
        );
        capsule.copyDebugData(new Vector3f(0.0F, 1.0F, 0.0F), data, scratch);
        require(data.kind == CollisionProxyKind.CAPSULE,
                "Capsule debug kind was not copied");
        requireVectorNear(data.capsuleStart, new Vector3f(-1.0F, 0.0F, 0.0F),
                EPSILON, "Capsule start was not copied");
        requireVectorNear(data.capsuleEnd, new Vector3f(1.0F, 0.0F, 0.0F),
                EPSILON, "Capsule end was not copied");
        requireNear(data.capsuleRadius, 0.6F, EPSILON,
                "Capsule scaled collider radius was not copied");
        requireNear(data.leverArm, 1.5F, EPSILON,
                "Capsule scaled lever arm was not copied");
    }

    private static void verifiesSolverResetInvalidatesPreparedCopies() {
        BoneModelSnapshot model = RuntimeEndpointHierarchyFixture.model();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:collision_debug_data",
                        model,
                        RuntimeEndpointHierarchyFixture.collisionMetadata()
                )
        );
        int nodeIndex = proxyNodeIndex(layout);
        require(nodeIndex >= 0, "Collision debug fixture lost its proxy");
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        require(solver.preparedProxyCount(nodeIndex) == 0
                        && !solver.copyPreparedCollisionProxy(
                        nodeIndex, 0, data
                ), "Prepared proxy existed before solve");
        solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, true);
        require(solver.preparedProxyCount(nodeIndex) == 1
                        && solver.copyPreparedCollisionProxy(
                        nodeIndex, 0, data
                ) && Float.isFinite(data.clearance)
                        && data.source == CollisionProxySource.EXPLICIT,
                "Latest prepared proxy could not be copied");
        solver.reset();
        require(solver.preparedProxyCount(nodeIndex) == 0
                        && !solver.copyPreparedCollisionProxy(
                        nodeIndex, 0, data
                ) && data.kind == null,
                "Solver reset retained prepared debug data");
    }

    private static int proxyNodeIndex(PhysicsSolverLayout layout) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).driven()
                    && layout.node(index).constraint()
                    .collisionProxies().proxyCount() > 0) {
                return index;
            }
        }
        return -1;
    }
}
