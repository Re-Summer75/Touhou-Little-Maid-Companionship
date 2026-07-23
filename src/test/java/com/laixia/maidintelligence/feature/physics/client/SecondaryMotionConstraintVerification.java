package com.laixia.maidintelligence.feature.physics.client;

final class SecondaryMotionConstraintVerification {
    private SecondaryMotionConstraintVerification() {
    }

    static void run() {
        ReferenceSpaceConstraintVerification.run();
        ConstraintProjectionEdgeVerification.run();
        CollisionConstraintVerification.run();
    }
}
