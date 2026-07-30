package com.laixia.maidintelligence.feature.physics.discovery.structure;


import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;

/**
 * Bakes conservative cloth and rest-preserving ornament dynamics.
 */
final class ClothAccessoryDynamics {
    private ClothAccessoryDynamics() {
    }

    static PhysicsBoneSelectionPlan.Decision dangling(
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan.ChainSegment segment
    ) {
        PhysicsBoneSelectionPlan.SpringProfile stable =
                new PhysicsBoneSelectionPlan.SpringProfile(
                        1.55F, 0.0F, 0.18F, 1.45F,
                        0.45F, 0.55F, 0.28F, 0.40F
                );
        PhysicsBoneSelectionPlan.ConstraintProfile source =
                decision.constraints();
        PhysicsBoneSelectionPlan.ConstraintProfile constraints =
                new PhysicsBoneSelectionPlan.ConstraintProfile(
                        source.simulationSpace(),
                        Math.min(
                                0.10F,
                                source.rotationInertiaScale() * 0.25F
                        ),
                        new PhysicsBoneSelectionPlan.SwingLimits(
                                0.22F, 0.22F, 0.24F, 0.12F
                        ),
                        true,
                        true,
                        source.hitRadiusScale() * 0.85F,
                        source.collision(),
                        source.enabled()
                );
        return decision.withDynamics(
                decision.profile().multiply(stable),
                constraints,
                decision.structureRole(),
                segment
        );
    }

    static PhysicsBoneSelectionPlan.Decision skirt(
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan.ChainSegment segment
    ) {
        float t = segment.normalizedPosition();
        PhysicsBoneSelectionPlan.SpringProfile curve =
                new PhysicsBoneSelectionPlan.SpringProfile(
                        lerp(1.35F, 0.82F, t),
                        lerp(0.85F, 1.20F, t),
                        lerp(0.45F, 1.20F, t),
                        lerp(1.15F, 0.75F, t),
                        lerp(1.24F, 0.96F, t),
                        lerp(0.58F, 1.05F, t),
                        lerp(0.68F, 1.02F, t),
                        lerp(0.55F, 0.90F, t),
                        lerp(0.65F, 1.00F, t)
                );
        return decision.withDynamics(
                decision.profile().multiply(curve),
                withRotationInertia(
                        decision.constraints(),
                        lerp(
                                decision.constraints()
                                        .rotationInertiaScale() * 0.55F,
                                decision.constraints()
                                        .rotationInertiaScale() * 0.95F,
                                t
                        )
                ),
                normalizedRole(decision),
                segment
        );
    }

    static PhysicsBoneSelectionPlan.Decision compoundSkirt(
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan.ChainSegment segment
    ) {
        PhysicsBoneSelectionPlan.SpringProfile conservative =
                new PhysicsBoneSelectionPlan.SpringProfile(
                        1.28F, 0.85F, 0.30F, 1.30F, 1.22F,
                        0.62F, 0.70F, 0.55F, 0.72F
                );
        return decision.withDynamics(
                decision.profile().multiply(conservative),
                withRotationInertia(
                        decision.constraints(),
                        decision.constraints().rotationInertiaScale() * 0.60F
                ),
                decision.structureRole(),
                segment
        );
    }

    private static PhysicsBoneSelectionPlan.ConstraintProfile
    withRotationInertia(
            PhysicsBoneSelectionPlan.ConstraintProfile profile,
            float rotationInertia
    ) {
        return new PhysicsBoneSelectionPlan.ConstraintProfile(
                profile.simulationSpace(),
                rotationInertia,
                profile.swingLimits(),
                profile.backstop(),
                profile.headCollision(),
                profile.hitRadiusScale(),
                profile.collision(),
                profile.enabled()
        );
    }

    private static PhysicsBoneSelectionPlan.StructureRole normalizedRole(
            PhysicsBoneSelectionPlan.Decision decision
    ) {
        return decision.structureRole()
                == PhysicsBoneSelectionPlan.StructureRole.NONE
                ? PhysicsBoneSelectionPlan.StructureRole
                .FLEXIBLE_CHAIN_SEGMENT
                : decision.structureRole();
    }

    private static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }
}
