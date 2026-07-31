package com.laixia.maidintelligence.feature.physics.discovery.candidate;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.discovery.structure.ClothAccessoryMetrics;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

/**
 * Converts direct or empty-anchor body semantics into scored candidates.
 */
final class BodySemanticCandidate {
    private static final Set<String> DANGLING_TOKENS = Set.of(
            "pendant", "tassel", "charm", "guashi", "liusu"
    );
    private static final List<String> DANGLING_MARKERS = List.of(
            "挂饰", "掛飾", "吊坠", "吊墜", "流苏", "流蘇", "태슬"
    );

    private BodySemanticCandidate() {
    }

    static Candidate score(
            ScoringContext context,
            ClothAccessoryMetrics structure
    ) {
        double base = switch (context.hint()) {
            case HAIR -> 0.60D + 0.10D * context.thinScore()
                    + 0.08D * context.chainScore();
            case EAR -> 0.60D + 0.08D * context.thinScore()
                    + 0.08D * context.chainScore();
            case TAIL -> 0.60D + 0.10D * context.chainScore()
                    + 0.08D * context.lengthScore();
            case SKIRT, CAPE -> 0.56D + 0.12D * context.thinScore()
                    + 0.08D * context.chainScore();
            case WING -> 0.58D + 0.10D * context.thinScore()
                    + 0.08D * context.chainScore();
            case RIBBON -> 0.54D + 0.12D * context.thinScore()
                    + 0.10D * context.chainScore();
            default -> -1.0D;
        };
        if (base < 0.0D) {
            return null;
        }
        boolean dangling = context.hint()
                == PhysicsBoneSelectionPlan.PartType.RIBBON
                && (structure.narrowBodyPendant()
                || hasDanglingName(context.node().bone().getName()));
        PhysicsBoneSelectionPlan.StructureRole role =
                context.hint() == PhysicsBoneSelectionPlan.PartType.SKIRT
                        && structure.singleBoneCloth()
                        ? PhysicsBoneSelectionPlan.StructureRole
                        .COMPOUND_SINGLE_BONE
                        : dangling
                        ? PhysicsBoneSelectionPlan.StructureRole
                        .DANGLING_ACCESSORY
                        : PhysicsBoneSelectionPlan.StructureRole.NONE;
        return Candidate.of(
                context.hint(),
                base + context.semanticScore(),
                role,
                reason(context.hint(), dangling)
        );
    }

    private static boolean hasDanglingName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return DiscoveryMath.tokens(name).stream()
                .anyMatch(DANGLING_TOKENS::contains)
                || DANGLING_MARKERS.stream().anyMatch(lower::contains);
    }

    private static String reason(
            PhysicsBoneSelectionPlan.PartType type,
            boolean dangling
    ) {
        if (dangling) {
            return "named hanging body ornament";
        }
        return switch (type) {
            case HAIR -> "soft appendage with hair semantic hint";
            case EAR -> "soft appendage with ear semantic hint";
            case TAIL -> "rear body appendage with tail semantic hint";
            case SKIRT -> "lower-body cloth with skirt semantic hint";
            case CAPE -> "rear cloth with cape semantic hint";
            case WING -> "lateral appendage with wing semantic hint";
            case RIBBON -> "thin ornament with ribbon semantic hint";
            default -> "";
        };
    }
}
