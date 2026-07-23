package com.laixia.maidintelligence.feature.physics.client.solver;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
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
    private static final float PIXELS_PER_BLOCK = 16.0F;
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
            PhysicsBoneSelectionPlan.ConstraintProfile constraints =
                    plan.decision(bone).constraints();
            if (constraints.enabled()
                    && (constraints.backstop()
                    || constraints.headCollision())
                    && geometry.head() != null) {
                addPath(geometry.head().bone(), active, parents);
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
        Node[] nodes = flattened.toArray(Node[]::new);
        IdentityHashMap<AnimatedGeoBone, Integer> indices =
                new IdentityHashMap<>();
        for (int index = 0; index < nodes.length; index++) {
            indices.put(nodes[index].bone(), index);
        }
        IdentityHashMap<AnimatedGeoBone, BoneRestPose> restPoses =
                BoneRestPose.collect(model);
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
            PhysicsBoneSelectionPlan.ConstraintProfile constraints =
                    node.decision().constraints();
            AnimatedGeoBone collisionReferenceBone =
                    constraints.enabled()
                            && (constraints.backstop()
                            || constraints.headCollision())
                            && geometry.head() != null
                            ? geometry.head().bone()
                            : null;
            int collisionReferenceIndex =
                    collisionReferenceBone == null
                            ? -1
                            : indices.getOrDefault(
                                    collisionReferenceBone,
                                    -1
                            );
            boolean drivenParent = node.parentIndex() >= 0
                    && nodes[node.parentIndex()].driven();
            node.constraint = createConstraint(
                    node,
                    space,
                    referenceIndex,
                    collisionReferenceIndex,
                    collisionReferenceBone,
                    drivenParent,
                    geometry,
                    restPoses
            );
        }
        boolean referencesPreordered = true;
        for (int index = 0; index < nodes.length; index++) {
            if (nodes[index].driven()
                    && (nodes[index].constraint().referenceNodeIndex()
                    >= index
                    || nodes[index].constraint()
                    .collisionReferenceNodeIndex() >= index)) {
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
            int collisionReferenceIndex,
            AnimatedGeoBone collisionReferenceBone,
            boolean drivenParent,
            PhysicsBoneGeometry.Analysis geometry,
            IdentityHashMap<AnimatedGeoBone, BoneRestPose> restPoses
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

        boolean hasHeadCollider = collisionReferenceBone != null
                && !geometry.headBounds().isEmpty();
        BoneRestPose collisionReferencePose = hasHeadCollider
                ? restPoses.get(collisionReferenceBone)
                : null;
        Quaternionf collisionReferenceRest =
                collisionReferencePose == null
                        ? null
                        : collisionReferencePose.orientation();
        if (collisionReferenceRest == null) {
            collisionReferenceRest = new Quaternionf();
        }
        Quaternionf collisionReferenceInverse =
                new Quaternionf(collisionReferenceRest).conjugate();
        Vector3f headCenter = geometry.headBounds().center();
        Vector3f pivotOffsetModel = new Vector3f(pivot).sub(headCenter);
        Vector3f collisionOutward = new Vector3f(pivotOffsetModel);
        if (collisionOutward.lengthSquared() < EPSILON
                && geometryNode != null) {
            collisionOutward.set(geometryNode.center()).sub(headCenter);
        }
        if (collisionOutward.lengthSquared() < EPSILON) {
            collisionOutward.set(0.0F, 0.0F, -1.0F);
        } else {
            collisionOutward.normalize();
        }
        Vector3f pivotFromReference =
                collisionReferenceInverse.transform(
                pivotOffsetModel,
                new Vector3f()
        );
        Vector3f normalFromReference =
                collisionReferenceInverse.transform(
                collisionOutward,
                new Vector3f()
        ).normalize();

        Vector3f headSize = geometry.headBounds().size();
        float headRadius = Math.max(
                headSize.x,
                Math.max(headSize.y, headSize.z)
        ) * 0.5F;
        float hitRadius = deriveHitRadius(geometryNode, headRadius)
                * profile.hitRadiusScale();
        Vector3f planePointModel =
                new Vector3f(collisionOutward).mul(headRadius);
        Vector3f planePointFromReference =
                collisionReferenceInverse.transform(
                planePointModel,
                new Vector3f()
        );
        float rawLeverArm =
                node.kinematics().leverArm() / PIXELS_PER_BLOCK;
        Vector3f rawTip = node.kinematics().effectivePivot()
                .fma(rawLeverArm, node.axis());
        Vector3f restTipModel = boneRestPose == null
                ? boneRest.transform(
                        node.axis(),
                        new Vector3f()
                ).normalize().mul(rawLeverArm).add(pivot)
                : boneRestPose.transformPosition(rawTip, new Vector3f());
        float leverArm = Math.max(
                pivot.distance(restTipModel),
                1.0F / PIXELS_PER_BLOCK
        );
        Vector3f restTip = restTipModel.sub(headCenter);
        float planeDistance =
                (restTip.x - planePointModel.x) * collisionOutward.x
                        + (restTip.y - planePointModel.y)
                        * collisionOutward.y
                        + (restTip.z - planePointModel.z)
                        * collisionOutward.z;
        float minimumRadius = headRadius + hitRadius;
        boolean legalBackstop = planeDistance + 0.02F >= hitRadius;
        boolean legalSphere = restTip.length() + 0.02F >= minimumRadius;
        boolean collisionType =
                node.decision().type()
                        != PhysicsBoneSelectionPlan.PartType.HEAD_SHELL;
        boolean collisionEligible = profile.enabled()
                && hasHeadCollider
                && !drivenParent
                && collisionType;

        return new SecondaryMotionConstraint(
                profile.enabled(),
                space,
                referenceIndex,
                collisionReferenceIndex,
                profile.rotationInertiaScale(),
                rightLocal,
                outwardLocal,
                limits,
                collisionEligible && profile.backstop()
                        && legalBackstop,
                collisionEligible && profile.headCollision()
                        && legalSphere,
                pivotFromReference,
                planePointFromReference,
                normalFromReference,
                headRadius,
                hitRadius,
                leverArm
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

    private static float deriveHitRadius(
            PhysicsBoneGeometry.Node node,
            float headRadius
    ) {
        if (node == null || !node.hasGeometry()) {
            return 0.0F;
        }
        Vector3f size = node.size();
        float smallest = smallestPositive(size.x, size.y, size.z);
        if (smallest <= EPSILON) {
            return 0.0F;
        }
        return Math.min(smallest * 0.5F, headRadius * 0.15F);
    }

    private static float smallestPositive(float x, float y, float z) {
        float result = Float.POSITIVE_INFINITY;
        if (x > EPSILON) {
            result = x;
        }
        if (y > EPSILON) {
            result = Math.min(result, y);
        }
        if (z > EPSILON) {
            result = Math.min(result, z);
        }
        return Float.isFinite(result) ? result : 0.0F;
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
