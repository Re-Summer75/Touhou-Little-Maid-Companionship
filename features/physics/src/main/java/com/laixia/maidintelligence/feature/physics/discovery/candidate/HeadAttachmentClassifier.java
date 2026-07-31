package com.laixia.maidintelligence.feature.physics.discovery.candidate;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.discovery.classifier.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.discovery.structure.BoneStructureMetrics;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Distinguishes compact rigid mounts from their independently flexible tails.
 */
final class HeadAttachmentClassifier {
    private static final Set<String> FIXED_TOKENS = Set.of(
            "flower", "clip", "hairclip", "hairpin", "pin",
            "ornament", "decoration", "accessory", "ball", "balls"
    );
    private static final List<String> FIXED_MARKERS = List.of(
            "hairclip", "hairpin", "ornament", "decoration",
            "发夹", "髮夾", "发饰", "髮飾", "髪飾り", "ヘアピン",
            "머리핀", "머리장식"
    );

    private HeadAttachmentClassifier() {
    }

    static boolean isRigid(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            BoneStructureMetrics structure
    ) {
        if (!node.hasGeometry()
                || !geometry.isInHeadSubtree(node)
                || geometry.headBounds().isEmpty()) {
            return false;
        }
        List<String> tokens = DiscoveryMath.tokens(node.bone().getName());
        boolean named = isNamed(node.bone().getName());
        if (!named) {
            boolean directlyFlexible = PhysicsBoneClassifier
                    .classifyVisibleGeometry(node.bone().getName())
                    .isPhysical();
            return !directlyFlexible
                    && structure.structurallyRigidAttachment();
        }
        Shape shape = shape(node, geometry);
        boolean pairedBall = tokens.contains("ball")
                || tokens.contains("balls");
        if (node.bone().children().isEmpty()) {
            return shape.compact() || pairedBall;
        }
        if (!shape.compact()) {
            return false;
        }
        if (structure.structurallyRigidAttachment()) {
            return true;
        }
        for (BoneModelSnapshot.Bone childBone : node.bone().children()) {
            PhysicsBoneGeometry.Node child = geometry.node(childBone);
            if (child != null && (
                    PhysicsBoneClassifier
                            .classifyVisibleGeometry(childBone.getName())
                            .isPhysical()
                            || child.maxChainDepth() > 0
            )) {
                return true;
            }
        }
        return false;
    }

    static String reason(BoneStructureMetrics structure) {
        return structure.structurallyRigidAttachment()
                ? "compact rigid attachment structure"
                : "named rigid head attachment";
    }

    static boolean isNamed(String name) {
        List<String> tokens = DiscoveryMath.tokens(name);
        String lowerName = name.toLowerCase(Locale.ROOT);
        return tokens.stream().anyMatch(FIXED_TOKENS::contains)
                || FIXED_MARKERS.stream().anyMatch(
                marker -> lowerName.contains(
                        marker.toLowerCase(Locale.ROOT)
                )
        );
    }

    private static Shape shape(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        Vector3f size = node.size();
        Vector3f headSize = geometry.headBounds().size();
        double headWidth = Math.max(
                Math.max(headSize.x, headSize.z),
                DiscoveryMath.EPSILON
        );
        double maximum = Math.max(size.x, Math.max(size.y, size.z));
        return new Shape(
                maximum <= headWidth * 0.60D
                        && node.bounds().volume()
                        <= geometry.headBounds().volume() * 0.20D
        );
    }

    private record Shape(boolean compact) {
    }
}
