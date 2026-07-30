package com.laixia.maidintelligence.feature.physics.layout;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.BodyCollisionGeometry;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.BodyCollisionGeometryAnalyzer;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.CollisionProxyComposer;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.CollisionProxyPlan;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.CollisionProxyPlanner;
import org.joml.Quaternionf;
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
    private static final float EPSILON = 1.0E-6F;

    private final Node[] nodes;
    private final int fullBoneCount;
    private final int drivenBoneCount;
    private final boolean referencesPreordered;

    private PhysicsSolverLayout(
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
        PhysicsLayoutSelection selection =
                PhysicsLayoutSelection.select(model, plan);
        PhysicsBoneGeometry.Analysis geometry = selection.geometry();
        BodyCollisionGeometry collisionGeometry =
                selection.collisionGeometry();
        List<BoneModelSnapshot.Bone> preorder = selection.preorder();
        List<BoneModelSnapshot.Bone> activeOrder =
                selection.activeOrder();
        IdentityHashMap<
                BoneModelSnapshot.Bone,
                BoneModelSnapshot.Bone
                > parents = selection.parents();
        IdentityHashMap<
                BoneModelSnapshot.Bone,
                PhysicsBoneSelectionPlan.SimulationSpace
                > spaces = selection.spaces();
        IdentityHashMap<
                BoneModelSnapshot.Bone,
                CollisionProxyPlan
                > collisionPlans = selection.collisionPlans();
        List<Node> flattened = new ArrayList<>(activeOrder.size());
        IdentityHashMap<BoneModelSnapshot.Bone, Integer> flattenedIndices =
                new IdentityHashMap<>();
        int[] drivenSlot = {0};
        for (BoneModelSnapshot.Bone bone : activeOrder) {
            PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
            int slot = decision.driven() ? drivenSlot[0]++ : -1;
            BoneKinematics.Metrics kinematics = decision.driven()
                    ? plan.kinematics(bone)
                    : null;
            if (decision.driven() && kinematics == null) {
                kinematics = BoneKinematics.measure(
                        bone,
                        parents.get(bone),
                        nearestSolidAncestor(bone, parents),
                        decision.type(),
                        decision.structureRole(),
                        decision.chainSegment(),
                        nextChainBone(bone, decision, plan)
                );
            }
            int parentIndex = flattenedIndices.getOrDefault(
                    parents.get(bone),
                    -1
            );
            flattenedIndices.put(bone, flattened.size());
            flattened.add(new Node(
                    bone,
                    parentIndex,
                    decision.driven() ? Role.DRIVEN : Role.ANCESTOR,
                    slot,
                    decision,
                    plan.path(bone),
                    kinematics
            ));
        }
        Node[] nodes = flattened.toArray(Node[]::new);
        IdentityHashMap<BoneModelSnapshot.Bone, Integer> indices =
                new IdentityHashMap<>();
        for (int index = 0; index < nodes.length; index++) {
            indices.put(nodes[index].bone(), index);
        }
        IdentityHashMap<BoneModelSnapshot.Bone, BoneRestPose> restPoses =
                BoneRestPose.collect(model);
        CollisionProxyComposer collisionComposer =
                new CollisionProxyComposer(
                        geometry,
                        collisionGeometry,
                        indices,
                        restPoses
                );
        for (Node node : nodes) {
            if (!node.driven()) {
                continue;
            }
            PhysicsBoneSelectionPlan.SimulationSpace space =
                    spaces.getOrDefault(
                            node.bone(),
                            PhysicsBoneSelectionPlan.SimulationSpace.MODEL
                    );
            BoneModelSnapshot.Bone referenceBone = referenceBone(space, geometry);
            int referenceIndex = referenceBone == null
                    ? -1
                    : indices.getOrDefault(referenceBone, -1);
            node.constraint = createConstraint(
                    node,
                    space,
                    referenceIndex,
                    geometry,
                    restPoses,
                    collisionComposer,
                    collisionPlans.get(node.bone())
            );
        }
        boolean referencesPreordered = true;
        for (int index = 0; index < nodes.length; index++) {
            if (nodes[index].driven()
                    && (nodes[index].constraint().referenceNodeIndex()
                    >= index
                    || !nodes[index].constraint().collisionProxies()
                            .referencesBefore(index))) {
                referencesPreordered = false;
                break;
            }
        }
        return new PhysicsSolverLayout(
                nodes,
                preorder.size(),
                drivenSlot[0],
                referencesPreordered
        );
    }

    static void addPath(
            BoneModelSnapshot.Bone bone,
            Set<BoneModelSnapshot.Bone> active,
            Map<BoneModelSnapshot.Bone, BoneModelSnapshot.Bone> parents
    ) {
        BoneModelSnapshot.Bone cursor = bone;
        while (cursor != null && active.add(cursor)) {
            cursor = parents.get(cursor);
        }
    }

    static PhysicsBoneSelectionPlan.SimulationSpace
    resolveSimulationSpace(
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        PhysicsBoneSelectionPlan.SimulationSpace requested =
                decision.constraints().simulationSpace();
        if (requested != PhysicsBoneSelectionPlan.SimulationSpace.AUTO) {
            return hasReference(requested, geometry)
                    ? requested
                    : PhysicsBoneSelectionPlan.SimulationSpace.MODEL;
        }
        if (node != null && geometry.head() != null
                && node.isDescendantOf(geometry.head())) {
            return PhysicsBoneSelectionPlan.SimulationSpace.HEAD_LOCAL;
        }
        if (geometry.head() != null
                && (decision.type()
                == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                || decision.type()
                == PhysicsBoneSelectionPlan.PartType.HAIR
                || decision.type()
                == PhysicsBoneSelectionPlan.PartType.EAR)) {
            return PhysicsBoneSelectionPlan.SimulationSpace.HEAD_LOCAL;
        }
        return switch (decision.type()) {
            case SKIRT, CAPE, RIBBON, WING ->
                    geometry.body() == null
                            ? PhysicsBoneSelectionPlan.SimulationSpace.MODEL
                            : PhysicsBoneSelectionPlan.SimulationSpace.BODY_LOCAL;
            default -> PhysicsBoneSelectionPlan.SimulationSpace.MODEL;
        };
    }

    private static boolean hasReference(
            PhysicsBoneSelectionPlan.SimulationSpace space,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        return switch (space) {
            case HEAD_LOCAL -> geometry.head() != null;
            case BODY_LOCAL -> geometry.body() != null;
            case MODEL, AUTO -> true;
        };
    }

    static BoneModelSnapshot.Bone referenceBone(
            PhysicsBoneSelectionPlan.SimulationSpace space,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        return switch (space) {
            case HEAD_LOCAL -> geometry.head() == null
                    ? null
                    : geometry.head().bone();
            case BODY_LOCAL -> geometry.body() == null
                    ? null
                    : geometry.body().bone();
            case MODEL, AUTO -> null;
        };
    }

    private static SecondaryMotionConstraint createConstraint(
            Node node,
            PhysicsBoneSelectionPlan.SimulationSpace space,
            int referenceIndex,
            PhysicsBoneGeometry.Analysis geometry,
            IdentityHashMap<BoneModelSnapshot.Bone, BoneRestPose> restPoses,
            CollisionProxyComposer collisionComposer,
            CollisionProxyPlan collisionPlan
    ) {
        PhysicsBoneSelectionPlan.ConstraintProfile profile =
                node.decision().constraints();
        PhysicsBoneSelectionPlan.SwingLimits authored =
                profile.swingLimits();
        float cap = SwingRange.maximum(node);
        PhysicsBoneSelectionPlan.SwingLimits limits =
                new PhysicsBoneSelectionPlan.SwingLimits(
                        Math.min(authored.left(), cap),
                        Math.min(authored.right(), cap),
                        Math.min(authored.outward(), cap),
                        Math.min(authored.inward(), cap)
                );

        BoneRestPose boneRestPose = restPoses.get(node.bone());
        Quaternionf boneRest = boneRestPose == null
                ? new Quaternionf()
                : boneRestPose.orientation();
        PhysicsBoneGeometry.Node geometryNode =
                geometry.node(node.bone());
        PhysicsBoneGeometry.Bounds referenceBounds = switch (space) {
            case HEAD_LOCAL -> geometry.headBounds();
            case BODY_LOCAL -> geometry.bodyBounds();
            case MODEL, AUTO -> geometry.modelBounds();
        };
        Vector3f referenceCenter = referenceBounds.center();
        Vector3f pivot = boneRestPose == null
                ? geometryNode == null
                        ? new Vector3f()
                        : new Vector3f(geometryNode.pivot())
                : boneRestPose.transformPosition(
                        node.kinematics().effectivePivot(),
                        new Vector3f()
                );
        Vector3f outwardModel =
                new Vector3f(pivot).sub(referenceCenter);
        if (outwardModel.lengthSquared() < EPSILON && geometryNode != null) {
            outwardModel.set(geometryNode.center()).sub(referenceCenter);
        }
        if (outwardModel.lengthSquared() < EPSILON) {
            outwardModel.set(0.0F, 0.0F, -1.0F);
        } else {
            outwardModel.normalize();
        }

        Vector3f outwardLocal = boneRest.transformInverse(
                outwardModel,
                new Vector3f()
        );
        orthogonalize(outwardLocal, node.axis());
        Vector3f rightLocal = outwardLocal.cross(
                node.axis(),
                new Vector3f()
        );
        if (rightLocal.lengthSquared() < EPSILON) {
            rightLocal.set(1.0F, 0.0F, 0.0F);
            orthogonalize(rightLocal, node.axis());
        } else {
            rightLocal.normalize();
        }
        node.axis().cross(rightLocal, outwardLocal).normalize();

        CollisionProxySet collisionProxies =
                collisionComposer.compose(
                        collisionPlan,
                        node,
                        boneRestPose,
                        pivot
                );

        /*
         * The sheet's own thickness, expressed as a box in the bone frame so a
         * contact can ask for its reach along one normal. Only the thin
         * direction is filled in: the wide ones are the panel's width and length,
         * which never stand between the axis and a surface, and padding by them
         * is what made every scalar attempt worse.
         */
        Vector3f meshHalf = MeshSheetExtent.halfExtents(
                geometryNode,
                node.axis(),
                rightLocal,
                outwardLocal
        );

        return new SecondaryMotionConstraint(
                profile.enabled(),
                space,
                referenceIndex,
                profile.rotationInertiaScale(),
                rightLocal,
                outwardLocal,
                limits,
                collisionProxies,
                meshHalf
        );
    }

    private static void orthogonalize(Vector3f vector, Vector3f axis) {
        float projection = vector.dot(axis);
        vector.add(
                -axis.x * projection,
                -axis.y * projection,
                -axis.z * projection
        );
        if (vector.lengthSquared() < EPSILON) {
            if (Math.abs(axis.y) < 0.90F) {
                vector.set(0.0F, 1.0F, 0.0F).cross(axis);
            } else {
                vector.set(1.0F, 0.0F, 0.0F).cross(axis);
            }
        }
        vector.normalize();
    }

    static void collect(
            BoneModelSnapshot.Bone bone,
            BoneModelSnapshot.Bone parent,
            List<BoneModelSnapshot.Bone> preorder,
            Map<BoneModelSnapshot.Bone, BoneModelSnapshot.Bone> parents
    ) {
        preorder.add(bone);
        if (parent != null) {
            parents.put(bone, parent);
        }
        for (BoneModelSnapshot.Bone child : bone.children()) {
            collect(child, bone, preorder, parents);
        }
    }

    private static BoneModelSnapshot.Bone nearestSolidAncestor(
            BoneModelSnapshot.Bone bone,
            Map<BoneModelSnapshot.Bone, BoneModelSnapshot.Bone> parents
    ) {
        BoneModelSnapshot.Bone cursor = parents.get(bone);
        while (cursor != null) {
            if (cursor.geometry().cubes().getCubeCount() > 0) {
                return cursor;
            }
            cursor = parents.get(cursor);
        }
        return null;
    }

    private static BoneModelSnapshot.Bone nextChainBone(
            BoneModelSnapshot.Bone bone,
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan plan
    ) {
        if (!decision.chainSegment().present()
                || decision.chainSegment().index()
                >= decision.chainSegment().count() - 1) {
            return null;
        }
        for (BoneModelSnapshot.Bone child : bone.children()) {
            PhysicsBoneSelectionPlan.Decision childDecision =
                    plan.decision(child);
            if (childDecision.driven()
                    && childDecision.chainId().equals(decision.chainId())
                    && childDecision.chainSegment().index()
                    == decision.chainSegment().index() + 1) {
                return child;
            }
            if (child.geometry().cubes().getCubeCount() == 0) {
                BoneModelSnapshot.Bone nested =
                        nextChainBone(child, decision, plan);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    static List<BoneModelSnapshot.Bone> orderActive(
            List<BoneModelSnapshot.Bone> preorder,
            Set<BoneModelSnapshot.Bone> active,
            Map<BoneModelSnapshot.Bone, BoneModelSnapshot.Bone> parents,
            Map<BoneModelSnapshot.Bone, PhysicsBoneSelectionPlan.SimulationSpace> spaces,
            Map<BoneModelSnapshot.Bone, CollisionProxyPlan> collisionPlans,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        List<BoneModelSnapshot.Bone> output = new ArrayList<>(active.size());
        Set<BoneModelSnapshot.Bone> emitted = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        while (output.size() < active.size()) {
            boolean progressed = false;
            for (BoneModelSnapshot.Bone bone : preorder) {
                if (!active.contains(bone)
                        || emitted.contains(bone)
                        || !dependenciesReady(
                        bone,
                        active,
                        emitted,
                        parents,
                        spaces,
                        collisionPlans,
                        geometry
                )) {
                    continue;
                }
                emitted.add(bone);
                output.add(bone);
                progressed = true;
            }
            if (!progressed) {
                appendRemaining(preorder, active, emitted, output);
            }
        }
        return output;
    }

    private static boolean dependenciesReady(
            BoneModelSnapshot.Bone bone,
            Set<BoneModelSnapshot.Bone> active,
            Set<BoneModelSnapshot.Bone> emitted,
            Map<BoneModelSnapshot.Bone, BoneModelSnapshot.Bone> parents,
            Map<BoneModelSnapshot.Bone, PhysicsBoneSelectionPlan.SimulationSpace> spaces,
            Map<BoneModelSnapshot.Bone, CollisionProxyPlan> collisionPlans,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        if (!ready(parents.get(bone), bone, active, emitted)) {
            return false;
        }
        PhysicsBoneSelectionPlan.SimulationSpace space = spaces.get(bone);
        if (space != null
                && !ready(
                referenceBone(space, geometry),
                bone,
                active,
                emitted
        )) {
            return false;
        }
        CollisionProxyPlan collisionPlan = collisionPlans.get(bone);
        if (collisionPlan != null) {
            for (BoneModelSnapshot.Bone reference
                    : collisionPlan.referenceBones()) {
                if (!ready(reference, bone, active, emitted)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean ready(
            BoneModelSnapshot.Bone dependency,
            BoneModelSnapshot.Bone bone,
            Set<BoneModelSnapshot.Bone> active,
            Set<BoneModelSnapshot.Bone> emitted
    ) {
        return dependency == null
                || dependency == bone
                || !active.contains(dependency)
                || emitted.contains(dependency);
    }

    private static void appendRemaining(
            List<BoneModelSnapshot.Bone> preorder,
            Set<BoneModelSnapshot.Bone> active,
            Set<BoneModelSnapshot.Bone> emitted,
            List<BoneModelSnapshot.Bone> output
    ) {
        for (BoneModelSnapshot.Bone bone : preorder) {
            if (active.contains(bone) && emitted.add(bone)) {
                output.add(bone);
            }
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

        private Node(
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

        public SecondaryMotionConstraint constraint() {
            return constraint;
        }
    }
}
