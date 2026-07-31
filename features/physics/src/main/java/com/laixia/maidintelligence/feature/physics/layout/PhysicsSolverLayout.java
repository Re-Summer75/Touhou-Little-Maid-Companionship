package com.laixia.maidintelligence.feature.physics.layout;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import org.joml.Vector3f;

/**
 * Flat pre-order representation of the minimum skeleton needed by physics.
 * It contains every driven bone and exactly the ancestors required to carry
 * the rendered orientation to those bones.
 */
public final class PhysicsSolverLayout {
    private final Node[] nodes;
    private final int fullBoneCount;
    private final int drivenBoneCount;
    private final boolean referencesPreordered;

    PhysicsSolverLayout(
            Node[] nodes,
            int fullBoneCount,
            int drivenBoneCount,
            boolean referencesPreordered
    ) {
        this.nodes = nodes;
        this.fullBoneCount = fullBoneCount;
        this.drivenBoneCount = drivenBoneCount;
        this.referencesPreordered = referencesPreordered;
    }

    public static PhysicsSolverLayout build(
            BoneModelSnapshot model,
            PhysicsBoneSelectionPlan plan
    ) {
        return PhysicsSolverLayoutBuilder.build(model, plan);
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

    public boolean referencesPreordered() {
        return referencesPreordered;
    }

    public Node node(int index) {
        return nodes[index];
    }

    public enum Role {
        ANCESTOR,
        DRIVEN
    }

    public static final class Node {
        private final BoneModelSnapshot.Bone bone;
        private final int parentIndex;
        private final Role role;
        private final int drivenSlot;
        private final PhysicsBoneSelectionPlan.Decision decision;
        private final String path;
        private final BoneKinematics.Metrics kinematics;
        private final Vector3f axis;
        private SecondaryMotionConstraint constraint;

        Node(
                BoneModelSnapshot.Bone bone,
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

        public BoneModelSnapshot.Bone bone() {
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

        public Vector3f axisInto(Vector3f output) {
            return output.set(axis);
        }

        void constraint(SecondaryMotionConstraint value) {
            constraint = value;
        }

        public SecondaryMotionConstraint constraint() {
            return constraint;
        }
    }
}
