package com.laixia.maidintelligence.feature.physics.client.solver;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flat pre-order representation of the minimum skeleton needed by physics.
 * It contains every driven bone and exactly the ancestors required to carry
 * the rendered orientation to those bones.
 */
public final class PhysicsSolverLayout {
    private final Node[] nodes;
    private final int fullBoneCount;
    private final int drivenBoneCount;

    private PhysicsSolverLayout(
            Node[] nodes,
            int fullBoneCount,
            int drivenBoneCount
    ) {
        this.nodes = nodes;
        this.fullBoneCount = fullBoneCount;
        this.drivenBoneCount = drivenBoneCount;
    }

    public static PhysicsSolverLayout build(
            AnimatedGeoModel model,
            PhysicsBoneSelectionPlan plan
    ) {
        List<AnimatedGeoBone> preorder = new ArrayList<>();
        IdentityHashMap<AnimatedGeoBone, AnimatedGeoBone> parents =
                new IdentityHashMap<>();
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            collect(bone, null, preorder, parents);
        }

        Set<AnimatedGeoBone> active = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        for (AnimatedGeoBone bone : preorder) {
            if (!plan.isDriven(bone)) {
                continue;
            }
            AnimatedGeoBone cursor = bone;
            while (cursor != null && active.add(cursor)) {
                cursor = parents.get(cursor);
            }
        }

        List<Node> flattened = new ArrayList<>(active.size());
        int[] drivenSlot = {0};
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            appendActive(
                    bone,
                    -1,
                    active,
                    plan,
                    parents,
                    flattened,
                    drivenSlot
            );
        }
        return new PhysicsSolverLayout(
                flattened.toArray(Node[]::new),
                preorder.size(),
                drivenSlot[0]
        );
    }

    private static void collect(
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            List<AnimatedGeoBone> preorder,
            Map<AnimatedGeoBone, AnimatedGeoBone> parents
    ) {
        preorder.add(bone);
        if (parent != null) {
            parents.put(bone, parent);
        }
        for (AnimatedGeoBone child : bone.children()) {
            collect(child, bone, preorder, parents);
        }
    }

    private static void appendActive(
            AnimatedGeoBone bone,
            int parentIndex,
            Set<AnimatedGeoBone> active,
            PhysicsBoneSelectionPlan plan,
            Map<AnimatedGeoBone, AnimatedGeoBone> parents,
            List<Node> output,
            int[] nextDrivenSlot
    ) {
        if (!active.contains(bone)) {
            return;
        }
        PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
        int slot = decision.driven() ? nextDrivenSlot[0]++ : -1;
        BoneKinematics.Metrics kinematics = null;
        if (decision.driven()) {
            kinematics = plan.kinematics(bone);
            if (kinematics == null) {
                kinematics = BoneKinematics.measure(
                        bone,
                        parents.get(bone),
                        decision.type()
                );
            }
        }
        int index = output.size();
        output.add(new Node(
                bone,
                parentIndex,
                decision.driven() ? Role.DRIVEN : Role.ANCESTOR,
                slot,
                decision,
                plan.path(bone),
                kinematics
        ));
        for (AnimatedGeoBone child : bone.children()) {
            appendActive(
                    child,
                    index,
                    active,
                    plan,
                    parents,
                    output,
                    nextDrivenSlot
            );
        }
    }

    public int activeNodeCount() {
        return nodes.length;
    }

    public int fullBoneCount() {
        return fullBoneCount;
    }

    public int drivenBoneCount() {
        return drivenBoneCount;
    }

    public Node node(int index) {
        return nodes[index];
    }

    public enum Role {
        ANCESTOR,
        DRIVEN
    }

    public static final class Node {
        private final AnimatedGeoBone bone;
        private final int parentIndex;
        private final Role role;
        private final int drivenSlot;
        private final PhysicsBoneSelectionPlan.Decision decision;
        private final String path;
        private final BoneKinematics.Metrics kinematics;
        private final Vector3f axis;

        private Node(
                AnimatedGeoBone bone,
                int parentIndex,
                Role role,
                int drivenSlot,
                PhysicsBoneSelectionPlan.Decision decision,
                String path,
                BoneKinematics.Metrics kinematics
        ) {
            this.bone = bone;
            this.parentIndex = parentIndex;
            this.role = role;
            this.drivenSlot = drivenSlot;
            this.decision = decision;
            this.path = path;
            this.kinematics = kinematics;
            this.axis = kinematics == null
                    ? null
                    : kinematics.axisInto(new Vector3f());
        }

        public AnimatedGeoBone bone() {
            return bone;
        }

        public int parentIndex() {
            return parentIndex;
        }

        public Role role() {
            return role;
        }

        public boolean driven() {
            return role == Role.DRIVEN;
        }

        public int drivenSlot() {
            return drivenSlot;
        }

        public PhysicsBoneSelectionPlan.Decision decision() {
            return decision;
        }

        public String path() {
            return path;
        }

        public BoneKinematics.Metrics kinematics() {
            return kinematics;
        }

        Vector3f axis() {
            return axis;
        }
    }
}
