package com.laixia.maidintelligence.feature.physics.engine.collision.bake;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;

import java.util.Objects;
import java.util.Optional;

/**
 * Read-only body landmarks measured in rest-pose model space. Bounds use the
 * same block units as {@link PhysicsBoneGeometry.Analysis}.
 */
public record BodyCollisionGeometry(
        PhysicsBoneGeometry.Node body,
        PhysicsBoneGeometry.Bounds bodyBounds
) {
    public BodyCollisionGeometry {
        bodyBounds = Objects.requireNonNull(bodyBounds, "bodyBounds");
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
