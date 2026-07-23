package com.laixia.maidintelligence.feature.physics.client.discovery;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

final class CandidateScorer {
    private CandidateScorer() {
    }

    static Candidate score(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            AnimatedGeoModel model
    ) {
        if (!node.hasGeometry()) {
            return Candidate.reject("no geometry");
        }
        if (Boolean.TRUE.equals(node.bone().geoBone().dontRender())) {
            return Candidate.reject("model marks bone as never render");
        }
        SemanticHint semantic = semanticHint(node);
        if (RigidBoneFilter.isCoreBone(
                node,
                geometry,
                model,
                semantic.classification()
        )) {
            return Candidate.reject("rigid humanoid or locator bone");
        }
        PhysicsBoneSelectionPlan.PartType hint =
                DiscoveryMath.partType(semantic.classification().type());
        ScoringContext context = ScoringContext.create(
                node,
                geometry,
                hint,
                semantic.score(),
                semantic.strong()
        );
        if (context.inHead()) {
            return HeadCandidateScorer.score(context);
        }
        Candidate bodyCandidate = BodyCandidateScorer.score(context);
        if (!isSpatialHeadAppendage(context)) {
            return bodyCandidate;
        }
        if (bodyCandidate.confidence()
                >= AutomaticPlanSelector.AUTO_THRESHOLD) {
            return bodyCandidate;
        }
        Candidate headCandidate = HeadCandidateScorer.score(context);
        return headCandidate.confidence() > bodyCandidate.confidence()
                ? headCandidate
                : bodyCandidate;
    }

    private static boolean isSpatialHeadAppendage(
            ScoringContext context
    ) {
        if (context.hint() != PhysicsBoneSelectionPlan.PartType.GENERIC
                && context.hint()
                != PhysicsBoneSelectionPlan.PartType.HAIR
                && context.hint()
                != PhysicsBoneSelectionPlan.PartType.EAR
                && context.hint()
                != PhysicsBoneSelectionPlan.PartType.RIBBON
                && context.hint()
                != PhysicsBoneSelectionPlan.PartType.WING) {
            return false;
        }
        double pivotX = Math.abs(
                context.node().pivot().x - context.headCenter().x
        ) / context.headWidth();
        double pivotY = Math.abs(
                context.node().pivot().y - context.headCenter().y
        ) / context.headHeight();
        double pivotZ = Math.abs(
                context.node().pivot().z - context.headCenter().z
        ) / context.headWidth();
        double maximumPivotY =
                context.hint() == PhysicsBoneSelectionPlan.PartType.GENERIC
                        ? 0.90D
                        : 1.35D;
        boolean attachedNearHead = pivotX <= 1.30D
                && pivotY <= maximumPivotY
                && pivotZ <= 1.35D;
        boolean geometryNearHead = context.yRatio() >= 0.45D
                && context.lateralHead() <= 1.35D
                && context.belowHead() >= -1.0D
                && context.belowHead() <= 2.75D
                && Math.abs(context.behindHead()) <= 1.50D;
        return attachedNearHead || geometryNearHead;
    }

    private static SemanticHint semanticHint(
            PhysicsBoneGeometry.Node node
    ) {
        PhysicsBoneClassifier.Classification direct =
                PhysicsBoneClassifier.classifyVisibleGeometry(
                        node.bone().getName()
                );
        if (direct.isPhysical()) {
            return new SemanticHint(direct, 0.18D, true);
        }

        PhysicsBoneGeometry.Node ancestor = node.parent();
        while (ancestor != null && !ancestor.hasGeometry()) {
            String name = ancestor.bone().getName();
            if (PhysicsBoneClassifier.isBreaker(name)) {
                break;
            }
            PhysicsBoneClassifier.Classification inherited =
                    PhysicsBoneClassifier.classifyVisibleGeometry(name);
            if (inherited.isPhysical()) {
                return new SemanticHint(
                        inherited,
                        0.18D,
                        true
                );
            }
            ancestor = ancestor.parent();
        }
        return SemanticHint.NONE;
    }

    private record SemanticHint(
            PhysicsBoneClassifier.Classification classification,
            double score,
            boolean strong
    ) {
        private static final SemanticHint NONE = new SemanticHint(
                PhysicsBoneClassifier.classify(""),
                0.0D,
                false
        );
    }
}
