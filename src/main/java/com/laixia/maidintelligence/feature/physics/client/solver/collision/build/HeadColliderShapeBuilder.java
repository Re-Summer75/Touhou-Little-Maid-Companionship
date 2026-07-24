package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxies;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxy;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Selects a sphere or short capsule for the fitted head surface.
 */
public final class HeadColliderShapeBuilder {
    private static final float CAPSULE_ASPECT = 1.35F;

    private HeadColliderShapeBuilder() {
    }

    public static boolean isRestTipLegal(
            Vector3f headSize,
            Vector3f restTipFromCenter,
            float sphereRadius,
            float hitRadius,
            float margin
    ) {
        float width = Math.max(headSize.x, headSize.z);
        if (width <= 0.0F || headSize.y <= width * CAPSULE_ASPECT) {
            return restTipFromCenter.length() + margin
                    >= sphereRadius + hitRadius;
        }
        float radius = width * 0.45F;
        float halfSegment = Math.max(0.0F, headSize.y * 0.5F - radius);
        float closestY = Math.max(
                -halfSegment,
                Math.min(halfSegment, restTipFromCenter.y)
        );
        float dx = restTipFromCenter.x;
        float dy = restTipFromCenter.y - closestY;
        float dz = restTipFromCenter.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return distance + margin >= radius + hitRadius;
    }

    public static CollisionProxy build(
            int referenceIndex,
            Vector3f originModel,
            Vector3f pivotFromReference,
            Quaternionf referenceInverse,
            Vector3f headSize,
            float sphereRadius,
            float hitRadius,
            float leverArm
    ) {
        float width = Math.max(headSize.x, headSize.z);
        if (width <= 0.0F || headSize.y <= width * CAPSULE_ASPECT) {
            return CollisionProxies.sphere(
                    referenceIndex,
                    originModel,
                    pivotFromReference,
                    new Vector3f(),
                    sphereRadius,
                    hitRadius,
                    leverArm
            );
        }
        float radius = width * 0.45F;
        float halfSegment = Math.max(0.0F, headSize.y * 0.5F - radius);
        Vector3f start = referenceInverse.transform(
                new Vector3f(0.0F, -halfSegment, 0.0F),
                new Vector3f()
        );
        Vector3f end = referenceInverse.transform(
                new Vector3f(0.0F, halfSegment, 0.0F),
                new Vector3f()
        );
        return CollisionProxies.capsule(
                referenceIndex,
                originModel,
                pivotFromReference,
                start,
                end,
                radius,
                hitRadius,
                leverArm
        );
    }
}
