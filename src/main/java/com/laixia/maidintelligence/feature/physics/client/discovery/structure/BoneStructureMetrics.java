package com.laixia.maidintelligence.feature.physics.client.discovery.structure;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

/**
 * Model-load-time evidence for rigid mounts and flexible hair structures.
 */
public record BoneStructureMetrics(
        boolean compact,
        boolean elongated,
        boolean longLeaf,
        boolean mirroredSibling,
        boolean coincidentFamily,
        boolean distalDescendant,
        int cubeCount,
        PhysicsBoneSelectionPlan.StructureRole suggestedRole
) {
    public static final BoneStructureMetrics NONE =
            new BoneStructureMetrics(
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    0,
                    PhysicsBoneSelectionPlan.StructureRole.NONE
            );

    public boolean structurallyRigidAttachment() {
        return compact
                && !elongated
                && (coincidentFamily || distalDescendant);
    }

    public boolean anonymousPonytail() {
        return longLeaf && mirroredSibling;
    }
}
