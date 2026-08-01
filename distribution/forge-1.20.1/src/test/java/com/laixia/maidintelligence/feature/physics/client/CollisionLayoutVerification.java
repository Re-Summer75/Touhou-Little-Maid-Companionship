package com.laixia.maidintelligence.feature.physics.client;

final class CollisionLayoutVerification {
    private CollisionLayoutVerification() {
    }

    static void run() throws Exception {
        AutomaticCollisionLayoutVerification.run();
        AutomaticReferenceSafetyVerification.run();
        HeadMeshCollisionVerification.run();
        MeshCollisionVerification.run();
        ClothLayerCollisionVerification.run();
        WinefoxApronCollisionVerification.run();
        CollisionSchemaCompatibilityVerification.run();
        ExplicitCollisionLayoutVerification.run();
        SqueezedContactVerification.run();
        SweptContactVerification.run();
        SeatedOverlapVerification.run();
        WindCollisionVerification.run();
        CullBucketSubdivisionVerification.run();
    }
}
