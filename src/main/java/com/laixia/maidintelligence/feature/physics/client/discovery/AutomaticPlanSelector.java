package com.laixia.maidintelligence.feature.physics.client.discovery;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

import java.util.Set;

final class AutomaticPlanSelector {
    static final double AUTO_THRESHOLD = 0.70D;

    private AutomaticPlanSelector() {
    }

    static void apply(
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry,
            AnimatedGeoModel model,
            Set<AnimatedGeoBone> excluded
    ) {
        for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
            if (excluded.contains(node.bone())) {
                continue;
            }
            PhysicsBoneSelectionPlan.Decision current =
                    plan.current(node.bone());
            if (current != null && current.driven()) {
                continue;
            }
            Candidate candidate = CandidateScorer.score(node, geometry, model);
            PhysicsBoneSelectionPlan.Decision parentDecision =
                    node.parent() == null
                            ? null
                            : plan.current(node.parent().bone());
            boolean continuation = isSerialContinuation(
                    node,
                    candidate,
                    parentDecision
            );
            if (continuation) {
                candidate = new Candidate(
                        parentDecision.type(),
                        Math.max(
                                candidate.confidence(),
                                Math.min(
                                        0.90D,
                                        parentDecision.confidence() - 0.03D
                                )
                        ),
                        "serial continuation of " + parentDecision.chainId()
                );
            }
            decide(plan, node, candidate, parentDecision, continuation);
        }
    }

    private static boolean isSerialContinuation(
            PhysicsBoneGeometry.Node node,
            Candidate candidate,
            PhysicsBoneSelectionPlan.Decision parent
    ) {
        return parent != null
                && parent.driven()
                && parent.source() == PhysicsBoneSelectionPlan.Source.AUTO
                && parent.type() != PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                && node.parent().bone().children().size() == 1
                && candidate.confidence() >= 0.35D;
    }

    private static void decide(
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Node node,
            Candidate candidate,
            PhysicsBoneSelectionPlan.Decision parent,
            boolean continuation
    ) {
        if (candidate.confidence() >= AUTO_THRESHOLD) {
            plan.decide(
                    node.bone(),
                    PhysicsBoneSelectionPlan.Decision.driven(
                            candidate.type(),
                            PhysicsBoneSelectionPlan.Source.AUTO,
                            continuation
                                    ? parent.chainId()
                                    : DiscoveryMath.autoChainId(
                                    node,
                                    candidate.type()
                            ),
                            candidate.confidence(),
                            PhysicsBoneSelectionPlan.SpringProfile.defaults(
                                    candidate.type()
                            ),
                            candidate.reason()
                    )
            );
        } else {
            plan.decide(
                    node.bone(),
                    PhysicsBoneSelectionPlan.Decision.rejected(
                            candidate.type(),
                            PhysicsBoneSelectionPlan.Source.AUTO,
                            candidate.confidence(),
                            candidate.reason()
                    )
            );
        }
    }
}
