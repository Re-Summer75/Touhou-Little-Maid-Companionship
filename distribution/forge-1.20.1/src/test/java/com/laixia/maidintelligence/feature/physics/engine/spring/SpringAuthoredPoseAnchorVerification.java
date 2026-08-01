package com.laixia.maidintelligence.feature.physics.engine.spring;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProjection;
import org.joml.Vector3f;

/**
 * Focused checks for visible-speed and wind-penetration pose anchoring.
 */
final class SpringAuthoredPoseAnchorVerification {
    private SpringAuthoredPoseAnchorVerification() {
    }

    static void run() {
        capsLargeUndrivenRecovery();
        leavesOutwardMotionUntouched();
        findsNearestClearPointTowardAuthor();
    }

    private static void capsLargeUndrivenRecovery() {
        Vector3f current = new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f requested = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f rest = new Vector3f(requested);
        float leverArm = 10.0F;
        float dt = 1.0F / 60.0F;
        if (!SpringAuthoredPoseAnchor.limitRecovery(
                current, requested, rest, leverArm, dt
        )) {
            throw new AssertionError("Large recovery was not rate limited");
        }
        float travel = current.distance(requested) * leverArm * 16.0F;
        float maximum = SpringAuthoredPoseAnchor.maximumRecoveryDistance(dt);
        if (travel > maximum + 1.0E-3F) {
            throw new AssertionError(
                    "Visible recovery exceeded its rate limit: " + travel
            );
        }
    }

    private static void leavesOutwardMotionUntouched() {
        Vector3f current = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f requested = new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f before = new Vector3f(requested);
        if (SpringAuthoredPoseAnchor.limitRecovery(
                current, requested, current, 10.0F, 1.0F / 60.0F
        ) || !requested.equals(before, 0.0F)) {
            throw new AssertionError("Outward secondary motion was rate limited");
        }
    }

    private static void findsNearestClearPointTowardAuthor() {
        Vector3f requested = new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f rest = new Vector3f(0.0F, 1.0F, 0.0F);
        ThresholdProjection projection = new ThresholdProjection(0.70F);
        if (!SpringAuthoredPoseAnchor.recoverPenetration(
                requested,
                rest,
                projection,
                new CollisionScratch()
        ) || requested.y < 0.699F || requested.y > 0.705F) {
            throw new AssertionError(
                    "Authored boundary search missed the nearest clear point: "
                            + requested
            );
        }
    }

    private static final class ThresholdProjection
            implements CollisionProjection {
        private final float minimumY;

        private ThresholdProjection(float minimumY) {
            this.minimumY = minimumY;
        }

        @Override
        public void beginProjectionSeries() {
        }

        @Override
        public boolean project(
                Vector3f direction,
                CollisionScratch scratch,
                int maximumPasses
        ) {
            return false;
        }

        @Override
        public boolean isClear(
                Vector3f direction,
                CollisionScratch scratch
        ) {
            return direction.y >= minimumY;
        }

        @Override
        public boolean isClear(
                Vector3f direction,
                float penetrationTolerance,
                CollisionScratch scratch
        ) {
            return isClear(direction, scratch);
        }

        @Override
        public boolean resolveRecurringContact(
                boolean recurringContact,
                Vector3f projectedDirection,
                CollisionScratch scratch
        ) {
            return false;
        }
    }
}
