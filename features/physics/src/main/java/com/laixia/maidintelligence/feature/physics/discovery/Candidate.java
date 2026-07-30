package com.laixia.maidintelligence.feature.physics.discovery;


import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;

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

    /**
     * Whether the scorer positively ruled the bone out, as opposed to merely
     * failing to recognise it. Zero confidence is reserved for this: every
     * scoring path has a positive base term, so only {@link #reject} produces
     * it.
     */
    boolean rejected() {
        return confidence <= 0.0D;
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
