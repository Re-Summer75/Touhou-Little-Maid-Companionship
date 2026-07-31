package com.laixia.maidintelligence.feature.physics.layout.plan;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.CollisionProxyPlan;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dependency-safe node selection and ordering for layout bake time.
 */
public final class LayoutDependencyPlanner {
    private LayoutDependencyPlanner() {
    }

    public static void addPath(
            BoneModelSnapshot.Bone bone,
            Set<BoneModelSnapshot.Bone> active,
            Map<BoneModelSnapshot.Bone, BoneModelSnapshot.Bone> parents
    ) {
        BoneModelSnapshot.Bone cursor = bone;
        while (cursor != null && active.add(cursor)) {
            cursor = parents.get(cursor);
        }
    }

    public static PhysicsBoneSelectionPlan.SimulationSpace
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
        if (node != null
                && geometry.head() != null
                && node.isDescendantOf(geometry.head())) {
            return PhysicsBoneSelectionPlan.SimulationSpace.HEAD_LOCAL;
        }
        if (geometry.head() != null
                && (decision.type()
                == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                || decision.type() == PhysicsBoneSelectionPlan.PartType.HAIR
                || decision.type() == PhysicsBoneSelectionPlan.PartType.EAR)) {
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

    public static BoneModelSnapshot.Bone referenceBone(
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

    public static void collect(
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

    public static List<BoneModelSnapshot.Bone> orderActive(
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

    private static boolean dependenciesReady(
            BoneModelSnapshot.Bone bone,
            Set<BoneModelSnapshot.Bone> active,
            Set<BoneModelSnapshot.Bone> emitted,
            Map<BoneModelSnapshot.Bone, BoneModelSnapshot.Bone> parents,
            Map<
                    BoneModelSnapshot.Bone,
                    PhysicsBoneSelectionPlan.SimulationSpace
                    > spaces,
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
}
