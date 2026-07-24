package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Extracts conservative collision landmarks without changing discovery plans
 * or solver layout.
 */
public final class BodyCollisionGeometryAnalyzer {
    private static final Set<String> BODY_NAMES = Set.of(
            "body", "upperbody", "upbody", "torso"
    );

    private BodyCollisionGeometryAnalyzer() {
    }

    public static BodyCollisionGeometry analyze(
            PhysicsBoneGeometry.Analysis analysis
    ) {
        Objects.requireNonNull(analysis, "analysis");
        PhysicsBoneGeometry.Node body = coherentBody(analysis);
        PhysicsBoneGeometry.Bounds bodyBounds = bodyBounds(analysis, body);
        Optional<BodyCollisionGeometry.LegPair> legs =
                findLegs(analysis, body, bodyBounds);
        return new BodyCollisionGeometry(
                analysis.head(),
                analysis.headBounds(),
                body,
                bodyBounds,
                legs
        );
    }

    private static PhysicsBoneGeometry.Node coherentBody(
            PhysicsBoneGeometry.Analysis analysis
    ) {
        PhysicsBoneGeometry.Node cursor = analysis.head();
        while (cursor != null) {
            String name = cursor.bone().getName().toLowerCase(Locale.ROOT);
            if (BODY_NAMES.contains(name)) {
                return cursor;
            }
            cursor = cursor.parent();
        }
        return analysis.body();
    }

    private static PhysicsBoneGeometry.Bounds bodyBounds(
            PhysicsBoneGeometry.Analysis analysis,
            PhysicsBoneGeometry.Node body
    ) {
        if (body == null) {
            return analysis.bodyBounds();
        }
        if (body == analysis.body()) {
            return analysis.bodyBounds();
        }
        if (!body.bounds().isEmpty()) {
            return body.bounds();
        }
        return nearestSolidBounds(analysis.nodes(), body);
    }

    private static PhysicsBoneGeometry.Bounds nearestSolidBounds(
            List<PhysicsBoneGeometry.Node> nodes,
            PhysicsBoneGeometry.Node root
    ) {
        int maximumDepth = root.depth() + root.maxChainDepth();
        for (int depth = root.depth() + 1; depth <= maximumDepth; depth++) {
            PhysicsBoneGeometry.Bounds bounds = null;
            for (PhysicsBoneGeometry.Node node : nodes) {
                if (node.depth() == depth
                        && node.hasGeometry()
                        && node.isDescendantOf(root)) {
                    bounds = bounds == null
                            ? node.bounds()
                            : bounds.union(node.bounds());
                }
            }
            if (bounds != null) {
                return bounds;
            }
        }
        return root.subtreeBounds();
    }

    private static Optional<BodyCollisionGeometry.LegPair> findLegs(
            PhysicsBoneGeometry.Analysis analysis,
            PhysicsBoneGeometry.Node body,
            PhysicsBoneGeometry.Bounds bodyBounds
    ) {
        if (body == null || bodyBounds.isEmpty()) {
            return Optional.empty();
        }
        List<PhysicsBoneGeometry.Node> left =
                analysis.byName().getOrDefault("leftleg", List.of());
        List<PhysicsBoneGeometry.Node> right =
                analysis.byName().getOrDefault("rightleg", List.of());
        if (!left.isEmpty() || !right.isEmpty()) {
            if (left.size() == 1
                    && right.size() == 1
                    && LegPairValidator.valid(
                    left.get(0), right.get(0), body, bodyBounds, false
            )) {
                return Optional.of(pair(left.get(0), right.get(0)));
            }
            return Optional.empty();
        }
        return geometricPair(analysis.nodes(), body, bodyBounds);
    }

    private static Optional<BodyCollisionGeometry.LegPair> geometricPair(
            List<PhysicsBoneGeometry.Node> nodes,
            PhysicsBoneGeometry.Node body,
            PhysicsBoneGeometry.Bounds bodyBounds
    ) {
        BodyCollisionGeometry.LegPair match = null;
        for (int leftIndex = 0; leftIndex < nodes.size(); leftIndex++) {
            PhysicsBoneGeometry.Node first = nodes.get(leftIndex);
            if (!fallbackCandidate(first, bodyBounds)) {
                continue;
            }
            for (int rightIndex = leftIndex + 1;
                 rightIndex < nodes.size();
                 rightIndex++) {
                PhysicsBoneGeometry.Node second = nodes.get(rightIndex);
                if (first.parent() == null
                        || first.parent() != second.parent()
                        || !fallbackCandidate(second, bodyBounds)) {
                    continue;
                }
                PhysicsBoneGeometry.Node left =
                        first.center().x >= second.center().x ? first : second;
                PhysicsBoneGeometry.Node right = left == first ? second : first;
                if (!LegPairValidator.valid(
                        left, right, body, bodyBounds, true
                )) {
                    continue;
                }
                if (match != null) {
                    return Optional.empty();
                }
                match = pair(left, right);
            }
        }
        return Optional.ofNullable(match);
    }

    private static boolean fallbackCandidate(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Bounds bodyBounds
    ) {
        String name = node.bone().getName().toLowerCase(Locale.ROOT);
        return node.hasGeometry()
                && !node.subtreeBounds().isEmpty()
                && node.center().y < bodyBounds.center().y
                && !name.matches(".*(?:left|right)?leg\\d+.*");
    }

    private static BodyCollisionGeometry.LegPair pair(
            PhysicsBoneGeometry.Node left,
            PhysicsBoneGeometry.Node right
    ) {
        return new BodyCollisionGeometry.LegPair(
                new BodyCollisionGeometry.Leg(left, left.subtreeBounds()),
                new BodyCollisionGeometry.Leg(right, right.subtreeBounds())
        );
    }
}
