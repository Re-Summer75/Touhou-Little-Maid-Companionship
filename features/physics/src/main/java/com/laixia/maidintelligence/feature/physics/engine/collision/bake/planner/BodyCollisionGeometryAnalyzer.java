package com.laixia.maidintelligence.feature.physics.engine.collision.bake.planner;

import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Extracts conservative collision landmarks without changing discovery plans.
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
        return new BodyCollisionGeometry(body, bodyBounds);
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
        if (body == null || body == analysis.body()) {
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
}
