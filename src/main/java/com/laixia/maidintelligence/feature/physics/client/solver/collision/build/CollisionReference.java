package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * One immutable rest-pose frame used while baking collision proxies.
 */
final class CollisionReference {
    private static final CollisionReference MODEL =
            new CollisionReference(null, new Vector3f());

    private final AnimatedGeoBone bone;
    private final Vector3f originModel;

    private CollisionReference(
            AnimatedGeoBone bone,
            Vector3f originModel
    ) {
        this.bone = bone;
        this.originModel = new Vector3f(originModel);
    }

    static CollisionReference model() {
        return MODEL;
    }

    static CollisionReference of(PhysicsBoneGeometry.Node node) {
        Objects.requireNonNull(node, "node");
        Vector3f origin = node.bounds().isEmpty()
                ? new Vector3f(node.pivot())
                : node.bounds().center();
        return new CollisionReference(node.bone(), origin);
    }

    static CollisionReference of(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Bounds bounds
    ) {
        Objects.requireNonNull(node, "node");
        Vector3f origin = bounds == null || bounds.isEmpty()
                ? new Vector3f(node.pivot())
                : bounds.center();
        return new CollisionReference(node.bone(), origin);
    }

    AnimatedGeoBone bone() {
        return bone;
    }

    Vector3f copyOrigin(Vector3f output) {
        return output.set(originModel);
    }
}
