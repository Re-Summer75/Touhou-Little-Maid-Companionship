package com.laixia.maidintelligence.feature.physics.discovery;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;

import java.util.Set;

final class MetadataRejections {
    private MetadataRejections() {
    }

    static void rejectExcluded(
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry,
            Set<BoneModelSnapshot.Bone> excluded,
            String origin
    ) {
        for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
            if (excluded.contains(node.bone())) {
                plan.decide(
                        node.bone(),
                        PhysicsBoneSelectionPlan.Decision.rejected(
                                PhysicsBoneSelectionPlan.PartType.GENERIC,
                                PhysicsBoneSelectionPlan.Source.METADATA,
                                1.0D,
                                "explicitly excluded by " + origin
                        )
                );
            }
        }
    }

    static void rejectUnlistedExplicit(
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry,
            Set<BoneModelSnapshot.Bone> excluded,
            String origin
    ) {
        for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
            PhysicsBoneSelectionPlan.Decision current =
                    plan.current(node.bone());
            if (node.hasGeometry()
                    && !excluded.contains(node.bone())
                    && (current == null || !current.driven())) {
                plan.decide(
                        node.bone(),
                        PhysicsBoneSelectionPlan.Decision.rejected(
                                PhysicsBoneSelectionPlan.PartType.GENERIC,
                                PhysicsBoneSelectionPlan.Source.METADATA,
                                1.0D,
                                "not listed by explicit metadata: " + origin
                        )
                );
            }
        }
    }
}
