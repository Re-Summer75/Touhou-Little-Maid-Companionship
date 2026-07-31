package com.laixia.maidintelligence.feature.physics.discovery.candidate;


import com.laixia.maidintelligence.feature.physics.discovery.classifier.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.discovery.structure.BoneStructureAnalysis;
import com.laixia.maidintelligence.feature.physics.discovery.structure.BoneStructureMetrics;

import java.util.Set;

final class AutomaticPlanSelector {
    static final double AUTO_THRESHOLD = 0.70D;

    private AutomaticPlanSelector() {
    }

    static void apply(
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry,
            BoneModelSnapshot model,
            BoneStructureAnalysis structures,
            Set<BoneModelSnapshot.Bone> excluded
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
            Candidate candidate = CandidateScorer.score(
                    node,
                    geometry,
                    model,
                    structures
            );
            PhysicsBoneSelectionPlan.Decision parentDecision =
                    node.parent() == null
                            ? null
                            : plan.current(node.parent().bone());
            PhysicsBoneGeometry.Node rigidAncestor =
                    RigidAttachmentContinuation.ancestor(node, plan);
            PhysicsBoneSelectionPlan.Decision rigidDecision =
                    rigidAncestor == null
                            ? null
                            : plan.current(rigidAncestor.bone());
            BoneStructureMetrics structure =
                    structures.metrics(node.bone());
            if (rigidDecision != null
                    && rigidDecision.structureRole()
                    == PhysicsBoneSelectionPlan.StructureRole
                    .RIGID_ATTACHMENT_BASE
                    && structure.longLeaf()
                    && candidate.type()
                    == PhysicsBoneSelectionPlan.PartType.HAIR
                    && candidate.confidence() < AUTO_THRESHOLD) {
                candidate = Candidate.of(
                        PhysicsBoneSelectionPlan.PartType.HAIR,
                        0.82D,
                        PhysicsBoneSelectionPlan.StructureRole
                                .COMPOUND_SINGLE_BONE,
                        "long flexible child of rigid attachment"
                );
            }
            if (RigidAttachmentContinuation.shouldRemainRigid(
                    node,
                    rigidAncestor,
                    candidate,
                    structure
            )) {
                candidate = Candidate.reject(
                        PhysicsBoneSelectionPlan.StructureRole
                                .RIGID_ATTACHMENT_BASE,
                        "coincident continuation of rigid attachment"
                );
            }
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
                        candidate.structureRole()
                                == PhysicsBoneSelectionPlan.StructureRole.NONE
                                ? PhysicsBoneSelectionPlan.StructureRole
                                .FLEXIBLE_CHAIN_SEGMENT
                                : candidate.structureRole(),
                        "serial continuation of " + parentDecision.chainId()
                );
            }
            decide(plan, node, candidate, parentDecision, continuation);
        }
    }

    /**
     * The sole child of a flexible segment continues it unless something
     * positively rules the bone out.
     *
     * <p>Requiring the child to independently score as soft would break every
     * chain whose lower segments stop looking remarkable on their own: the
     * spatial scorers all reward geometry behind the body, so a skirt panel at
     * the front runs out of evidence one or two segments down and the tail of
     * the chain is left rigid while its mirror image at the back survives. The
     * chain's identity is settled by its root; a segment only has to not be a
     * rigid part to inherit it.
     */
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
                && (candidate.type() == parent.type()
                || !PhysicsBoneClassifier.classifyVisibleGeometry(
                        node.bone().getName()
                ).isPhysical())
                && !candidate.rejected();
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
                            PhysicsBoneSelectionPlan.ConstraintProfile.defaults(
                                    candidate.type()
                            ),
                            candidate.structureRole()
                                    == PhysicsBoneSelectionPlan.StructureRole.NONE
                                    ? PhysicsBoneSelectionPlan.StructureRole
                                    .FLEXIBLE_CHAIN_SEGMENT
                                    : candidate.structureRole(),
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
                            candidate.structureRole(),
                            candidate.reason()
                    )
            );
        }
    }
}
