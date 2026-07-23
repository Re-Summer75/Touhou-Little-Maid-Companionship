package com.laixia.maidintelligence.feature.physics.client;

/**
 * Aggregates the independent offline verification modules for bone physics.
 */
public final class BonePhysicsVerification {
    private BonePhysicsVerification() {
    }

    public static void main(String[] args) throws Exception {
        BoneClassifierVerification.run();
        MotionDynamicsVerification.run();
        MotionAllocationVerification.run();
        MetadataGeometryVerification.run();
        HairDiscoveryVerification.run();
        AnonymousGeometryVerification.run();
        WinefoxGeometryVerification.run();
        BoneKinematicsVerification.run();
        BundledGeckoModelVerification.run();
        PhysicsPlanCacheVerification.run();
        SolverEquivalenceVerification.run();
        System.out.println("Bone physics verification passed.");
    }
}
