package com.laixia.maidintelligence.feature.physics.discovery.candidate;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.discovery.structure.BoneStructureAnalysis;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;

import java.util.Set;

/**
 * Narrow stage boundary that keeps candidate models package-private.
 */
public final class AutomaticCandidatePlanner {
    private AutomaticCandidatePlanner() {
    }

    public static void apply(
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry,
            BoneModelSnapshot model,
            BoneStructureAnalysis structures,
            Set<BoneModelSnapshot.Bone> excluded
    ) {
        AutomaticPlanSelector.apply(
                plan,
                geometry,
                model,
                structures,
                excluded
        );
    }
}
