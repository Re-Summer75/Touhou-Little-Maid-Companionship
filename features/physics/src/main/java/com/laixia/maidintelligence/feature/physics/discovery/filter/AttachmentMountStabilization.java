package com.laixia.maidintelligence.feature.physics.discovery.filter;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.layout.BoneKinematics;

/**
 * Public stage boundary for the package-private mount stabilizer.
 */
public final class AttachmentMountStabilization {
    private AttachmentMountStabilization() {
    }

    public static PhysicsBoneSelectionPlan.Decision apply(
            PhysicsBoneSelectionPlan.Decision decision,
            BoneKinematics.Metrics kinematics
    ) {
        return AttachmentMountStabilizer.apply(decision, kinematics);
    }
}
