package com.laixia.maidintelligence.feature.physics.layout;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.CollisionProxyComposer;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.CollisionProxyPlan;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Bake-time builder behind the compact public solver-layout facade.
 */
final class PhysicsSolverLayoutBuilder {
    private PhysicsSolverLayoutBuilder() {
    }

    static PhysicsSolverLayout build(
            BoneModelSnapshot model,
            PhysicsBoneSelectionPlan plan
    ) {
        PhysicsLayoutSelection selection =
                PhysicsLayoutSelection.select(model, plan);
        PhysicsBoneGeometry.Analysis geometry = selection.geometry();
        List<BoneModelSnapshot.Bone> preorder = selection.preorder();
        List<BoneModelSnapshot.Bone> activeOrder = selection.activeOrder();
        IdentityHashMap<
                BoneModelSnapshot.Bone,
                BoneModelSnapshot.Bone
                > parents = selection.parents();
        List<PhysicsSolverLayout.Node> flattened =
                new ArrayList<>(activeOrder.size());
        IdentityHashMap<BoneModelSnapshot.Bone, Integer> flattenedIndices =
                new IdentityHashMap<>();
        int drivenCount = 0;
        for (BoneModelSnapshot.Bone bone : activeOrder) {
            PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
            int slot = decision.driven() ? drivenCount++ : -1;
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
            flattened.add(new PhysicsSolverLayout.Node(
                    bone,
                    parentIndex,
                    decision.driven()
                            ? PhysicsSolverLayout.Role.DRIVEN
                            : PhysicsSolverLayout.Role.ANCESTOR,
                    slot,
                    decision,
                    plan.path(bone),
                    kinematics
            ));
        }
        PhysicsSolverLayout.Node[] nodes =
                flattened.toArray(PhysicsSolverLayout.Node[]::new);
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
                        selection.collisionGeometry(),
                        indices,
                        restPoses
                );
        for (PhysicsSolverLayout.Node node : nodes) {
            if (!node.driven()) {
                continue;
            }
            PhysicsBoneSelectionPlan.SimulationSpace space =
                    selection.spaces().getOrDefault(
                            node.bone(),
                            PhysicsBoneSelectionPlan.SimulationSpace.MODEL
                    );
            BoneModelSnapshot.Bone referenceBone =
                    PhysicsLayoutPlanning.referenceBone(space, geometry);
            int referenceIndex = referenceBone == null
                    ? -1
                    : indices.getOrDefault(referenceBone, -1);
            CollisionProxyPlan collisionPlan =
                    selection.collisionPlans().get(node.bone());
            node.constraint(SecondaryConstraintFactory.create(
                    node,
                    space,
                    referenceIndex,
                    geometry,
                    restPoses,
                    collisionComposer,
                    collisionPlan
            ));
        }
        boolean referencesPreordered = referencesPreordered(nodes);
        SkirtBranchConstraintLayout skirtBranchConstraints =
                SkirtBranchConstraintPlanner.build(nodes, restPoses);
        return new PhysicsSolverLayout(
                nodes,
                preorder.size(),
                drivenCount,
                referencesPreordered,
                skirtBranchConstraints
        );
    }

    private static boolean referencesPreordered(
            PhysicsSolverLayout.Node[] nodes
    ) {
        for (int index = 0; index < nodes.length; index++) {
            if (nodes[index].driven()
                    && (nodes[index].constraint().referenceNodeIndex() >= index
                    || !nodes[index].constraint().collisionProxies()
                            .referencesBefore(index))) {
                return false;
            }
        }
        return true;
    }

    private static BoneModelSnapshot.Bone nearestSolidAncestor(
            BoneModelSnapshot.Bone bone,
            IdentityHashMap<
                    BoneModelSnapshot.Bone,
                    BoneModelSnapshot.Bone
                    > parents
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
}
