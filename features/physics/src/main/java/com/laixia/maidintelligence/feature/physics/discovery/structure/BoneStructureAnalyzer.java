package com.laixia.maidintelligence.feature.physics.discovery.structure;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import org.joml.Vector3f;

import java.util.IdentityHashMap;

/**
 * Derives conservative rigid/flexible evidence without relying on bone names.
 */
public final class BoneStructureAnalyzer {
    private static final double EPSILON = 1.0E-6D;

    private BoneStructureAnalyzer() {
    }

    public static BoneStructureAnalysis analyze(
            PhysicsBoneGeometry.Analysis geometry
    ) {
        IdentityHashMap<BoneModelSnapshot.Bone, BoneStructureMetrics> result =
                new IdentityHashMap<>();
        PhysicsBoneGeometry.Bounds headBounds = geometry.headBounds();
        Vector3f headSize = headBounds.size();
        Vector3f headCenter = headBounds.center();
        double headWidth = Math.max(
                EPSILON,
                Math.max(headSize.x, headSize.z)
        );
        double headHeight = Math.max(EPSILON, headSize.y);
        for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
            result.put(
                    node.bone(),
                    measure(
                            node,
                            geometry,
                            headCenter,
                            headWidth,
                            headHeight
                    )
            );
        }
        return new BoneStructureAnalysis(
                result,
                ClothAccessoryAnalyzer.analyze(geometry)
        );
    }

    private static BoneStructureMetrics measure(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            Vector3f headCenter,
            double headWidth,
            double headHeight
    ) {
        if (!node.hasGeometry() || geometry.headBounds().isEmpty()) {
            return BoneStructureMetrics.NONE;
        }
        Vector3f size = node.size();
        double maximum = StructureGeometryMath.maximum(size);
        double minimum = StructureGeometryMath.minimum(size);
        boolean compact = geometry.isInHeadSubtree(node)
                && maximum <= headWidth * 0.75D
                && node.bounds().volume()
                <= geometry.headBounds().volume() * 0.28D;
        boolean elongated = maximum / Math.max(EPSILON, minimum) >= 2.80D;
        boolean mirrored = StructureGeometryMath.hasMirroredSibling(
                node,
                geometry,
                headCenter,
                headWidth,
                headHeight
        );
        boolean coincident = StructureGeometryMath.coincidentFamily(
                node,
                geometry,
                Math.max(0.04D, headWidth * 0.10D)
        );
        boolean distalDescendant =
                StructureGeometryMath.hasDistalDescendant(
                        node,
                        geometry,
                        headWidth
                );
        int cubeCount = node.bone().geometry().cubes().getCubeCount();
        boolean longLeaf = geometry.isInHeadSubtree(node)
                && (cubeCount >= 2 || elongated)
                && !hasVisibleDescendant(node, geometry)
                && size.y >= headHeight * 1.10D
                && maximum >= headWidth * 1.05D
                && headCenter.y - node.center().y >= headHeight * 0.25D
                && Math.abs(node.pivot().y - headCenter.y)
                <= headHeight * 1.10D;
        return new BoneStructureMetrics(
                compact,
                elongated,
                longLeaf,
                mirrored,
                coincident,
                distalDescendant,
                cubeCount
        );
    }

    private static boolean hasVisibleDescendant(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        for (BoneModelSnapshot.Bone childBone : node.bone().children()) {
            PhysicsBoneGeometry.Node child = geometry.node(childBone);
            if (child != null
                    && (child.hasGeometry()
                    || hasVisibleDescendant(child, geometry))) {
                return true;
            }
        }
        return false;
    }
}
