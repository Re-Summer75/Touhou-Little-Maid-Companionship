package com.laixia.maidintelligence.feature.physics.api;

final class PhysicsDecisionFactory {
    private PhysicsDecisionFactory() {
    }

    static PhysicsBoneSelectionPlan.Decision rejectedDefault() {
        PhysicsBoneSelectionPlan.PartType type =
                PhysicsBoneSelectionPlan.PartType.GENERIC;
        return new PhysicsBoneSelectionPlan.Decision(
                false,
                type,
                PhysicsBoneSelectionPlan.Source.NONE,
                "",
                0.0D,
                PhysicsBoneSelectionPlan.SpringProfile.defaults(type),
                PhysicsBoneSelectionPlan.ConstraintProfile.defaults(type),
                PhysicsBoneSelectionPlan.StructureRole.NONE,
                PhysicsBoneSelectionPlan.ChainSegment.none(),
                "not selected"
        );
    }

    static PhysicsBoneSelectionPlan.Decision driven(
            PhysicsBoneSelectionPlan.PartType type,
            PhysicsBoneSelectionPlan.Source source,
            String chainId,
            double confidence,
            PhysicsBoneSelectionPlan.SpringProfile profile,
            PhysicsBoneSelectionPlan.ConstraintProfile constraints,
            PhysicsBoneSelectionPlan.StructureRole structureRole,
            String reason
    ) {
        return new PhysicsBoneSelectionPlan.Decision(
                true,
                type,
                source,
                chainId,
                confidence,
                profile,
                constraints,
                structureRole == null
                        ? PhysicsBoneSelectionPlan.StructureRole
                        .FLEXIBLE_CHAIN_SEGMENT
                        : structureRole,
                PhysicsBoneSelectionPlan.ChainSegment.none(),
                reason
        );
    }

    static PhysicsBoneSelectionPlan.Decision rejected(
            PhysicsBoneSelectionPlan.PartType type,
            PhysicsBoneSelectionPlan.Source source,
            double confidence,
            PhysicsBoneSelectionPlan.StructureRole structureRole,
            String reason
    ) {
        return new PhysicsBoneSelectionPlan.Decision(
                false,
                type,
                source,
                "",
                confidence,
                PhysicsBoneSelectionPlan.SpringProfile.defaults(type),
                PhysicsBoneSelectionPlan.ConstraintProfile.defaults(type),
                structureRole == null
                        ? PhysicsBoneSelectionPlan.StructureRole.NONE
                        : structureRole,
                PhysicsBoneSelectionPlan.ChainSegment.none(),
                reason
        );
    }

    static PhysicsBoneSelectionPlan.Decision withDynamics(
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan.SpringProfile profile,
            PhysicsBoneSelectionPlan.ConstraintProfile constraints,
            PhysicsBoneSelectionPlan.StructureRole role,
            PhysicsBoneSelectionPlan.ChainSegment segment
    ) {
        return new PhysicsBoneSelectionPlan.Decision(
                decision.driven(),
                decision.type(),
                decision.source(),
                decision.chainId(),
                decision.confidence(),
                profile == null ? decision.profile() : profile,
                constraints == null ? decision.constraints() : constraints,
                role == null ? decision.structureRole() : role,
                segment == null ? decision.chainSegment() : segment,
                decision.reason()
        );
    }
}
