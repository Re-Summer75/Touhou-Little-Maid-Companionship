package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import org.joml.Vector3f;

/** Rest-pose reach of one driven segment, shared by the collider planners. */
final class SegmentReach {
    private SegmentReach() {
    }

    /**
     * Rest direction of the segment, from its pivot toward the geometry it
     * carries. Falls back to hanging straight down.
     */
    static Vector3f restDirection(
            PhysicsBoneGeometry.Node node,
            Vector3f output
    ) {
        PhysicsBoneGeometry.Bounds bounds = bounds(node);
        if (bounds.isEmpty()) {
            return output.set(0.0F, -1.0F, 0.0F);
        }
        output.set(bounds.center()).sub(node.pivot());
        return output.lengthSquared() <= 1.0E-10F
                ? output.set(0.0F, -1.0F, 0.0F)
                : output;
    }

    /**
     * Upper bound on how far this bone's endpoint can travel from its pivot.
     */
    static float reach(PhysicsBoneGeometry.Node node) {
        PhysicsBoneGeometry.Bounds bounds = bounds(node);
        if (bounds.isEmpty()) {
            return 0.0F;
        }
        Vector3f pivot = node.pivot();
        float dx = Math.max(
                Math.abs((float) bounds.minX() - pivot.x),
                Math.abs((float) bounds.maxX() - pivot.x)
        );
        float dy = Math.max(
                Math.abs((float) bounds.minY() - pivot.y),
                Math.abs((float) bounds.maxY() - pivot.y)
        );
        float dz = Math.max(
                Math.abs((float) bounds.minZ() - pivot.z),
                Math.abs((float) bounds.maxZ() - pivot.z)
        );
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Radius that bounds a cube for any rotation of its own bone. */
    static float sweptRadius(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.CubeBox cube,
            Vector3f scratch
    ) {
        float radius = 0.0F;
        for (int index = 0; index < 8; index++) {
            radius = Math.max(
                    radius,
                    node.pivot().distance(cube.corner(index, scratch))
            );
        }
        return radius;
    }

    private static PhysicsBoneGeometry.Bounds bounds(
            PhysicsBoneGeometry.Node node
    ) {
        return node.bounds().isEmpty() ? node.subtreeBounds() : node.bounds();
    }
}
