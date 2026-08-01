package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProjector;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Covers selective substeps when a thin collider crosses a segment in one
 * frame while both endpoint poses are clear.
 */
public final class RelativeMotionSweepVerification {
    private RelativeMotionSweepVerification() {
    }

    public static void run() {
        verifiesOneFrameCrossingProducesContact();
        verifiesSeparatingContactIsNotReplayed();
    }

    private static void verifiesOneFrameCrossingProducesContact() {
        PreparedCollisionProxySet set = set();
        PreparedCollisionProxy proxy = set.proxy(0);
        Vector3f runtimePivot = new Vector3f(0.0F, -1.0F, 0.0F);
        Vector3f direction = new Vector3f(0.0F, 1.0F, 0.0F);
        CollisionScratch scratch = new CollisionScratch();

        pose(proxy, runtimePivot, -0.6F, 1);
        require(!set.project(direction, scratch, 24),
                "Clear sweep start unexpectedly projected");

        pose(proxy, runtimePivot, 0.6F, 2);
        direction.set(0.0F, 1.0F, 0.0F);
        require(set.project(direction, scratch, 24),
                "One-frame box crossing tunneled through the segment");
        require(Math.abs(direction.x) >= 0.15F,
                "Swept contact produced no lateral response: " + direction);
    }

    private static void verifiesSeparatingContactIsNotReplayed() {
        PreparedCollisionProxySet set = set();
        PreparedCollisionProxy proxy = set.proxy(0);
        Vector3f runtimePivot = new Vector3f(0.0F, -1.0F, 0.0F);
        Vector3f direction = new Vector3f(0.0F, 1.0F, 0.0F);
        CollisionScratch scratch = new CollisionScratch();

        pose(proxy, runtimePivot, 0.0F, 1);
        set.project(direction, scratch, 24);
        pose(proxy, runtimePivot, 0.6F, 2);
        direction.set(0.0F, 1.0F, 0.0F);
        require(!set.project(direction, scratch, 24),
                "A collider moving away replayed its previous contact");
    }

    private static PreparedCollisionProxySet set() {
        PreparedCollisionProxySet set = new PreparedCollisionProxySet(1);
        set.proxy(0).setBox(
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
        return set;
    }

    private static void pose(
            PreparedCollisionProxy proxy,
            Vector3f runtimePivot,
            float referenceX,
            int frame
    ) {
        Matrix4f transform = new Matrix4f().translate(referenceX, 0.0F, 0.0F);
        proxy.shape().prepareNow(
                transform,
                transform.normal(new Matrix3f()),
                1.0F
        );
        proxy.bindFrame(runtimePivot, 1.0F, 1.0F, 1.0F, frame);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
