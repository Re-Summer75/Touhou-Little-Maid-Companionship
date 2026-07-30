package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.solver.spring.SpringProjectionDamperVerification;

/**
 * Aggregates the independent offline verification modules for bone physics.
 */
public final class BonePhysicsVerification {
    private BonePhysicsVerification() {
    }

    public static void main(String[] args) throws Exception {
        BoneClassifierVerification.run();
        MotionDynamicsVerification.run();
        EnvironmentalWindVerification.run();
        AnimationTimelineClockVerification.run();
        AnimationInertiaVerification.run();
        TailAnimationContinuityVerification.run();
        MotionAllocationVerification.run();
        MetadataGeometryVerification.run();
        HairDiscoveryVerification.run();
        AnonymousGeometryVerification.run();
        WinefoxGeometryVerification.run();
        BoneKinematicsVerification.run();
        BoneAttachmentFrameVerification.run();
        PivotRolePolicyVerification.run();
        DistributedBoneRigidityVerification.run();
        ContactAwarePivotVerification.run();
        SupportStabilityPivotVerification.run();
        SmallSupportStabilityVerification.run();
        BundledAttachmentPivotVerification.run();
        RigidAttachmentVerification.run();
        ClothAccessoryDiscoveryVerification.run();
        InitialPoseStabilityVerification.run();
        SpringProjectionDamperVerification.run();
        VariableFrameStabilityVerification.run();
        DanglingAccessoryStabilityVerification.run();
        BundledGeckoModelVerification.run();
        BundledAxisPolarityVerification.run();
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
