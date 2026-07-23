package com.laixia.maidintelligence.feature.physics.client.discovery;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import org.joml.Vector3f;

final class HeadCandidateScorer {
    private HeadCandidateScorer() {
    }

    static Candidate score(ScoringContext context) {
        if (isHeadShell(context.node(), context)) {
            double confidence = DiscoveryMath.clamp(
                    0.64D
                            + 0.12D * context.chainScore()
                            + 0.10D * context.lengthScore()
                            + context.semanticScore()
            );
            return new Candidate(
                    PhysicsBoneSelectionPlan.PartType.HEAD_SHELL,
                    confidence,
                    "visible head shell with attached flexible descendants"
            );
        }
        if (context.hint() == PhysicsBoneSelectionPlan.PartType.EAR) {
            return Candidate.of(
                    context.hint(),
                    0.62D + context.semanticScore()
                            + 0.10D * context.thinScore()
                            + 0.08D * DiscoveryMath.clamp(context.lateralHead()),
                    "head-side appendage with ear semantic hint"
            );
        }
        if (isAnonymousEar(context)) {
            return Candidate.of(
                    PhysicsBoneSelectionPlan.PartType.EAR,
                    0.60D
                            + 0.10D * context.thinScore()
                            + 0.10D * context.chainScore()
                            + 0.10D * DiscoveryMath.clamp(context.lateralHead()),
                    "mirrored upper head-side appendage"
            );
        }
        if (isAnonymousTopHairTuft(context)) {
            return Candidate.of(
                    PhysicsBoneSelectionPlan.PartType.HAIR,
                    0.67D
                            + 0.12D * context.thinScore()
                            + 0.08D * context.lengthScore()
                            + 0.06D * context.chainScore(),
                    "anonymous top hair tuft attached to visible hair shell"
            );
        }
        if (isAnonymousFringe(context)) {
            double frontScore = DiscoveryMath.clamp(
                    (-context.behindHead() - 0.15D) / 0.85D
            );
            return Candidate.of(
                    PhysicsBoneSelectionPlan.PartType.HAIR,
                    0.58D
                            + 0.12D * context.thinScore()
                            + 0.08D * context.chainScore()
                            + 0.08D * context.lengthScore()
                            + 0.08D * frontScore
                            + (hasFrontSiblingCluster(context)
                            ? 0.10D : 0.0D),
                    "anonymous forehead fringe geometry"
            );
        }
        if (context.hint() == PhysicsBoneSelectionPlan.PartType.WING
                || (context.lateralHead() > 0.95D
                && context.size().x > context.headWidth() * 0.45D)) {
            return Candidate.of(
                    PhysicsBoneSelectionPlan.PartType.WING,
                    0.40D + context.semanticScore()
                            + 0.15D * context.thinScore()
                            + 0.14D * context.chainScore()
                            + 0.10D * DiscoveryMath.clamp(
                            context.lateralHead() / 1.5D
                    ),
                    "wide lateral head appendage"
            );
        }
        return scoreHairOrRibbon(context);
    }

    static boolean isHeadShell(
            PhysicsBoneGeometry.Node node,
            ScoringContext context
    ) {
        PhysicsBoneSelectionPlan.PartType hint = node == context.node()
                ? context.hint()
                : DiscoveryMath.partType(
                PhysicsBoneClassifier.classify(node.bone().getName()).type()
        );
        return isHeadShell(
                node,
                context.geometry(),
                context.headCenter(),
                context.headWidth(),
                context.headHeight(),
                hint
        );
    }

