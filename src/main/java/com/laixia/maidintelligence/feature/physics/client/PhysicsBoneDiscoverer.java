package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.discovery.DiscoveryPlanner;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;

import java.util.HashSet;
import java.util.Set;

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
        Set<String> distributedChains = distributedAutoChains(
                model,
                discovered
        );
        PhysicsBoneSelectionPlan.Builder enriched =
                PhysicsBoneSelectionPlan.builder(modelId);
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            copyWithKinematics(
                    bone,
                    null,
                    null,
                    discovered,
                    distributedChains,
                    enriched
            );
        }
        return enriched.build();
    }

    private static void copyWithKinematics(
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            AnimatedGeoBone nearestSolidAncestor,
            PhysicsBoneSelectionPlan discovered,
            Set<String> distributedChains,
            PhysicsBoneSelectionPlan.Builder output
    ) {
        PhysicsBoneSelectionPlan.Decision decision =
                discovered.decision(bone);
        if (decision.source() == PhysicsBoneSelectionPlan.Source.AUTO
                && decision.driven()
                && distributedChains.contains(decision.chainId())) {
            decision = PhysicsBoneSelectionPlan.Decision.rejected(
                    decision.type(),
                    decision.source(),
                    decision.confidence(),
                    PhysicsBoneSelectionPlan.StructureRole
                            .RIGID_ATTACHMENT_BASE,
                    "distributed cube clusters lack one safe physical pivot"
            );
        }
        BoneKinematics.Metrics kinematics = null;
        if (decision.driven()) {
            kinematics = BoneKinematics.measure(
                    bone,
                    parent,
                    nearestSolidAncestor,
                    decision.type(),
                    decision.structureRole(),
                    decision.chainSegment(),
                    nextChainBone(bone, decision, discovered)
            );
            if (decision.source() == PhysicsBoneSelectionPlan.Source.AUTO
                    && kinematics.supportStabilityUnsupported()) {
                decision = PhysicsBoneSelectionPlan.Decision.rejected(
                        decision.type(),
                        decision.source(),
                        decision.confidence(),
                        PhysicsBoneSelectionPlan.StructureRole
                                .RIGID_ATTACHMENT_BASE,
                        "single-bone attachment lacks stable upper support"
                );
                kinematics = null;
            } else {
                decision = AttachmentMountStabilizer.apply(
                        decision,
                        kinematics
                );
            }
        }
        output.path(bone, discovered.path(bone));
        output.decide(bone, decision);
        if (kinematics != null) {
            output.kinematics(bone, kinematics);
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
                    distributedChains,
                    output
            );
        }
    }

    private static Set<String> distributedAutoChains(
            AnimatedGeoModel model,
            PhysicsBoneSelectionPlan plan
    ) {
        Set<String> output = new HashSet<>();
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            collectDistributedAutoChains(bone, plan, output);
        }
        return output;
    }

    private static void collectDistributedAutoChains(
            AnimatedGeoBone bone,
            PhysicsBoneSelectionPlan plan,
            Set<String> output
    ) {
        PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
        if (decision.driven()
                && decision.source() == PhysicsBoneSelectionPlan.Source.AUTO
                && BoneKinematics.hasDistributedGeometry(bone)) {
            output.add(decision.chainId());
        }
        for (AnimatedGeoBone child : bone.children()) {
            collectDistributedAutoChains(child, plan, output);
        }
    }

    private static AnimatedGeoBone nextChainBone(
            AnimatedGeoBone bone,
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan plan
    ) {
        if (!decision.chainSegment().present()
                || decision.chainSegment().index()
                >= decision.chainSegment().count() - 1) {
            return null;
        }
        for (AnimatedGeoBone child : bone.children()) {
            PhysicsBoneSelectionPlan.Decision childDecision =
                    plan.decision(child);
            if (childDecision.driven()
                    && childDecision.chainId().equals(decision.chainId())
                    && childDecision.chainSegment().index()
                    == decision.chainSegment().index() + 1) {
                return child;
            }
            if (child.geoBone().cubes().getCubeCount() == 0) {
                AnimatedGeoBone nested =
                        nextChainBone(child, decision, plan);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }
}
