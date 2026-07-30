package com.laixia.maidintelligence.feature.physics.discovery;


import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.discovery.structure.ClothAccessoryMetrics;

import java.util.List;
import java.util.Locale;
import java.util.Set;

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
