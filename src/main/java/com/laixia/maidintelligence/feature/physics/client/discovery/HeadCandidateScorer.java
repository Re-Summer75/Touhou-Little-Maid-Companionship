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
                + context.semanticScore();
        return Candidate.of(
                type,
                confidence,
                attachedToShell
                        ? "flexible child attached to visible head shell"
                        : "flexible geometry in head subtree"
        );
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
