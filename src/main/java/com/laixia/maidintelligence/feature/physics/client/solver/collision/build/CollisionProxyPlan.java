package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
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

    private final Automatic automatic;
    private final CollisionReference body;
    private final List<Explicit> explicit;
    private final List<AnimatedGeoBone> referenceBones;

    CollisionProxyPlan(
            Automatic automatic,
            CollisionReference body,
            List<Explicit> explicit
    ) {
        this.automatic = automatic;
        this.body = body;
        this.explicit = List.copyOf(explicit);
        this.referenceBones = collectReferences();
    }

    Automatic automatic() {
        return automatic;
    }

    CollisionReference body() {
        return body;
    }

    List<Explicit> explicit() {
        return explicit;
    }

    public List<AnimatedGeoBone> referenceBones() {
        return referenceBones;
    }

    private List<AnimatedGeoBone> collectReferences() {
        List<AnimatedGeoBone> output = new ArrayList<>();
        Set<AnimatedGeoBone> seen = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        add(body, output, seen);
        for (Explicit entry : explicit) {
            add(entry.reference(), output, seen);
        }
        return List.copyOf(output);
    }

    private static void add(
            CollisionReference reference,
            List<AnimatedGeoBone> output,
            Set<AnimatedGeoBone> seen
    ) {
        if (reference != null
                && reference.bone() != null
                && seen.add(reference.bone())) {
            output.add(reference.bone());
        }
    }
}
