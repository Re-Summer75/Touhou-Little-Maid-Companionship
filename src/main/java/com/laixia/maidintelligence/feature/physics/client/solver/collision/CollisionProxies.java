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

    public static CollisionProxy box(
            int referenceNodeIndex,
            Vector3f referenceOriginModel,
            Vector3f pivotFromReference,
            Vector3f centerFromReference,
            Vector3f axisXFromReference,
            Vector3f axisYFromReference,
            Vector3f axisZFromReference,
            Vector3f halfExtents,
            float hitRadius,
            float leverArm
    ) {
        return box(
                referenceNodeIndex,
                referenceOriginModel,
                pivotFromReference,
                centerFromReference,
                axisXFromReference,
                axisYFromReference,
                axisZFromReference,
                halfExtents,
                hitRadius,
                CollisionProjector.CLOSED_BOX,
                leverArm
        );
    }

    /**
     * @param openAxis local axis whose positive face is the only closed one,
     *                 or {@link CollisionProjector#CLOSED_BOX}
     */
    public static CollisionProxy box(
            int referenceNodeIndex,
            Vector3f referenceOriginModel,
            Vector3f pivotFromReference,
            Vector3f centerFromReference,
            Vector3f axisXFromReference,
            Vector3f axisYFromReference,
            Vector3f axisZFromReference,
            Vector3f halfExtents,
            float hitRadius,
            int openAxis,
            float leverArm
    ) {
        return new BoxCollisionProxy(
                referenceNodeIndex,
                referenceOriginModel,
                pivotFromReference,
                centerFromReference,
                axisXFromReference,
                axisYFromReference,
                axisZFromReference,
                halfExtents,
                hitRadius,
                openAxis,
                leverArm
        );
    }

    public static CollisionProxy box(
            int referenceNodeIndex,
            Vector3f pivotFromReference,
            Vector3f centerFromReference,
            Vector3f halfExtents,
            float hitRadius,
            float leverArm
    ) {
        return box(
                referenceNodeIndex,
                new Vector3f(),
                pivotFromReference,
                centerFromReference,
                new Vector3f(1.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, 1.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, 1.0F),
                halfExtents,
                hitRadius,
                leverArm
        );
    }

    public static CollisionProxy withSource(
            CollisionProxy proxy,
            CollisionProxySource source
    ) {
        return source == proxy.source()
                ? proxy
                : new SourcedCollisionProxy(proxy, source);
    }
}
