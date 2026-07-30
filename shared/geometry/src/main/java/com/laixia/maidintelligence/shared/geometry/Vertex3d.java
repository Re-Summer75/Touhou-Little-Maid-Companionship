package com.laixia.maidintelligence.shared.geometry;

import java.util.Objects;

/**
 * A model vertex whose optional attributes remain adapter-owned.
 */
public record Vertex3d(Vec3d position) {
    public Vertex3d {
        Objects.requireNonNull(position, "position");
    }
}