    private static Candidate scoreHairOrRibbon(ScoringContext context) {
        PhysicsBoneSelectionPlan.PartType type =
                context.hint() == PhysicsBoneSelectionPlan.PartType.RIBBON
                        ? PhysicsBoneSelectionPlan.PartType.RIBBON
                        : PhysicsBoneSelectionPlan.PartType.HAIR;
        boolean attachedToShell = context.node().parent() != null
                && isHeadShell(context.node().parent(), context);
        boolean attachedToSemanticAnchor = isEmptySoftAnchor(
                context.node().parent(),
                type
        );
        double locationScore = DiscoveryMath.clamp(
                0.45D * DiscoveryMath.clamp(
                        (context.belowHead() + 0.35D) / 1.5D
                )
                        + 0.35D * DiscoveryMath.clamp(
                        (context.behindHead() + 0.25D) / 1.25D
                )
                        + 0.20D * DiscoveryMath.clamp(context.lateralHead())
        );
        double confidence = 0.30D
                + 0.20D * context.thinScore()
                + 0.15D * context.chainScore()
                + 0.10D * context.lengthScore()
                + 0.15D * locationScore
                + (attachedToShell ? 0.22D : 0.0D)
                + (attachedToSemanticAnchor ? 0.16D : 0.0D)
                + context.semanticScore();
        if (context.strongSemantic()
                && (context.hint() == PhysicsBoneSelectionPlan.PartType.HAIR
                || context.hint()
                == PhysicsBoneSelectionPlan.PartType.RIBBON)) {
            confidence = Math.max(
                    confidence,
                    0.60D
                            + context.semanticScore()
                            + 0.06D * context.thinScore()
                            + 0.04D * context.chainScore()
            );
        }
        return Candidate.of(
                type,
                confidence,
                attachedToShell
                        ? "flexible child attached to visible head shell"
                        : attachedToSemanticAnchor
                        ? "flexible child attached to semantic soft-part anchor"
                        : "flexible geometry in head subtree"
        );
    }

    private static boolean isEmptySoftAnchor(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneSelectionPlan.PartType expectedType
    ) {
        if (node == null || node.hasGeometry()) {
            return false;
        }
        PhysicsBoneClassifier.Classification classification =
                PhysicsBoneClassifier.classifyVisibleGeometry(
                        node.bone().getName()
                );
        return DiscoveryMath.partType(classification.type())
                == expectedType;
    }

    private static boolean isAnonymousEar(ScoringContext context) {
        return context.hint() == PhysicsBoneSelectionPlan.PartType.GENERIC
                && context.lateralHead() > 0.45D
                && context.belowHead() > -0.45D
                && context.belowHead() < 0.30D
                && Math.abs(context.behindHead()) < 0.75D
                && context.visibleLength() < context.headWidth() * 0.90D
                && hasMirroredSibling(context);
    }

    private static boolean isAnonymousTopHairTuft(
            ScoringContext context
    ) {
        PhysicsBoneGeometry.Node parent = context.node().parent();
        return context.hint() == PhysicsBoneSelectionPlan.PartType.GENERIC
                && parent != null
                && isHeadShell(parent, context)
                && context.node().bone().children().isEmpty()
                && context.belowHead() >= -1.45D
                && context.belowHead() <= -0.45D
                && context.lateralHead() <= 0.65D
                && Math.abs(context.behindHead()) <= 0.85D
                && context.size().x <= context.headWidth() * 0.85D
                && context.size().y <= context.headHeight() * 0.85D
                && context.size().z <= context.headWidth() * 0.65D
                && context.visibleLength() <= context.headWidth() * 0.90D;
    }

    private static boolean isAnonymousFringe(ScoringContext context) {
        boolean attachedToHeadShell = context.node().parent() != null
                && isHeadShell(context.node().parent(), context);
        if (context.hint() != PhysicsBoneSelectionPlan.PartType.GENERIC
                || context.behindHead() > -0.18D
                || context.belowHead() < -0.65D
                || context.belowHead() > 1.10D
                || context.lateralHead() > 0.95D
                // A full fringe may be wider than the head cube. Reject by
                // vertical length instead of the largest AABB dimension.
                || context.size().y > context.headHeight() * 1.15D
                || context.size().x > context.headWidth() * 1.60D) {
            return false;
        }
        return context.thinScore() >= 0.40D
                || hasFrontSiblingCluster(context)
                // Rotated multi-cube bangs can have a thick aggregate AABB.
                // A front-facing leaf directly attached to a verified hair
                // shell still has strong structural evidence.
                || (attachedToHeadShell
                && context.node().bone().children().isEmpty()
                && context.thinScore() >= 0.20D);
    }

