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
        BoneAttachmentFrameVerification.run();
        PivotRolePolicyVerification.run();
        DistributedBoneRigidityVerification.run();
        BundledPivotSurfaceVerification.run();
        RigidAttachmentVerification.run();
        ClothAccessoryDiscoveryVerification.run();
        InitialPoseStabilityVerification.run();
        DanglingAccessoryStabilityVerification.run();
        BundledGeckoModelVerification.run();
        AccessoryChainIndependenceVerification.run();
        BundledChainJointVerification.run();
        BundledAttachmentFrameVerification.run();
        BundledSupportGeometryVerification.run();
        PhysicsPlanCacheVerification.run();
        SolverEquivalenceVerification.run();
        SecondaryMotionConstraintVerification.run();
        BodyCollisionGeometryVerification.run();
        CollisionLayoutVerification.run();
        RuntimeEndpointHierarchyVerification.run();
        AffineCollisionScaleVerification.run();
        CollisionProxyDebugDataVerification.run();
        SecondaryMotionAllocationVerification.run();
        System.out.println("Bone physics verification passed.");
    }
}
