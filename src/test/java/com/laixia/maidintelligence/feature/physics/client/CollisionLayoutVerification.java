package com.laixia.maidintelligence.feature.physics.client;

final class CollisionLayoutVerification {
    private CollisionLayoutVerification() {
    }

    static void run() {
        AutomaticCollisionLayoutVerification.run();
        AutomaticReferenceSafetyVerification.run();
        HeadCapsuleLayoutVerification.run();
        CollisionSchemaCompatibilityVerification.run();
        ExplicitCollisionLayoutVerification.run();
    }
}
