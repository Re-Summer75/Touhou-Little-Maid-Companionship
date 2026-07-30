package com.laixia.maidintelligence.feature.physics.layout;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.BodyCollisionGeometry;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.BodyCollisionGeometryAnalyzer;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.CollisionProxyPlan;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.CollisionProxyPlanner;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Owns active-bone, simulation-space and collision-reference selection before
 * the immutable runtime layout is flattened.
 */
record PhysicsLayoutSelection(
        PhysicsBoneGeometry.Analysis geometry,
        BodyCollisionGeometry collisionGeometry,
        List<BoneModelSnapshot.Bone> preorder,
        List<BoneModelSnapshot.Bone> activeOrder,
        IdentityHashMap<
                BoneModelSnapshot.Bone,
                BoneModelSnapshot.Bone
                > parents,
        IdentityHashMap<
                BoneModelSnapshot.Bone,
                PhysicsBoneSelectionPlan.SimulationSpace
                > spaces,
        IdentityHashMap<
                BoneModelSnapshot.Bone,
                CollisionProxyPlan
                > collisionPlans
) {
    static PhysicsLayoutSelection select(
            BoneModelSnapshot model,
            PhysicsBoneSelectionPlan plan
    ) {
        PhysicsBoneGeometry.Analysis geometry =
                PhysicsBoneGeometry.analyze(model);
        List<BoneModelSnapshot.Bone> preorder = new ArrayList<>();
        IdentityHashMap<
                BoneModelSnapshot.Bone,
                BoneModelSnapshot.Bone
                > parents = new IdentityHashMap<>();
        for (BoneModelSnapshot.Bone bone : model.topLevelBones()) {
            PhysicsSolverLayout.collect(bone, null, preorder, parents);
        }

        Set<BoneModelSnapshot.Bone> active = Collections.newSetFromMap(
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
        IdentityHashMap<
                BoneModelSnapshot.Bone,
                CollisionProxyPlan
                > collisionPlans = new IdentityHashMap<>();
        IdentityHashMap<
                BoneModelSnapshot.Bone,
                PhysicsBoneSelectionPlan.SimulationSpace
                > spaces = new IdentityHashMap<>();
        for (BoneModelSnapshot.Bone bone : preorder) {
            if (!plan.isDriven(bone)) {
                continue;
            }
            PhysicsSolverLayout.addPath(bone, active, parents);
            PhysicsBoneSelectionPlan.SimulationSpace space =
                    PhysicsSolverLayout.resolveSimulationSpace(
                            plan.decision(bone),
                            geometry.node(bone),
                            geometry
                    );
            spaces.put(bone, space);
            BoneModelSnapshot.Bone reference =
                    PhysicsSolverLayout.referenceBone(space, geometry);
            if (reference != null) {
                PhysicsSolverLayout.addPath(reference, active, parents);
            }
            CollisionProxyPlan collisionPlan = collisionPlanner.plan(
                    bone,
                    plan.decision(bone),
                    space
            );
            collisionPlans.put(bone, collisionPlan);
            for (BoneModelSnapshot.Bone collisionReference
                    : collisionPlan.referenceBones()) {
                PhysicsSolverLayout.addPath(
                        collisionReference,
                        active,
                        parents
                );
            }
        }
        return new PhysicsLayoutSelection(
                geometry,
                collisionGeometry,
                List.copyOf(preorder),
                PhysicsSolverLayout.orderActive(
                        preorder,
                        active,
                        parents,
                        spaces,
                        collisionPlans,
                        geometry
                ),
                parents,
                spaces,
                collisionPlans
        );
    }
}
