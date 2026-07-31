package com.laixia.maidintelligence.feature.physics.discovery.candidate;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import org.joml.Vector3f;

record ScoringContext(
        PhysicsBoneGeometry.Node node,
        PhysicsBoneGeometry.Analysis geometry,
        PhysicsBoneSelectionPlan.PartType hint,
        double semanticScore,
        boolean strongSemantic,
        PhysicsBoneGeometry.Bounds bodyBounds,
        Vector3f bodyCenter,
        Vector3f center,
        Vector3f size,
        Vector3f headCenter,
        double modelWidth,
        double bodyWidth,
        double headWidth,
        double headHeight,
        double thinScore,
        double chainScore,
        double visibleLength,
        double lengthScore,
        boolean inHead,
        double lateralHead,
        double belowHead,
        double behindHead,
        double yRatio,
        double lateralBody,
        double behindBody
) {
    static ScoringContext create(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            PhysicsBoneSelectionPlan.PartType hint,
            double semanticScore,
            boolean strongSemantic
    ) {
        PhysicsBoneGeometry.Bounds modelBounds = geometry.modelBounds();
        PhysicsBoneGeometry.Bounds headBounds = geometry.headBounds();
        PhysicsBoneGeometry.Bounds bodyBounds = geometry.bodyBounds().isEmpty()
                ? modelBounds
                : geometry.bodyBounds();
        Vector3f modelCenter = modelBounds.center();
        Vector3f bodyCenter = bodyBounds.center();
        Vector3f center = node.center();
        Vector3f size = node.size();
        double modelHeight = Math.max(modelBounds.sizeY(), DiscoveryMath.EPSILON);
        double modelWidth = Math.max(
                Math.max(modelBounds.size().x, modelBounds.size().z),
                DiscoveryMath.EPSILON
        );
        double bodyWidth = Math.max(
                Math.max(bodyBounds.size().x, bodyBounds.size().z),
                DiscoveryMath.EPSILON
        );
        double headWidth = headBounds.isEmpty()
                ? modelWidth * 0.28D
                : Math.max(headBounds.size().x, headBounds.size().z);
        double headHeight = headBounds.isEmpty()
                ? modelHeight * 0.22D
                : Math.max(headBounds.size().y, DiscoveryMath.EPSILON);
        Vector3f headCenter = headBounds.isEmpty()
                ? new Vector3f(
                modelCenter.x,
                (float) (modelBounds.maxY() - headHeight * 0.5D),
                modelCenter.z
        )
                : headBounds.center();
        double thinScore = DiscoveryMath.clamp(
                (0.58D - node.thinRatio()) / 0.48D
        );
        double chainScore = DiscoveryMath.clamp(
                (node.maxChainDepth()
                        + (node.parent() != null
                        && node.parent().bone().children().size() == 1 ? 1 : 0))
                        / 3.0D
        );
        double visibleLength = Math.max(size.x, Math.max(size.y, size.z));
        double lengthScore = DiscoveryMath.clamp(
                visibleLength / Math.max(headWidth * 1.25D, DiscoveryMath.EPSILON)
        );
        double lateralHead = Math.abs(center.x - headCenter.x)
                / Math.max(headWidth, DiscoveryMath.EPSILON);
        double belowHead = (headCenter.y - center.y)
                / Math.max(headHeight, DiscoveryMath.EPSILON);
        double behindHead = (center.z - headCenter.z)
                / Math.max(headWidth, DiscoveryMath.EPSILON);
        return new ScoringContext(
                node, geometry, hint, semanticScore, strongSemantic,
                bodyBounds, bodyCenter,
                center, size, headCenter, modelWidth, bodyWidth, headWidth,
                headHeight, thinScore, chainScore, visibleLength, lengthScore,
                geometry.isInHeadSubtree(node), lateralHead, belowHead, behindHead,
                (center.y - modelBounds.minY()) / modelHeight,
                Math.abs(center.x - bodyCenter.x) / bodyWidth,
                (center.z - bodyCenter.z) / bodyWidth
        );
    }
}
