package com.laixia.maidintelligence.shared.geometry;

import java.util.List;

/**
 * Narrow pull-free boundary for traversing transformed model elements.
 *
 * @param <M> adapter-owned model type
 * @param <C> adapter-owned read context, such as a pose snapshot
 */
@FunctionalInterface
public interface ModelGeometryReader<M, C> {
    void read(M model, C context, GeometryVisitor visitor);

    @FunctionalInterface
    interface GeometryVisitor {
        void visit(
                String partPath,
                int elementIndex,
                Bounds3d bounds,
                List<Quad3d> faces
        );
    }
}
