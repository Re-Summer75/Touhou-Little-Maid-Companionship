package com.laixia.maidintelligence.feature.physics.client.solver.collision;

import org.joml.Vector3f;

/**
 * Construction-only factory for immutable collision proxies.
 */
public final class CollisionProxies {
    private CollisionProxies() {
    }

    public static CollisionProxy plane(
            int referenceNodeIndex,
            Vector3f pivotFromReference,
            Vector3f pointFromReference,
            Vector3f normalFromReference,
            float hitRadius,
            float leverArm
    ) {
        return plane(
                referenceNodeIndex,
                new Vector3f(),
                pivotFromReference,
                pointFromReference,
                normalFromReference,
                hitRadius,
                leverArm
        );
    }

    public static CollisionProxy plane(
            int referenceNodeIndex,
            Vector3f referenceOriginModel,
            Vector3f pivotFromReference,
            Vector3f pointFromReference,
            Vector3f normalFromReference,
            float hitRadius,
            float leverArm
    ) {
        return new PlaneCollisionProxy(
                referenceNodeIndex,
                referenceOriginModel,
                pivotFromReference,
                pointFromReference,
                normalFromReference,
                hitRadius,
                leverArm
        );
    }

    public static CollisionProxy sphere(
            int referenceNodeIndex,
            Vector3f pivotFromReference,
            Vector3f centerFromReference,
            float radius,
            float hitRadius,
            float leverArm
    ) {
        return sphere(
                referenceNodeIndex,
                new Vector3f(),
                pivotFromReference,
                centerFromReference,
                radius,
                hitRadius,
                leverArm
        );
    }

    public static CollisionProxy sphere(
            int referenceNodeIndex,
            Vector3f referenceOriginModel,
            Vector3f pivotFromReference,
            Vector3f centerFromReference,
            float radius,
            float hitRadius,
            float leverArm
    ) {
        return new SphereCollisionProxy(
                referenceNodeIndex,
                referenceOriginModel,
                pivotFromReference,
                centerFromReference,
                radius,
                hitRadius,
                leverArm
        );
    }

    public static CollisionProxy capsule(
            int referenceNodeIndex,
            Vector3f pivotFromReference,
            Vector3f startFromReference,
            Vector3f endFromReference,
            float radius,
            float hitRadius,
            float leverArm
    ) {
        return capsule(
                referenceNodeIndex,
                new Vector3f(),
                pivotFromReference,
                startFromReference,
                endFromReference,
                radius,
                hitRadius,
                leverArm
        );
    }

    public static CollisionProxy capsule(
            int referenceNodeIndex,
            Vector3f referenceOriginModel,
            Vector3f pivotFromReference,
            Vector3f startFromReference,
            Vector3f endFromReference,
            float radius,
            float hitRadius,
            float leverArm
    ) {
        return new CapsuleCollisionProxy(
                referenceNodeIndex,
                referenceOriginModel,
                pivotFromReference,
                startFromReference,
                endFromReference,
                radius,
                hitRadius,
                leverArm
        );
    }

    public static CollisionProxy withSource(
            CollisionProxy proxy,
            CollisionProxySource source
    ) {
        return source == CollisionProxySource.AUTOMATIC
                ? proxy
                : new SourcedCollisionProxy(proxy, source);
    }
}
