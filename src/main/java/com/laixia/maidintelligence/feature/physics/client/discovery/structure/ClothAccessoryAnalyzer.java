package com.laixia.maidintelligence.feature.physics.client.discovery.structure;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import org.joml.Vector3f;

import java.util.IdentityHashMap;

/**
 * Derives conservative cloth, pendant, and wearable evidence from rest geometry.
 */
final class ClothAccessoryAnalyzer {
    private static final double EPSILON = 1.0E-6D;

    private ClothAccessoryAnalyzer() {
    }

    static IdentityHashMap<AnimatedGeoBone, ClothAccessoryMetrics> analyze(
            PhysicsBoneGeometry.Analysis geometry
    ) {
        IdentityHashMap<AnimatedGeoBone, ClothAccessoryMetrics> result =
                new IdentityHashMap<>();
        for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
            result.put(node.bone(), measure(node, geometry));
        }
        for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
            ClothAccessoryMetrics metrics = result.get(node.bone());
            if (metrics != null
                    && dominantAttachmentBody(node, geometry, result)) {
                result.put(
                        node.bone(),
                        new ClothAccessoryMetrics(
                                metrics.lowerBodyPanel(),
                                metrics.narrowBodyPendant(),
                                metrics.sideMountedHeadAccessory(),
                                metrics.upperEdgeAttached(),
                                metrics.hangingHeadAccessory(),
                                metrics.headEnclosingWearable(),
                                metrics.facialDescendants(),
                                true,
                                metrics.singleBoneCloth()
                        )
                );
            }
        }
        return result;
    }

    private static ClothAccessoryMetrics measure(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        if (!node.hasGeometry()) {
            return ClothAccessoryMetrics.NONE;
        }
        Vector3f size = node.size();
        Vector3f center = node.center();
        Vector3f modelSize = geometry.modelBounds().size();
        double modelWidth = Math.max(
                EPSILON,
                Math.max(modelSize.x, modelSize.z)
        );
        double modelHeight = Math.max(EPSILON, modelSize.y);
        double verticalRatio = (
                center.y - geometry.modelBounds().minY()
        ) / modelHeight;
        Vector3f modelCenter = geometry.modelBounds().center();
        double lateralModel = Math.abs(center.x - modelCenter.x);
        double depthModel = Math.abs(center.z - modelCenter.z);
        double maximum = StructureGeometryMath.maximum(size);
        double minimum = StructureGeometryMath.minimum(size);
        double elongation = maximum / Math.max(EPSILON, minimum);
        boolean inHead = geometry.isInHeadSubtree(node);
        boolean upperAttached = node.pivot().y
                >= node.bounds().maxY() - Math.max(0.08D, size.y * 0.25D);
        boolean facialDescendants =
                ClothStructureTopology.hasFacialDescendant(node, geometry);

        Vector3f headSize = geometry.headBounds().size();
        Vector3f headCenter = geometry.headBounds().center();
        double headWidth = Math.max(
                EPSILON,
                Math.max(headSize.x, headSize.z)
        );
        double headHeight = Math.max(EPSILON, headSize.y);
        double lateralHead = Math.abs(center.x - headCenter.x) / headWidth;
        double pivotLateralHead = Math.abs(
                node.pivot().x - headCenter.x
        ) / headWidth;
        double depthHead = Math.abs(center.z - headCenter.z) / headWidth;
        boolean enclosing = inHead
                && size.x >= headWidth * 0.55D
                && size.y >= headHeight * 0.55D
                && lateralHead <= 0.45D
                && pivotLateralHead <= 0.45D
                && depthHead <= 0.70D;
        boolean sideMountedHead = inHead
                && !enclosing
                && Math.max(lateralHead, pivotLateralHead) >= 0.35D
                && size.y >= headHeight * 0.35D;
        boolean hangingHead = sideMountedHead
                && !facialDescendants
                && (upperAttached
                || node.pivot().y >= center.y - size.y * 0.15D)
                && (size.y >= size.x * 0.70D
                || node.thinRatio() <= 0.15D);

        double horizontalSpan = Math.max(size.x, size.z);
        boolean lowerPanel = !inHead
                && verticalRatio < 0.60D
                && horizontalSpan >= modelWidth * 0.12D
                && size.y >= modelHeight * 0.05D
                && (node.thinRatio() <= 0.45D
                || horizontalSpan >= modelWidth * 0.28D);
        boolean narrowPendant = !inHead
                && verticalRatio < 0.72D
                && upperAttached
                && elongation >= 3.0D
                && node.thinRatio() <= 0.18D
                && size.y >= Math.max(size.x, size.z) * 0.75D
                && Math.min(size.x, size.z) <= modelWidth * 0.18D
                && (verticalRatio < 0.45D
                || lateralModel >= modelWidth * 0.15D
                || depthModel >= modelWidth * 0.15D);
        boolean namedCompoundCloth =
                "qunzi".equalsIgnoreCase(node.bone().getName())
                        || "dress".equalsIgnoreCase(
                        node.bone().getName()
                );
        boolean singleCloth = lowerPanel
                && node.bone().geoBone().cubes().getCubeCount() >= 4
                && horizontalSpan >= modelWidth * 0.25D
                && (namedCompoundCloth
                || !ClothStructureTopology.hasSkirtDescendant(
                        node,
                        geometry,
                        horizontalSpan
                ));
        return new ClothAccessoryMetrics(
                lowerPanel,
                narrowPendant,
                sideMountedHead,
                upperAttached,
                hangingHead,
                enclosing,
                facialDescendants,
                false,
                singleCloth
        );
    }

    private static boolean dominantAttachmentBody(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            IdentityHashMap<AnimatedGeoBone, ClothAccessoryMetrics> metrics
    ) {
        if (!node.hasGeometry() || !geometry.isInHeadSubtree(node)) {
            return false;
        }
        for (AnimatedGeoBone childBone : node.bone().children()) {
            PhysicsBoneGeometry.Node child = geometry.node(childBone);
            ClothAccessoryMetrics childMetrics = metrics.get(childBone);
            if (child == null
                    || childMetrics == null
                    || !child.hasGeometry()
                    || !childMetrics.hangingHeadAccessory()) {
                continue;
            }
            int ownCubes = node.bone().geoBone().cubes().getCubeCount();
            int childCubes = childBone.geoBone().cubes().getCubeCount();
            if (ownCubes >= Math.max(4, childCubes * 2)
                    && node.bounds().volume()
                    >= child.bounds().volume() * 1.20D) {
                return true;
            }
        }
        return false;
    }

}
