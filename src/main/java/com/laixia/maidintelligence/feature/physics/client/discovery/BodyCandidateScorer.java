package com.laixia.maidintelligence.feature.physics.client.discovery;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.discovery.structure.ClothAccessoryMetrics;

final class BodyCandidateScorer {
    private BodyCandidateScorer() {
    }

    static Candidate score(
            ScoringContext context,
            ClothAccessoryMetrics structure
    ) {
        Candidate semantic = BodySemanticCandidate.score(
                context,
                structure
        );
        if (semantic != null) {
            return semantic;
        }
        if (context.yRatio() >= 0.40D
                && context.lateralBody() > 0.42D
                && context.size().x
                > Math.max(context.size().y, context.size().z) * 1.15D) {
            return Candidate.of(
                    PhysicsBoneSelectionPlan.PartType.WING,
                    0.38D
                            + 0.16D * context.thinScore()
                            + 0.16D * context.chainScore()
                            + 0.13D * DiscoveryMath.clamp(
                            context.lateralBody() * 1.5D
                    ),
                    "wide lateral upper-body chain"
            );
        }
        if (isCape(context)) {
            return Candidate.of(
                    PhysicsBoneSelectionPlan.PartType.CAPE,
                    0.39D
                            + 0.18D * context.thinScore()
                            + 0.16D * context.chainScore()
                            + 0.10D * DiscoveryMath.clamp(
                            context.behindBody() * 2.0D
                    ),
                    "broad rear upper-body cloth"
            );
        }
        if (structure.lowerBodyPanel()) {
            return Candidate.of(
                    PhysicsBoneSelectionPlan.PartType.SKIRT,
                    structure.singleBoneCloth() ? 0.84D : 0.78D,
                    structure.singleBoneCloth()
                            ? PhysicsBoneSelectionPlan.StructureRole
                            .COMPOUND_SINGLE_BONE
                            : PhysicsBoneSelectionPlan.StructureRole
                            .FLEXIBLE_CHAIN_SEGMENT,
                    structure.singleBoneCloth()
                            ? "single-bone lower cloth shell"
                            : "lower-body cloth panel structure"
            );
        }
        if (structure.narrowBodyPendant()) {
            return Candidate.of(
                    PhysicsBoneSelectionPlan.PartType.RIBBON,
                    0.79D,
                    PhysicsBoneSelectionPlan.StructureRole
                            .DANGLING_ACCESSORY,
                    "narrow upper-attached body pendant"
            );
        }
        if (context.yRatio() < 0.58D
                && context.size().x > context.modelWidth() * 0.20D
                && context.thinScore() > 0.35D) {
            return Candidate.of(
                    PhysicsBoneSelectionPlan.PartType.SKIRT,
                    0.41D
                            + 0.18D * context.thinScore()
                            + 0.15D * context.chainScore()
                            + 0.10D * DiscoveryMath.clamp(
                            (0.62D - context.yRatio()) / 0.35D
                    ),
                    "thin lower-body cloth chain"
            );
        }
        if (isNarrowRibbon(context)) {
            return Candidate.of(
                    PhysicsBoneSelectionPlan.PartType.RIBBON,
                    0.40D
                            + 0.20D * context.thinScore()
                            + 0.20D * context.chainScore()
                            + 0.08D * DiscoveryMath.clamp(
                            context.behindBody() * 2.0D
                    ),
                    "narrow planar rear chain"
            );
        }
        if (context.behindBody() > 0.12D && context.yRatio() < 0.58D) {
            PhysicsBoneSelectionPlan.PartType type =
                    context.size().x > context.modelWidth() * 0.18D
                            ? PhysicsBoneSelectionPlan.PartType.CAPE
                            : PhysicsBoneSelectionPlan.PartType.TAIL;
            return Candidate.of(
                    type,
                    0.40D
                            + 0.18D * context.thinScore()
                            + 0.24D * context.chainScore()
                            + 0.12D * context.lengthScore()
                            + 0.08D * DiscoveryMath.clamp(
                            context.behindBody() * 2.0D
                    ),
                    "rear lower-body flexible chain"
            );
        }
        if (context.thinScore() > 0.55D
                && context.chainScore() > 0.30D
                && (Math.abs(context.behindBody()) > 0.10D
                || context.yRatio() < 0.35D)) {
            return Candidate.of(
                    PhysicsBoneSelectionPlan.PartType.RIBBON,
                    0.31D
                            + 0.22D * context.thinScore()
                            + 0.20D * context.chainScore()
                            + 0.10D * context.lengthScore(),
                    "generic thin serial appendage"
            );
        }
        return new Candidate(
                PhysicsBoneSelectionPlan.PartType.GENERIC,
                Math.min(
                        0.45D,
                        0.18D
                                + 0.12D * context.thinScore()
                                + 0.10D * context.chainScore()
                ),
                "geometry is not a high-confidence soft appendage"
        );
    }

    private static boolean isCape(ScoringContext context) {
        return context.behindBody() > 0.18D
                && context.yRatio() > 0.32D
                && context.center().y > context.bodyCenter().y
                - context.bodyBounds().sizeY() * 0.05D
                && context.size().x
                > Math.max(context.size().y, context.size().z) * 0.75D;
    }

    private static boolean isNarrowRibbon(ScoringContext context) {
        return context.behindBody() > 0.10D
                && context.size().x < context.bodyWidth() * 0.35D
                && context.thinScore() > 0.75D
                && context.chainScore() > 0.30D;
    }
}