    private static boolean hasFrontSiblingCluster(
            ScoringContext context
    ) {
        PhysicsBoneGeometry.Node parent = context.node().parent();
        if (parent == null) {
            return false;
        }
        int matching = 0;
        for (AnimatedGeoBone siblingBone : parent.bone().children()) {
            PhysicsBoneGeometry.Node sibling =
                    context.geometry().node(siblingBone);
            if (sibling == null || !sibling.hasGeometry()) {
                continue;
            }
            Vector3f center = sibling.center();
            Vector3f size = sibling.size();
            double behind = (center.z - context.headCenter().z)
                    / context.headWidth();
            double below = (context.headCenter().y - center.y)
                    / context.headHeight();
            double lateral = Math.abs(
                    center.x - context.headCenter().x
            ) / context.headWidth();
            double visibleLength = Math.max(
                    size.x,
                    Math.max(size.y, size.z)
            );
            if (behind <= -0.18D
                    && below >= -0.65D
                    && below <= 1.10D
                    && lateral <= 1.05D
                    && visibleLength <= context.headWidth() * 0.90D
                    && ++matching >= 2) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHeadShell(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            Vector3f headCenter,
            double headWidth,
            double headHeight,
            PhysicsBoneSelectionPlan.PartType hint
    ) {
        if (!geometry.isInHeadSubtree(node) || node.bone().children().isEmpty()) {
            return false;
        }
        Vector3f center = node.center();
        Vector3f size = node.size();
        double distance = Math.sqrt(
                DiscoveryMath.square((center.x - headCenter.x) / headWidth)
                        + DiscoveryMath.square((center.y - headCenter.y) / headHeight)
                        + DiscoveryMath.square((center.z - headCenter.z) / headWidth)
        );
        boolean headSized = size.x >= headWidth * 0.65D
                && size.z >= headWidth * 0.55D
                && size.y >= headHeight * 0.18D;
        return headSized && distance <= 1.15D
                && (node.bone().children().size() >= 2
                || hint == PhysicsBoneSelectionPlan.PartType.HAIR);
    }

    private static boolean hasMirroredSibling(ScoringContext context) {
        PhysicsBoneGeometry.Node node = context.node();
        if (node.parent() == null) {
            return false;
        }
        Vector3f center = context.center();
        double offsetX = center.x - context.headCenter().x;
        for (AnimatedGeoBone siblingBone : node.parent().bone().children()) {
            PhysicsBoneGeometry.Node sibling = context.geometry().node(siblingBone);
            if (siblingBone == node.bone() || sibling == null || !sibling.hasGeometry()) {
                continue;
            }
            Vector3f other = sibling.center();
            double otherX = other.x - context.headCenter().x;
            if (offsetX * otherX < 0.0D
                    && Math.abs(Math.abs(offsetX) - Math.abs(otherX))
                    <= context.headWidth() * 0.30D
                    && Math.abs(center.y - other.y) <= context.headWidth() * 0.30D
                    && Math.abs(center.z - other.z) <= context.headWidth() * 0.35D
                    && similarSize(context.size(), sibling.size())) {
                return true;
            }
        }
        return false;
    }

    private static boolean similarSize(Vector3f first, Vector3f second) {
        return Math.max(
                DiscoveryMath.ratio(first.x, second.x),
                Math.max(
                        DiscoveryMath.ratio(first.y, second.y),
                        DiscoveryMath.ratio(first.z, second.z)
                )
        ) <= 2.5D;
    }
}
