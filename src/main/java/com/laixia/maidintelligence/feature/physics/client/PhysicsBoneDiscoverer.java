package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.discovery.DiscoveryPlanner;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;

/**
 * Stable package-local entry point for building a model's physics plan.
 *
 * <p>The discovery implementation lives in the {@code discovery} subpackage;
 * this facade keeps existing cache and verification callers independent from
 * its internal module layout.
 */
final class PhysicsBoneDiscoverer {
    private PhysicsBoneDiscoverer() {
    }

    static PhysicsBoneSelectionPlan discover(
            String modelId,
            AnimatedGeoModel model,
            PhysicsMetadata metadata
    ) {
        PhysicsBoneSelectionPlan discovered =
                DiscoveryPlanner.discover(modelId, model, metadata);
        PhysicsBoneSelectionPlan.Builder enriched =
                PhysicsBoneSelectionPlan.builder(modelId);
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            copyWithKinematics(bone, null, null, discovered, enriched);
        }
        return enriched.build();
    }

    private static void copyWithKinematics(
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            AnimatedGeoBone nearestSolidAncestor,
            PhysicsBoneSelectionPlan discovered,
            PhysicsBoneSelectionPlan.Builder output
    ) {
        PhysicsBoneSelectionPlan.Decision decision =
                discovered.decision(bone);
        output.path(bone, discovered.path(bone));
        output.decide(bone, decision);
        if (decision.driven()) {
            output.kinematics(
                    bone,
                    BoneKinematics.measure(
                            bone,
                            parent,
                            nearestSolidAncestor,
                            decision.type(),
                            decision.structureRole()
                    )
            );
        }
        AnimatedGeoBone nextSolidAncestor =
                bone.geoBone().cubes().getCubeCount() > 0
                        ? bone
                        : nearestSolidAncestor;
        for (AnimatedGeoBone child : bone.children()) {
            copyWithKinematics(
                    child,
                    bone,
                    nextSolidAncestor,
                    discovered,
                    output
            );
        }
    }
}
