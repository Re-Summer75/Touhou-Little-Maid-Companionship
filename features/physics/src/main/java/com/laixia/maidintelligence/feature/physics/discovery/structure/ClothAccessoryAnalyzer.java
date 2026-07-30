package com.laixia.maidintelligence.feature.physics.discovery.structure;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;


import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import org.joml.Vector3f;

import java.util.IdentityHashMap;

/**
 * Derives conservative cloth, pendant, and wearable evidence from rest geometry.
 */
final class ClothAccessoryAnalyzer {
    private static final double EPSILON = 1.0E-6D;
    private static final double VOLUMETRIC_CUBE_RATIO = 0.45D;
    private static final double CORE_VOLUME_SHARE = 0.10D;

    private ClothAccessoryAnalyzer() {
    }

    static IdentityHashMap<BoneModelSnapshot.Bone, ClothAccessoryMetrics> analyze(
            PhysicsBoneGeometry.Analysis geometry
    ) {
        IdentityHashMap<BoneModelSnapshot.Bone, ClothAccessoryMetrics> result =
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
                                metrics.rigidClothMount(),
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
        boolean namedSoftPart =
                PhysicsBoneClassifier.classifyVisibleGeometry(
                        node.bone().getName()
                ).isPhysical();
        boolean rigidCore = hasVolumetricCore(node);
        boolean lowerPanel = !inHead
                && verticalRatio < 0.60D
                && horizontalSpan >= modelWidth * 0.12D
                && size.y >= modelHeight * 0.05D
                && (!rigidCore || namedSoftPart)
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
                && node.bone().geometry().cubes().getCubeCount() >= 4
                && horizontalSpan >= modelWidth * 0.25D
                && (namedCompoundCloth
                || !ClothStructureTopology.hasSkirtDescendant(
                        node,
                        geometry,
                        horizontalSpan
                ));
        boolean rigidClothMount = isRigidClothMount(node, geometry);
        return new ClothAccessoryMetrics(
                lowerPanel,
                narrowPendant,
                sideMountedHead,
                upperAttached,
                hangingHead,
                enclosing,
                facialDescendants,
                false,
                rigidClothMount,
                singleCloth
        );
    }

    /**
     * A short waist ring above several independent skirt panels is their
     * mount, not another spring segment. Driving it rotates every panel around
     * one central axis; one side then enters the torso whenever the opposite
     * side swings out, which no single endpoint contact can prevent.
     */
    private static boolean isRigidClothMount(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        if (node.bone().children().size() < 2
                || node.bone().geometry().cubes().getCubeCount() < 2) {
            return false;
        }
        Vector3f size = node.size();
        double maximumHorizontal = Math.max(size.x, size.z);
        double minimumHorizontal = Math.min(size.x, size.z);
        boolean enclosesPivot = node.pivot().x >= node.bounds().minX()
                && node.pivot().x <= node.bounds().maxX()
                && node.pivot().z >= node.bounds().minZ()
                && node.pivot().z <= node.bounds().maxZ();
        if (!enclosesPivot
                || minimumHorizontal < maximumHorizontal * 0.25D) {
            return false;
        }
        int hangingPanels = 0;
        double maximumPanelHeight = 0.0D;
        for (BoneModelSnapshot.Bone childBone : node.bone().children()) {
            PhysicsBoneGeometry.Node child = geometry.node(childBone);
            if (child == null || !child.hasGeometry()) {
                continue;
            }
            PhysicsBoneClassifier.Classification semantic =
                    PhysicsBoneClassifier.classifyVisibleGeometry(
                            childBone.getName()
                    );
            if (semantic.type() != PhysicsBoneClassifier.ChainType.SKIRT
                    || child.size().y < size.y * 1.75D
                    || child.center().y
                    >= node.center().y - size.y * 0.25D) {
                continue;
            }
            hangingPanels++;
            maximumPanelHeight = Math.max(
                    maximumPanelHeight,
                    child.size().y
            );
        }
        return hangingPanels >= 2
                && size.y <= maximumPanelHeight * 0.45D;
    }

    /**
     * A broad lower-body AABB is not sufficient cloth evidence: a jacket,
     * pelvis, or shorts shell can be just as wide. Real segmented skirts are
     * assembled from plates even when their union surrounds the whole body;
     * a substantial cube that is thick on all three axes instead identifies a
     * rigid body core. Explicit soft-part names remain authoritative.
     */
    private static boolean hasVolumetricCore(
            PhysicsBoneGeometry.Node node
    ) {
        double boundsVolume = Math.max(EPSILON, node.bounds().volume());
        for (PhysicsBoneGeometry.CubeBox cube : node.cubeBoxes()) {
            Vector3f half = cube.half();
            double maximum = Math.max(
                    half.x,
                    Math.max(half.y, half.z)
            );
            double minimum = Math.min(
                    half.x,
                    Math.min(half.y, half.z)
            );
            double volume = 8.0D * half.x * half.y * half.z;
            if (minimum / Math.max(EPSILON, maximum)
                    >= VOLUMETRIC_CUBE_RATIO
                    && volume / boundsVolume >= CORE_VOLUME_SHARE) {
                return true;
            }
        }
        return false;
    }

    private static boolean dominantAttachmentBody(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            IdentityHashMap<BoneModelSnapshot.Bone, ClothAccessoryMetrics> metrics
    ) {
        if (!node.hasGeometry() || !geometry.isInHeadSubtree(node)) {
            return false;
        }
        for (BoneModelSnapshot.Bone childBone : node.bone().children()) {
            PhysicsBoneGeometry.Node child = geometry.node(childBone);
            ClothAccessoryMetrics childMetrics = metrics.get(childBone);
            if (child == null
                    || childMetrics == null
                    || !child.hasGeometry()
                    || !childMetrics.hangingHeadAccessory()) {
                continue;
            }
            int ownCubes = node.bone().geometry().cubes().getCubeCount();
            int childCubes = childBone.geometry().cubes().getCubeCount();
            if (ownCubes >= Math.max(4, childCubes * 2)
                    && node.bounds().volume()
                    >= child.bounds().volume() * 1.20D) {
                return true;
            }
        }
        return false;
    }

}
