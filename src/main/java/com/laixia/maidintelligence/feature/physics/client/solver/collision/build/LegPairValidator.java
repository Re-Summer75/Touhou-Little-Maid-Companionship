package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;

import java.util.Locale;

final class LegPairValidator {
    private static final double EPSILON = 1.0E-5D;

    private LegPairValidator() {
    }

    static boolean valid(
            PhysicsBoneGeometry.Node left,
            PhysicsBoneGeometry.Node right,
            PhysicsBoneGeometry.Node body,
            PhysicsBoneGeometry.Bounds bodyBounds,
            boolean fallback
    ) {
        PhysicsBoneGeometry.Bounds leftBounds = left.subtreeBounds();
        PhysicsBoneGeometry.Bounds rightBounds = right.subtreeBounds();
        if (left == right
                || leftBounds.isEmpty()
                || rightBounds.isEmpty()
                || excludedBranch(left)
                || excludedBranch(right)
                || !related(left, right, body)) {
            return false;
        }

        double bodyWidth = spanX(bodyBounds);
        double bodyHeight = bodyBounds.sizeY();
        double bodyDepth = spanZ(bodyBounds);
        double leftHeight = leftBounds.sizeY();
        double rightHeight = rightBounds.sizeY();
        double leftWidth = spanX(leftBounds);
        double rightWidth = spanX(rightBounds);
        if (bodyWidth <= EPSILON
                || bodyHeight <= EPSILON
                || bodyDepth <= EPSILON
                || !reasonableRatio(leftHeight, rightHeight, 0.60D)
                || !reasonableRatio(
                leftBounds.volume(), rightBounds.volume(), 0.30D
        )) {
            return false;
        }

        double bodyX = centerX(bodyBounds);
        double leftX = centerX(leftBounds) - bodyX;
        double rightX = centerX(rightBounds) - bodyX;
        double separation = Math.abs(leftX - rightX);
        if (leftX * rightX >= -EPSILON
                || !reasonableRatio(Math.abs(leftX), Math.abs(rightX), 0.45D)
                || Math.abs((leftX + rightX) * 0.5D)
                > Math.max(bodyWidth * 0.22D, separation * 0.12D)) {
            return false;
        }

        double bodyY = centerY(bodyBounds);
        double leftY = centerY(leftBounds);
        double rightY = centerY(rightBounds);
        if (leftY >= bodyY - bodyHeight * 0.10D
                || rightY >= bodyY - bodyHeight * 0.10D
                || leftBounds.maxY() > bodyBounds.maxY() + bodyHeight * 0.25D
                || rightBounds.maxY() > bodyBounds.maxY() + bodyHeight * 0.25D
                || leftBounds.minY() >= bodyBounds.minY() + bodyHeight * 0.50D
                || rightBounds.minY() >= bodyBounds.minY() + bodyHeight * 0.50D) {
            return false;
        }

        double leftDepth = spanZ(leftBounds);
        double rightDepth = spanZ(rightBounds);
        double zTolerance = Math.max(
                bodyDepth * 0.75D,
                Math.max(leftDepth, rightDepth) * 0.50D
        );
        if (Math.abs(centerZ(leftBounds) - centerZ(rightBounds))
                > Math.max(0.25D, zTolerance)
                || !relativeToBody(
                leftBounds, bodyBounds, leftWidth, leftHeight, bodyWidth, bodyHeight
        )
                || !relativeToBody(
                rightBounds, bodyBounds, rightWidth, rightHeight, bodyWidth, bodyHeight
        )) {
            return false;
        }
        return !fallback || fallbackShape(leftBounds, bodyBounds)
                && fallbackShape(rightBounds, bodyBounds);
    }

    private static boolean related(
            PhysicsBoneGeometry.Node left,
            PhysicsBoneGeometry.Node right,
            PhysicsBoneGeometry.Node body
    ) {
        PhysicsBoneGeometry.Node pairRoot = commonAncestor(left, right);
        PhysicsBoneGeometry.Node skeleton = commonAncestor(body, pairRoot);
        return pairRoot != null
                && skeleton != null
                && distance(left, pairRoot) <= 2
                && distance(right, pairRoot) <= 2
                && distance(body, skeleton) <= 4
                && distance(pairRoot, skeleton) <= 3;
    }

    private static boolean relativeToBody(
            PhysicsBoneGeometry.Bounds leg,
            PhysicsBoneGeometry.Bounds body,
            double width,
            double height,
            double bodyWidth,
            double bodyHeight
    ) {
        double bodyVolume = body.volume();
        double volumeRatio = bodyVolume <= EPSILON
                ? Double.POSITIVE_INFINITY
                : leg.volume() / bodyVolume;
        return height >= bodyHeight * 0.30D
                && height <= bodyHeight * 4.50D
                && width <= bodyWidth
                && volumeRatio >= 0.005D
                && volumeRatio <= 6.0D;
    }

    private static boolean fallbackShape(
            PhysicsBoneGeometry.Bounds leg,
            PhysicsBoneGeometry.Bounds body
    ) {
        double height = leg.sizeY();
        return leg.maxY() <= body.minY() + body.sizeY() * 0.45D
                && height >= Math.max(spanX(leg), spanZ(leg)) * 1.20D;
    }

    private static boolean excludedBranch(PhysicsBoneGeometry.Node node) {
        PhysicsBoneGeometry.Node cursor = node;
        while (cursor != null) {
            if ("fox".equals(cursor.bone().getName().toLowerCase(Locale.ROOT))) {
                return true;
            }
            cursor = cursor.parent();
        }
        return false;
    }

    private static PhysicsBoneGeometry.Node commonAncestor(
            PhysicsBoneGeometry.Node first,
            PhysicsBoneGeometry.Node second
    ) {
        if (first == null || second == null) {
            return null;
        }
        while (first.depth() > second.depth()) {
            first = first.parent();
        }
        while (second.depth() > first.depth()) {
            second = second.parent();
        }
        while (first != second) {
            first = first.parent();
            second = second.parent();
        }
        return first;
    }

    private static int distance(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Node ancestor
    ) {
        return node.depth() - ancestor.depth();
    }

    private static boolean reasonableRatio(double first, double second, double minimum) {
        double maximum = Math.max(first, second);
        return maximum > EPSILON && Math.min(first, second) / maximum >= minimum;
    }

    private static double spanX(PhysicsBoneGeometry.Bounds bounds) {
        return bounds.maxX() - bounds.minX();
    }

    private static double spanZ(PhysicsBoneGeometry.Bounds bounds) {
        return bounds.maxZ() - bounds.minZ();
    }

    private static double centerX(PhysicsBoneGeometry.Bounds bounds) {
        return (bounds.minX() + bounds.maxX()) * 0.5D;
    }

    private static double centerY(PhysicsBoneGeometry.Bounds bounds) {
        return (bounds.minY() + bounds.maxY()) * 0.5D;
    }

    private static double centerZ(PhysicsBoneGeometry.Bounds bounds) {
        return (bounds.minZ() + bounds.maxZ()) * 0.5D;
    }
}
