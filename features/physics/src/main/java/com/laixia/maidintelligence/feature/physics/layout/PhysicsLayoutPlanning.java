package com.laixia.maidintelligence.feature.physics.layout;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.CollisionProxyPlan;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.layout.plan.LayoutDependencyPlanner;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Package-private bridge that keeps layout planning internals off the facade.
 */
final class PhysicsLayoutPlanning {
    private PhysicsLayoutPlanning() {
    }

    static void addPath(
            BoneModelSnapshot.Bone bone,
            Set<BoneModelSnapshot.Bone> active,
            Map<BoneModelSnapshot.Bone, BoneModelSnapshot.Bone> parents
    ) {
        LayoutDependencyPlanner.addPath(bone, active, parents);
    }

    static PhysicsBoneSelectionPlan.SimulationSpace resolveSimulationSpace(
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        return LayoutDependencyPlanner.resolveSimulationSpace(
                decision,
                node,
                geometry
        );
    }

    static BoneModelSnapshot.Bone referenceBone(
            PhysicsBoneSelectionPlan.SimulationSpace space,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        return LayoutDependencyPlanner.referenceBone(space, geometry);
    }

    static void collect(
            BoneModelSnapshot.Bone bone,
            BoneModelSnapshot.Bone parent,
            List<BoneModelSnapshot.Bone> preorder,
            Map<BoneModelSnapshot.Bone, BoneModelSnapshot.Bone> parents
    ) {
        LayoutDependencyPlanner.collect(bone, parent, preorder, parents);
    }

    static List<BoneModelSnapshot.Bone> orderActive(
            List<BoneModelSnapshot.Bone> preorder,
            Set<BoneModelSnapshot.Bone> active,
            Map<BoneModelSnapshot.Bone, BoneModelSnapshot.Bone> parents,
            Map<
                    BoneModelSnapshot.Bone,
                    PhysicsBoneSelectionPlan.SimulationSpace
                    > spaces,
            Map<BoneModelSnapshot.Bone, CollisionProxyPlan> collisionPlans,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        return LayoutDependencyPlanner.orderActive(
                preorder,
                active,
                parents,
                spaces,
                collisionPlans,
                geometry
        );
    }
}
