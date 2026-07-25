package com.laixia.maidintelligence.feature.physics.client.discovery.structure;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;

/**
 * Traversal-only evidence kept separate from geometric measurements.
 */
final class ClothStructureTopology {
    private ClothStructureTopology() {
    }

    static boolean hasFacialDescendant(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        for (AnimatedGeoBone childBone : node.bone().children()) {
            if (PhysicsBoneClassifier.isFacialFeature(childBone.getName())) {
                return true;
            }
            PhysicsBoneGeometry.Node child = geometry.node(childBone);
            if (child != null && hasFacialDescendant(child, geometry)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasSkirtDescendant(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            double parentSpan
    ) {
        for (AnimatedGeoBone childBone : node.bone().children()) {
            PhysicsBoneGeometry.Node child = geometry.node(childBone);
            if (child == null) {
                continue;
            }
            PhysicsBoneClassifier.Classification semantic =
                    PhysicsBoneClassifier.classifyVisibleGeometry(
                            childBone.getName()
                    );
            double childSpan = Math.max(child.size().x, child.size().z);
            if (child.hasGeometry()
                    && (semantic.type()
                    == PhysicsBoneClassifier.ChainType.SKIRT
                    || (!semantic.isPhysical()
                    && childSpan >= parentSpan * 0.45D
                    && child.center().y < node.pivot().y))) {
                return true;
            }
            if (!child.hasGeometry()
                    && hasSkirtDescendant(child, geometry, parentSpan)) {
                return true;
            }
        }
        return false;
    }
}
