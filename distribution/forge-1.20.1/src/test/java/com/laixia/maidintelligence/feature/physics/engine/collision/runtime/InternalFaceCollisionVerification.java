package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProjector;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Vector3f;

import java.util.List;

/**
 * Regression coverage for rigid compound-box internal face removal.
 */
public final class InternalFaceCollisionVerification {
    private static final Vector3f AXIS_X = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final Vector3f AXIS_Y = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vector3f AXIS_Z = new Vector3f(0.0F, 0.0F, 1.0F);

    private InternalFaceCollisionVerification() {
    }

    public static void run() {
        verifiesExactSeamIsHiddenOnBothBoxes();
        verifiesPartialAndOverlappingFacesRemainVisible();
        verifiesProjectionEscapesThroughVisibleOuterFace();
        verifiesFullyHiddenBoxDoesNotProject();
    }

    private static void verifiesExactSeamIsHiddenOnBothBoxes() {
        PreparedCollisionShape left = box(
                -0.5F, 0.0F, 0.0F, 0.5F, 1.0F, 1.0F
        );
        PreparedCollisionShape right = box(
                0.5F, 0.0F, 0.0F, 0.5F, 1.0F, 1.0F
        );
        PreparedCollisionSurfaceFilter.markHiddenBoxFaces(
                List.of(left, right)
        );
        require((left.hiddenFaces() & (1 << 0)) != 0,
                "Left box retained its covered positive-X face");
        require((right.hiddenFaces() & (1 << 1)) != 0,
                "Right box retained its covered negative-X face");
        require(Integer.bitCount(left.hiddenFaces()) == 1
                        && Integer.bitCount(right.hiddenFaces()) == 1,
                "Outer compound-box faces were removed");
    }

    private static void verifiesPartialAndOverlappingFacesRemainVisible() {
        PreparedCollisionShape candidate = box(
                -0.5F, 0.0F, 0.0F, 0.5F, 1.0F, 1.0F
        );
        PreparedCollisionShape partial = box(
                0.5F, 0.0F, 0.0F, 0.5F, 0.4F, 1.0F
        );
        PreparedCollisionSurfaceFilter.markHiddenBoxFaces(
                List.of(candidate, partial)
        );
        require(candidate.hiddenFaces() == 0,
                "A partially exposed face was removed");

        PreparedCollisionShape overlap = box(
                0.4F, 0.0F, 0.0F, 0.5F, 1.0F, 1.0F
        );
        PreparedCollisionSurfaceFilter.markHiddenBoxFaces(
                List.of(candidate, overlap)
        );
        require(candidate.hiddenFaces() == 0,
                "A deep overlap was mistaken for an exact seam");
    }

    private static void verifiesProjectionEscapesThroughVisibleOuterFace() {
        Vector3f center = new Vector3f(-0.5F, 0.0F, 0.0F);
        Vector3f half = new Vector3f(0.5F, 2.0F, 2.0F);
        Vector3f pivot = new Vector3f(center);
        float leverArm = 0.75F;
        Vector3f direction = new Vector3f(
                0.4F / leverArm,
                (float) Math.sqrt(1.0F - 0.16F / (leverArm * leverArm)),
                0.0F
        );
        require(CollisionProjector.projectBox(
                        direction, pivot, center,
                        AXIS_X, AXIS_Y, AXIS_Z, half,
                        0.0F, CollisionProjector.CLOSED_BOX, 1 << 0,
                        leverArm, new CollisionScratch()
                ),
                "Hidden seam fixture did not project");
        float projectedX = pivot.x + direction.x * leverArm;
        require(projectedX <= -0.999F,
                "Projection escaped through the hidden seam: " + projectedX);
    }

    private static void verifiesFullyHiddenBoxDoesNotProject() {
        Vector3f direction = new Vector3f(1.0F, 0.0F, 0.0F);
        CollisionScratch scratch = new CollisionScratch();
        boolean moved = CollisionProjector.projectBox(
                direction,
                new Vector3f(-1.0F, 0.0F, 0.0F),
                new Vector3f(),
                AXIS_X, AXIS_Y, AXIS_Z,
                new Vector3f(1.0F, 1.0F, 1.0F),
                0.0F, CollisionProjector.CLOSED_BOX, 0x3F,
                1.0F, scratch
        );
        require(!moved, "A box with no exposed face still projected");
    }

    private static PreparedCollisionShape box(
            float x,
            float y,
            float z,
            float halfX,
            float halfY,
            float halfZ
    ) {
        PreparedCollisionShape shape = new PreparedCollisionShape();
        shape.setBox(
                0,
                new Vector3f(),
                new Vector3f(x, y, z),
                AXIS_X,
                AXIS_Y,
                AXIS_Z,
                new Vector3f(halfX, halfY, halfZ),
                0.0F,
                CollisionProjector.CLOSED_BOX
        );
        return shape;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
