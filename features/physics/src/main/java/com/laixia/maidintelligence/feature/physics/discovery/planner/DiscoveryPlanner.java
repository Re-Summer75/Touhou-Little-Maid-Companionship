package com.laixia.maidintelligence.feature.physics.discovery.planner;

import com.laixia.maidintelligence.feature.physics.discovery.candidate.AutomaticCandidatePlanner;
import com.laixia.maidintelligence.feature.physics.discovery.metadata.MetadataPlanProcessor;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;
import com.laixia.maidintelligence.feature.physics.discovery.structure.BoneStructureAnalysis;
import com.laixia.maidintelligence.feature.physics.discovery.structure.BoneStructureAnalyzer;
import com.laixia.maidintelligence.feature.physics.discovery.structure.ChainDynamicsPlanner;

import java.util.Set;

/**
 * Coordinates the discovery stages while keeping metadata binding, scoring,
 * filtering, and plan mutation in focused modules.
 */
public final class DiscoveryPlanner {
    private DiscoveryPlanner() {
    }

    public static PhysicsBoneSelectionPlan discover(
            String modelId,
            BoneModelSnapshot model,
            PhysicsMetadata metadata
    ) {
        PhysicsBoneGeometry.Analysis geometry =
                PhysicsBoneGeometry.analyze(model);
        BoneStructureAnalysis structures =
                BoneStructureAnalyzer.analyze(geometry);
        PhysicsBoneSelectionPlan.Builder plan =
                PhysicsBoneSelectionPlan.builder(modelId);
        initialize(plan, geometry);

        Set<BoneModelSnapshot.Bone> excluded =
                MetadataPlanProcessor.apply(modelId, plan, geometry, metadata);
        if (metadata.mode() == PhysicsMetadata.Mode.EXPLICIT) {
            MetadataPlanProcessor.rejectUnlistedExplicit(
                    plan,
                    geometry,
                    excluded,
                    metadata.origin()
            );
            MetadataPlanProcessor.rejectExcluded(
                    plan,
                    geometry,
                    excluded,
                    metadata.origin()
            );
            ChainDynamicsPlanner.apply(plan, geometry);
            return plan.build();
        }

        AutomaticCandidatePlanner.apply(
                plan,
                geometry,
                model,
                structures,
                excluded
        );
        MetadataPlanProcessor.rejectExcluded(
                plan,
                geometry,
                excluded,
                metadata.origin()
        );
        ChainDynamicsPlanner.apply(plan, geometry);
        return plan.build();
    }

    private static void initialize(
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
            plan.path(node.bone(), node.path());
            plan.decide(
                    node.bone(),
                    PhysicsBoneSelectionPlan.Decision.rejected(
                            PhysicsBoneSelectionPlan.PartType.GENERIC,
                            PhysicsBoneSelectionPlan.Source.NONE,
                            0.0D,
                            node.hasGeometry() ? "not evaluated" : "no geometry"
                    )
            );
        }
    }
}
