package com.laixia.maidintelligence.feature.physics.client.discovery.structure;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import org.joml.Vector3f;

final class StructureGeometryMath {
    private static final double EPSILON = 1.0E-6D;

    private StructureGeometryMath() {
    }

    static boolean hasMirroredSibling(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            Vector3f headCenter,
            double headWidth,
            double headHeight
    ) {
        if (node.parent() == null) {
            return false;
        }
        Vector3f center = node.center();
        Vector3f size = node.size();
        double offsetX = center.x - headCenter.x;
        for (AnimatedGeoBone siblingBone : node.parent().bone().children()) {
            PhysicsBoneGeometry.Node sibling = geometry.node(siblingBone);
            if (siblingBone == node.bone()
                    || sibling == null
                    || !sibling.hasGeometry()) {
                continue;
            }
            Vector3f otherCenter = sibling.center();
            Vector3f otherSize = sibling.size();
            double otherX = otherCenter.x - headCenter.x;
            if (offsetX * otherX < 0.0D
                    && Math.abs(Math.abs(offsetX) - Math.abs(otherX))
                    <= headWidth * 0.30D
                    && Math.abs(center.y - otherCenter.y)
                    <= headHeight * 0.45D
                    && Math.abs(center.z - otherCenter.z)
                    <= headWidth * 0.45D
                    && similar(size, otherSize)) {
                return true;
            }
        }
        return false;
    }

    static boolean coincidentFamily(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            double pivotTolerance
    ) {
        if (coincident(node, node.parent(), pivotTolerance)) {
            return true;
        }
        for (AnimatedGeoBone childBone : node.bone().children()) {
            if (coincident(
                    node,
                    geometry.node(childBone),
                    pivotTolerance
            )) {
                return true;
            }
        }
        return false;
    }

    static boolean hasDistalDescendant(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            double headWidth
    ) {
        Vector3f ownSize = node.size();
        double ownSpan = maximum(ownSize);
        for (AnimatedGeoBone childBone : node.bone().children()) {
            PhysicsBoneGeometry.Node child = geometry.node(childBone);
            if (child == null || child.subtreeBounds().isEmpty()) {
                continue;
            }
            Vector3f childSize = child.subtreeBounds().size();
            double childSpan = maximum(childSize);
            if (childSpan >= Math.max(headWidth * 0.55D, ownSpan * 1.45D)
                    && child.subtreeBounds().center().distance(node.center())
                    >= Math.max(headWidth * 0.20D, ownSpan * 0.35D)) {
                return true;
            }
        }
        return false;
    }

    static double maximum(Vector3f size) {
        return Math.max(size.x, Math.max(size.y, size.z));
    }

    static double minimum(Vector3f size) {
        return Math.min(size.x, Math.min(size.y, size.z));
    }

    private static boolean coincident(
            PhysicsBoneGeometry.Node first,
            PhysicsBoneGeometry.Node second,
            double pivotTolerance
    ) {
        if (first == null
                || second == null
                || !first.hasGeometry()
                || !second.hasGeometry()
                || first.pivot().distance(second.pivot()) > pivotTolerance) {
            return false;
        }
        double overlap = overlapVolume(first.bounds(), second.bounds());
        double smaller = Math.min(
                first.bounds().volume(),
                second.bounds().volume()
        );
        return smaller > EPSILON && overlap / smaller >= 0.42D;
    }

    private static double overlapVolume(
            PhysicsBoneGeometry.Bounds first,
            PhysicsBoneGeometry.Bounds second
    ) {
        double x = Math.max(
                0.0D,
                Math.min(first.maxX(), second.maxX())
                        - Math.max(first.minX(), second.minX())
        );
        double y = Math.max(
                0.0D,
                Math.min(first.maxY(), second.maxY())
                        - Math.max(first.minY(), second.minY())
        );
        double z = Math.max(
                0.0D,
                Math.min(first.maxZ(), second.maxZ())
                        - Math.max(first.minZ(), second.minZ())
        );
        return x * y * z;
    }

    private static boolean similar(Vector3f first, Vector3f second) {
        return Math.max(
                ratio(first.x, second.x),
                Math.max(
                        ratio(first.y, second.y),
                        ratio(first.z, second.z)
                )
        ) <= 2.75D;
    }

    private static double ratio(double first, double second) {
        double small = Math.max(EPSILON, Math.min(first, second));
        return Math.max(first, second) / small;
    }
}
