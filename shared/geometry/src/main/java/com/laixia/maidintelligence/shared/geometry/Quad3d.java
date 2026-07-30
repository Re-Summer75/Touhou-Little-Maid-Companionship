package com.laixia.maidintelligence.shared.geometry;

import java.util.List;
import java.util.Objects;

/**
 * Four model vertices in adapter-provided order.
 */
public record Quad3d(
        Vertex3d first,
        Vertex3d second,
        Vertex3d third,
        Vertex3d fourth
) {
    public Quad3d {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(third, "third");
        Objects.requireNonNull(fourth, "fourth");
    }

    public static Quad3d fromPositions(List<Vec3d> positions) {
        Objects.requireNonNull(positions, "positions");
        if (positions.size() != 4) {
            throw new IllegalArgumentException("a quad must contain exactly four positions");
        }
        return new Quad3d(
                new Vertex3d(positions.get(0)),
                new Vertex3d(positions.get(1)),
                new Vertex3d(positions.get(2)),
                new Vertex3d(positions.get(3))
        );
    }

    public List<Vertex3d> vertices() {
        return List.of(first, second, third, fourth);
    }

    public List<Vec3d> positions() {
        return List.of(
                first.position(),
                second.position(),
                third.position(),
                fourth.position()
        );
    }

    public Vec3d center() {
        return first.position()
                .add(second.position())
                .add(third.position())
                .add(fourth.position())
                .scale(0.25D);
    }
}
