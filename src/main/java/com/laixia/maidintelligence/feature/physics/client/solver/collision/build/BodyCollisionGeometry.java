package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;

import java.util.Objects;
import java.util.Optional;

/**
 * Read-only body landmarks measured in rest-pose model space. Bounds use the
 * same block units as {@link PhysicsBoneGeometry.Analysis}.
 */
public record BodyCollisionGeometry(
        PhysicsBoneGeometry.Node head,
        PhysicsBoneGeometry.Bounds headBounds,
        PhysicsBoneGeometry.Node body,
        PhysicsBoneGeometry.Bounds bodyBounds,
        Optional<LegPair> legs
) {
    public BodyCollisionGeometry {
        headBounds = Objects.requireNonNull(headBounds, "headBounds");
        bodyBounds = Objects.requireNonNull(bodyBounds, "bodyBounds");
        legs = Objects.requireNonNull(legs, "legs");
    }

    public boolean hasHead() {
        return head != null && !headBounds.isEmpty();
    }

    public boolean hasBody() {
        return body != null && !bodyBounds.isEmpty();
    }

    /**
     * The model-space rear face of the body AABB. Gecko's rear direction is
     * positive Z, matching the existing appendage geometry conventions.
     */
    public Optional<BackPlane> backPlane() {
        if (!hasBody()) {
            return Optional.empty();
        }
        return Optional.of(new BackPlane(
                (bodyBounds.minX() + bodyBounds.maxX()) * 0.5D,
                (bodyBounds.minY() + bodyBounds.maxY()) * 0.5D,
                bodyBounds.maxZ(),
                0.0D,
                0.0D,
                1.0D
        ));
    }

    public record Leg(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Bounds bounds
    ) {
        public Leg {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    public record LegPair(Leg left, Leg right) {
        public LegPair {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
    }

    public record BackPlane(
            double pointX,
            double pointY,
            double pointZ,
            double normalX,
            double normalY,
            double normalZ
    ) {
    }
}
