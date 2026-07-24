package com.laixia.maidintelligence.feature.physics.client.discovery.structure;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

/**
 * Bakes bounded root-to-tip dynamics into automatic multi-bone chains.
 */
final class ChainDynamicsCurve {
    private ChainDynamicsCurve() {
    }

    static PhysicsBoneSelectionPlan.Decision apply(
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan.ChainSegment segment
    ) {
        if (decision.source() != PhysicsBoneSelectionPlan.Source.AUTO) {
            return decision.withDynamics(
                    null,
                    null,
                    normalizedRole(decision),
                    segment
            );
        }
        if (decision.structureRole()
                == PhysicsBoneSelectionPlan.StructureRole
                .COMPOUND_SINGLE_BONE) {
            return compound(decision, segment);
        }
        if (segment.count() <= 1 || !supportsGradient(decision.type())) {
            return decision.withDynamics(
                    null,
                    null,
                    normalizedRole(decision),
                    segment
            );
        }
        float t = segment.normalizedPosition();
        PhysicsBoneSelectionPlan.SpringProfile curve =
                new PhysicsBoneSelectionPlan.SpringProfile(
                        lerp(1.22F, 0.72F, t),
                        lerp(0.65F, 1.00F, t),
                        lerp(1.18F, 0.78F, t),
                        lerp(0.72F, 1.22F, t),
                        lerp(0.76F, 1.18F, t),
                        lerp(0.72F, 1.12F, t),
                        lerp(0.74F, 1.10F, t)
                );
        PhysicsBoneSelectionPlan.ConstraintProfile constraints =
                withRotationInertia(
                        decision.constraints(),
                        lerp(
                                decision.constraints()
                                        .rotationInertiaScale() * 0.65F,
                                decision.constraints()
                                        .rotationInertiaScale()
                                        + (1.0F - decision.constraints()
                                        .rotationInertiaScale()) * 0.45F,
                                t
                        )
                );
        return decision.withDynamics(
                decision.profile().multiply(curve),
                constraints,
                normalizedRole(decision),
                segment
        );
    }

    private static PhysicsBoneSelectionPlan.Decision compound(
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan.ChainSegment segment
    ) {
        PhysicsBoneSelectionPlan.SpringProfile conservative =
                new PhysicsBoneSelectionPlan.SpringProfile(
                        1.35F,
                        0.65F,
                        1.25F,
                        0.65F,
                        0.65F,
                        0.42F,
                        0.48F
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

    private static boolean supportsGradient(
            PhysicsBoneSelectionPlan.PartType type
    ) {
        return type == PhysicsBoneSelectionPlan.PartType.HAIR
                || type == PhysicsBoneSelectionPlan.PartType.RIBBON
                || type == PhysicsBoneSelectionPlan.PartType.CAPE
                || type == PhysicsBoneSelectionPlan.PartType.TAIL;
    }

    private static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }
}
