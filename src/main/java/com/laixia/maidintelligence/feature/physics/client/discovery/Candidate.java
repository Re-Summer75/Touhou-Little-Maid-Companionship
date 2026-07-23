package com.laixia.maidintelligence.feature.physics.client.discovery;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

record Candidate(
        PhysicsBoneSelectionPlan.PartType type,
        double confidence,
        String reason
) {
    static Candidate of(
            PhysicsBoneSelectionPlan.PartType type,
            double confidence,
            String reason
    ) {
        return new Candidate(type, DiscoveryMath.clamp(confidence), reason);
    }

    static Candidate reject(String reason) {
        return new Candidate(
                PhysicsBoneSelectionPlan.PartType.GENERIC,
                0.0D,
                reason
        );
    }
}
