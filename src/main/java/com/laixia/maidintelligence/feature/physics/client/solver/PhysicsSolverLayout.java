package com.laixia.maidintelligence.feature.physics.client.solver;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.build.BodyCollisionGeometry;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.build.BodyCollisionGeometryAnalyzer;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.build.CollisionProxyComposer;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.build.CollisionProxyPlan;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.build.CollisionProxyPlanner;
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
    private static final float MAX_ANGLE = 0.8F;
    private static final float MAX_TIP_DISPLACEMENT = 3.0F;
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
            AnimatedGeoModel model,
            PhysicsBoneSelectionPlan plan
    ) {
        PhysicsBoneGeometry.Analysis geometry =
                PhysicsBoneGeometry.analyze(model);
        List<AnimatedGeoBone> preorder = new ArrayList<>();
        IdentityHashMap<AnimatedGeoBone, AnimatedGeoBone> parents =
                new IdentityHashMap<>();
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            collect(bone, null, preorder, parents);
        }

        Set<AnimatedGeoBone> active = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        BodyCollisionGeometry collisionGeometry =
                BodyCollisionGeometryAnalyzer.analyze(geometry);
        CollisionProxyPlanner collisionPlanner =
                new CollisionProxyPlanner(
                        geometry,
                        collisionGeometry,
                        plan
                );
        IdentityHashMap<AnimatedGeoBone, CollisionProxyPlan> collisionPlans =
                new IdentityHashMap<>();
        IdentityHashMap<
                AnimatedGeoBone,
                PhysicsBoneSelectionPlan.SimulationSpace
                > spaces = new IdentityHashMap<>();
        for (AnimatedGeoBone bone : preorder) {
            if (!plan.isDriven(bone)) {
                continue;
            }
            addPath(bone, active, parents);
            PhysicsBoneSelectionPlan.SimulationSpace space =
                    resolveSimulationSpace(
                            plan.decision(bone),
                            geometry.node(bone),
                            geometry
                    );
            spaces.put(bone, space);
            AnimatedGeoBone reference = referenceBone(space, geometry);
            if (reference != null) {
                addPath(reference, active, parents);
            }
            CollisionProxyPlan collisionPlan = collisionPlanner.plan(
                    bone,
                    plan.decision(bone),
                    space
            );
            collisionPlans.put(bone, collisionPlan);
            for (AnimatedGeoBone collisionReference
                    : collisionPlan.referenceBones()) {
                addPath(collisionReference, active, parents);
            }
        }

        List<AnimatedGeoBone> activeOrder = orderActive(
                preorder,
                active,
                parents,
                spaces,
                collisionPlans,
                geometry
        );
        List<Node> flattened = new ArrayList<>(activeOrder.size());
        IdentityHashMap<AnimatedGeoBone, Integer> flattenedIndices =
                new IdentityHashMap<>();
        int[] drivenSlot = {0};
        for (AnimatedGeoBone bone : activeOrder) {
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
                        decision.structureRole()
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
        IdentityHashMap<AnimatedGeoBone, Integer> indices =
                new IdentityHashMap<>();
        for (int index = 0; index < nodes.length; index++) {
            indices.put(nodes[index].bone(), index);
        }
        IdentityHashMap<AnimatedGeoBone, BoneRestPose> restPoses =
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
            AnimatedGeoBone referenceBone = referenceBone(space, geometry);
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

    private static void addPath(
            AnimatedGeoBone bone,
            Set<AnimatedGeoBone> active,
            Map<AnimatedGeoBone, AnimatedGeoBone> parents
    ) {
        AnimatedGeoBone cursor = bone;
        while (cursor != null && active.add(cursor)) {
            cursor = parents.get(cursor);
        }
    }

    private static PhysicsBoneSelectionPlan.SimulationSpace
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

    private static AnimatedGeoBone referenceBone(
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
            IdentityHashMap<AnimatedGeoBone, BoneRestPose> restPoses,
            CollisionProxyComposer collisionComposer,
            CollisionProxyPlan collisionPlan
    ) {
        PhysicsBoneSelectionPlan.ConstraintProfile profile =
                node.decision().constraints();
        PhysicsBoneSelectionPlan.SwingLimits authored =
                profile.swingLimits();
        float cap = angularCap(node);
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

        return new SecondaryMotionConstraint(
                profile.enabled(),
                space,
                referenceIndex,
                profile.rotationInertiaScale(),
                rightLocal,
                outwardLocal,
                limits,
                collisionProxies
        );
    }

    private static float angularCap(Node node) {
        PhysicsBoneSelectionPlan.SpringProfile profile =
                node.decision().profile();
        float geometric = Math.min(
                MAX_ANGLE,
                node.kinematics().safeAngle()
        ) * profile.angleScale();
        float displacement = MAX_TIP_DISPLACEMENT
                * profile.tipDisplacementScale()
                / node.kinematics().leverArm();
        return Math.max(0.0F, Math.min(geometric, displacement));
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

    private static AnimatedGeoBone nearestSolidAncestor(
            AnimatedGeoBone bone,
            Map<AnimatedGeoBone, AnimatedGeoBone> parents
    ) {
        AnimatedGeoBone cursor = parents.get(bone);
        while (cursor != null) {
            if (cursor.geoBone().cubes().getCubeCount() > 0) {
                return cursor;
            }
            cursor = parents.get(cursor);
        }
        return null;
    }

    private static List<AnimatedGeoBone> orderActive(
            List<AnimatedGeoBone> preorder,
            Set<AnimatedGeoBone> active,
            Map<AnimatedGeoBone, AnimatedGeoBone> parents,
            Map<AnimatedGeoBone, PhysicsBoneSelectionPlan.SimulationSpace> spaces,
            Map<AnimatedGeoBone, CollisionProxyPlan> collisionPlans,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        List<AnimatedGeoBone> output = new ArrayList<>(active.size());
        Set<AnimatedGeoBone> emitted = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        while (output.size() < active.size()) {
            boolean progressed = false;
            for (AnimatedGeoBone bone : preorder) {
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
            AnimatedGeoBone bone,
            Set<AnimatedGeoBone> active,
            Set<AnimatedGeoBone> emitted,
            Map<AnimatedGeoBone, AnimatedGeoBone> parents,
            Map<AnimatedGeoBone, PhysicsBoneSelectionPlan.SimulationSpace> spaces,
            Map<AnimatedGeoBone, CollisionProxyPlan> collisionPlans,
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
            for (AnimatedGeoBone reference
                    : collisionPlan.referenceBones()) {
                if (!ready(reference, bone, active, emitted)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean ready(
            AnimatedGeoBone dependency,
            AnimatedGeoBone bone,
            Set<AnimatedGeoBone> active,
            Set<AnimatedGeoBone> emitted
    ) {
        return dependency == null
                || dependency == bone
                || !active.contains(dependency)
                || emitted.contains(dependency);
    }

    private static void appendRemaining(
            List<AnimatedGeoBone> preorder,
            Set<AnimatedGeoBone> active,
            Set<AnimatedGeoBone> emitted,
            List<AnimatedGeoBone> output
    ) {
        for (AnimatedGeoBone bone : preorder) {
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
        private final AnimatedGeoBone bone;
        private final int parentIndex;
        private final Role role;
        private final int drivenSlot;
        private final PhysicsBoneSelectionPlan.Decision decision;
        private final String path;
        private final BoneKinematics.Metrics kinematics;
        private final Vector3f axis;
        private SecondaryMotionConstraint constraint;

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

        public Vector3f axisInto(Vector3f output) {
            return output.set(axis);
        }

        public SecondaryMotionConstraint constraint() {
            return constraint;
        }
    }
}
