package com.laixia.maidintelligence.feature.physics.client.discovery;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

record Candidate(
        PhysicsBoneSelectionPlan.PartType type,
        double confidence,
        PhysicsBoneSelectionPlan.StructureRole structureRole,
        String reason
) {
    Candidate(
            PhysicsBoneSelectionPlan.PartType type,
            double confidence,
            String reason
    ) {
        this(
                type,
                confidence,
                PhysicsBoneSelectionPlan.StructureRole.NONE,
                reason
        );
    }

    static Candidate of(
            PhysicsBoneSelectionPlan.PartType type,
            double confidence,
            String reason
    ) {
        return of(
                type,
                confidence,
                PhysicsBoneSelectionPlan.StructureRole.NONE,
                reason
        );
    }

    static Candidate of(
            PhysicsBoneSelectionPlan.PartType type,
            double confidence,
            PhysicsBoneSelectionPlan.StructureRole structureRole,
            String reason
    ) {
        return new Candidate(
                type,
                DiscoveryMath.clamp(confidence),
                structureRole,
                reason
        );
    }

    static Candidate reject(String reason) {
        return reject(
                PhysicsBoneSelectionPlan.StructureRole.NONE,
                reason
        );
    }

    static Candidate reject(
            PhysicsBoneSelectionPlan.StructureRole structureRole,
            String reason
    ) {
        return new Candidate(
                PhysicsBoneSelectionPlan.PartType.GENERIC,
                0.0D,
                structureRole,
                reason
        );
    }
}
