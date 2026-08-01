package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProjector;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Regression coverage for conservative broad-phase gaps carried between
 * adjacent animation frames.
 */
public final class PreparedCollisionCullReserveVerification {
    private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);

    private PreparedCollisionCullReserveVerification() {
    }

    public static void run() {
        verifiesColliderMotionConsumesReserve();
        verifiesPivotMotionConsumesReserve();
        verifiesFrameGapInvalidatesReserve();
        verifiesPreviousContactForcesExactSlack();
    }

    private static void verifiesColliderMotionConsumesReserve() {
        PreparedCollisionProxy proxy = box();
        SwingCone cone = cone();
        Vector3f pivot = new Vector3f(0.0F, -1.0F, 0.0F);

        pose(proxy, 2.0F);
        float clear = proxy.coherentSlack(
                pivot, 1.0F, 1.0F, cone, 1,
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, false
        );
        require(clear > 0.0F && proxy.cullReserve > 0.0F,
                "Initial clear pose did not establish a cull reserve");

        pose(proxy, 0.0F);
        float contact = proxy.coherentSlack(
                pivot, 1.0F, 1.0F, cone, 2, 0.0F, 0.0F, false
        );
        require(contact <= 0.0F,
                "Collider crossed a cached broad-phase gap: " + contact);
    }

    private static void verifiesPivotMotionConsumesReserve() {
        PreparedCollisionProxy proxy = box();
        SwingCone cone = cone();
        Vector3f farPivot = new Vector3f(2.0F, -1.0F, 0.0F);

        pose(proxy, 0.0F);
        require(proxy.coherentSlack(
                farPivot, 1.0F, 1.0F, cone, 1,
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, false
        ) > 0.0F, "Far pivot was not culled");
        pose(proxy, 0.0F);
        float contact = proxy.coherentSlack(
                new Vector3f(0.0F, -1.0F, 0.0F),
                1.0F, 1.0F, cone, 2, 2.0F, 0.0F, false
        );
        require(contact <= 0.0F,
                "Pivot crossed a cached broad-phase gap: " + contact);
    }

    private static void verifiesPreviousContactForcesExactSlack() {
        PreparedCollisionProxy proxy = box();
        SwingCone cone = cone();
        Vector3f pivot = new Vector3f(0.0F, -1.0F, 0.0F);

        pose(proxy, 0.0F);
        proxy.coherentSlack(
                pivot, 1.0F, 1.0F, cone, 1,
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, false
        );
        pose(proxy, 0.0F);
        proxy.cullReserve = 100.0F;
        proxy.cullReserveFrame = 1;
        float contact = proxy.coherentSlack(
                pivot, 1.0F, 1.0F, cone, 2, 0.0F, 0.0F, true
        );
        require(contact <= 0.0F,
                "Active contact trusted a stale cull reserve: " + contact);
    }

    private static void verifiesFrameGapInvalidatesReserve() {
        PreparedCollisionProxy proxy = box();
        Vector3f pivot = new Vector3f(0.0F, -1.0F, 0.0F);

        pose(proxy, 0.0F);
        proxy.cullReserve = 100.0F;
        proxy.cullReserveFrame = 1;
        float contact = proxy.coherentSlack(
                pivot, 1.0F, 1.0F, cone(), 3, 0.0F, 0.0F, false
        );
        require(contact <= 0.0F,
                "Non-adjacent frame trusted a stale cull reserve: " + contact);
    }

    private static PreparedCollisionProxy box() {
        PreparedCollisionProxy proxy = new PreparedCollisionProxy();
        proxy.setBox(
                0,
                new Vector3f(),
                new Vector3f(),
                new Vector3f(1.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, 1.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, 1.0F),
                new Vector3f(0.2F, 0.2F, 0.2F),
                0.0F,
                CollisionProjector.CLOSED_BOX,
                1.0F
        );
        return proxy;
    }

    private static SwingCone cone() {
        SwingCone cone = new SwingCone();
        cone.set(UP, Float.POSITIVE_INFINITY);
        return cone;
    }

    private static void pose(PreparedCollisionProxy proxy, float x) {
        Matrix4f transform = new Matrix4f().translate(x, 0.0F, 0.0F);
        proxy.shape().prepareNow(
                transform,
                transform.normal(new Matrix3f()),
                1.0F
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
