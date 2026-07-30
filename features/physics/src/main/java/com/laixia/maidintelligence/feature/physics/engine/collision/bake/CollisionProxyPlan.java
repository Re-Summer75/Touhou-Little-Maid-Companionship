package com.laixia.maidintelligence.feature.physics.engine.collision.bake;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProjector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolved construction-time collision recipe for one driven bone.
 */
public final class CollisionProxyPlan {
    enum Automatic {
        NONE,
        BODY,
        CAPE
    }

    record Explicit(
            PhysicsBoneSelectionPlan.CollisionProxySpec spec,
            CollisionReference reference
    ) {
    }

    /**
     * One rest-pose cube as an oriented box, in model space.
     *
     * @param endpointRadius endpoint radius in pixels, when the caller knows
     *                       the contact better than the driven bone's overall
     *                       proportions do
     * @param endpointClamp  collider thickness the derived endpoint radius is
     *                       held below, so a fat endpoint cannot swallow a
     *                       small cube
     * @param openAxis       local axis whose positive face is the only closed
     *                       one, or {@link CollisionProjector#CLOSED_BOX}
     */
    record MeshCollider(
            CollisionReference reference,
            Vector3f centerModel,
            Vector3f axisXModel,
            Vector3f axisYModel,
            Vector3f axisZModel,
            Vector3f halfExtents,
            Optional<Float> endpointRadius,
            float endpointClamp,
            int openAxis
    ) {
        /** A solid cube from the rigid mesh. */
        MeshCollider(
                CollisionReference reference,
                Vector3f centerModel,
                Vector3f axisXModel,
                Vector3f axisYModel,
                Vector3f axisZModel,
                Vector3f halfExtents
        ) {
            this(
                    reference,
                    centerModel,
                    axisXModel,
                    axisYModel,
                    axisZModel,
                    halfExtents,
                    Optional.empty(),
                    Math.min(
                            halfExtents.x,
                            Math.min(halfExtents.y, halfExtents.z)
                    ),
                    CollisionProjector.CLOSED_BOX
            );
        }
    }

    private final Automatic automatic;
    private final CollisionReference body;
    private final List<MeshCollider> mesh;
    private final List<MeshCollider> layers;
    private final List<Explicit> explicit;
    private final List<BoneModelSnapshot.Bone> referenceBones;

    CollisionProxyPlan(
            Automatic automatic,
            CollisionReference body,
            List<MeshCollider> mesh,
            List<MeshCollider> layers,
            List<Explicit> explicit
    ) {
        this.automatic = automatic;
        this.body = body;
        this.mesh = List.copyOf(mesh);
        this.layers = List.copyOf(layers);
        this.explicit = List.copyOf(explicit);
        this.referenceBones = collectReferences();
    }

    Automatic automatic() {
        return automatic;
    }

    CollisionReference body() {
        return body;
    }

    List<MeshCollider> mesh() {
        return mesh;
    }

    /** Cloth panels this bone must stay outside of, from other chains. */
    List<MeshCollider> layers() {
        return layers;
    }

    List<Explicit> explicit() {
        return explicit;
    }

    public List<BoneModelSnapshot.Bone> referenceBones() {
        return referenceBones;
    }

    private List<BoneModelSnapshot.Bone> collectReferences() {
        List<BoneModelSnapshot.Bone> output = new ArrayList<>();
        Set<BoneModelSnapshot.Bone> seen = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        add(body, output, seen);
        for (MeshCollider collider : mesh) {
            add(collider.reference(), output, seen);
        }
        for (MeshCollider collider : layers) {
            add(collider.reference(), output, seen);
        }
        for (Explicit entry : explicit) {
            add(entry.reference(), output, seen);
        }
        return List.copyOf(output);
    }

    private static void add(
            CollisionReference reference,
            List<BoneModelSnapshot.Bone> output,
            Set<BoneModelSnapshot.Bone> seen
    ) {
        if (reference != null
                && reference.bone() != null
                && seen.add(reference.bone())) {
            output.add(reference.bone());
        }
    }
}
