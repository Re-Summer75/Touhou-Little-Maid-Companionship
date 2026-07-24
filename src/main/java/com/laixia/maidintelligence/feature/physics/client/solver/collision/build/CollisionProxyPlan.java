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
        HEAD,
        BODY,
        SKIRT,
        CAPE
    }

    record Explicit(
            PhysicsBoneSelectionPlan.CollisionProxySpec spec,
            CollisionReference reference
    ) {
    }

    private final Automatic automatic;
    private final CollisionReference head;
    private final CollisionReference body;
    private final CollisionReference leftLeg;
    private final CollisionReference rightLeg;
    private final List<Explicit> explicit;
    private final List<AnimatedGeoBone> referenceBones;

    CollisionProxyPlan(
            Automatic automatic,
            CollisionReference head,
            CollisionReference body,
            CollisionReference leftLeg,
            CollisionReference rightLeg,
            List<Explicit> explicit
    ) {
        this.automatic = automatic;
        this.head = head;
        this.body = body;
        this.leftLeg = leftLeg;
        this.rightLeg = rightLeg;
        this.explicit = List.copyOf(explicit);
        this.referenceBones = collectReferences();
    }

    Automatic automatic() {
        return automatic;
    }

    CollisionReference head() {
        return head;
    }

    CollisionReference body() {
        return body;
    }

    CollisionReference leftLeg() {
        return leftLeg;
    }

    CollisionReference rightLeg() {
        return rightLeg;
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
        add(head, output, seen);
        add(body, output, seen);
        add(leftLeg, output, seen);
        add(rightLeg, output, seen);
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
