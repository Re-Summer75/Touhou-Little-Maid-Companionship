package com.laixia.maidintelligence.feature.physics.client.discovery;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.PhysicsMetadata;
import com.laixia.maidintelligence.feature.physics.client.discovery.structure.BoneStructureAnalysis;
import com.laixia.maidintelligence.feature.physics.client.discovery.structure.BoneStructureAnalyzer;
import com.laixia.maidintelligence.feature.physics.client.discovery.structure.ChainDynamicsPlanner;

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
            AnimatedGeoModel model,
            PhysicsMetadata metadata
    ) {
        PhysicsBoneGeometry.Analysis geometry =
                PhysicsBoneGeometry.analyze(model);
        BoneStructureAnalysis structures =
                BoneStructureAnalyzer.analyze(geometry);
        PhysicsBoneSelectionPlan.Builder plan =
                PhysicsBoneSelectionPlan.builder(modelId);
        initialize(plan, geometry);

        Set<AnimatedGeoBone> excluded =
                MetadataPlanApplier.apply(modelId, plan, geometry, metadata);
        if (metadata.mode() == PhysicsMetadata.Mode.EXPLICIT) {
            MetadataRejections.rejectUnlistedExplicit(
                    plan,
                    geometry,
                    excluded,
                    metadata.origin()
            );
            MetadataRejections.rejectExcluded(
                    plan,
                    geometry,
                    excluded,
                    metadata.origin()
            );
            ChainDynamicsPlanner.apply(plan, geometry);
            return plan.build();
        }

        AutomaticPlanSelector.apply(
                plan,
                geometry,
                model,
                structures,
                excluded
        );
        MetadataRejections.rejectExcluded(
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
