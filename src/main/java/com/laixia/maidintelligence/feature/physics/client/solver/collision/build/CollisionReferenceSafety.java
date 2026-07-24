package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Rejects automatic frames whose animation-only geometry depends on physics.
 */
final class CollisionReferenceSafety {
    private CollisionReferenceSafety() {
    }

    static boolean isSafe(
            PhysicsBoneGeometry.Node reference,
            PhysicsBoneGeometry.Node driven,
            PhysicsBoneSelectionPlan plan,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        if (reference == null || driven == null
                || reference.isDescendantOf(driven)
                || plan.isDriven(reference.bone())
                || hasDrivenAncestor(reference, plan)) {
            return false;
        }
        for (PhysicsBoneGeometry.Node contributor
                : closestSolidContributors(reference, geometry)) {
            if (plan.isDriven(contributor.bone())
                    || hasDrivenAncestor(contributor, plan)
                    || contributor.isDescendantOf(driven)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasDrivenAncestor(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneSelectionPlan plan
    ) {
        PhysicsBoneGeometry.Node cursor = node.parent();
        while (cursor != null) {
            if (plan.isDriven(cursor.bone())) {
                return true;
            }
            cursor = cursor.parent();
        }
        return false;
    }

    private static List<PhysicsBoneGeometry.Node>
    closestSolidContributors(
            PhysicsBoneGeometry.Node root,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        if (root.hasGeometry()) {
            return List.of(root);
        }
        List<PhysicsBoneGeometry.Node> frontier = List.of(root);
        while (!frontier.isEmpty()) {
            List<PhysicsBoneGeometry.Node> solid = new ArrayList<>();
            List<PhysicsBoneGeometry.Node> next = new ArrayList<>();
            for (PhysicsBoneGeometry.Node node : frontier) {
                for (var childBone : node.bone().children()) {
                    PhysicsBoneGeometry.Node child =
                            geometry.node(childBone);
                    if (child == null) {
                        continue;
                    }
                    if (child.hasGeometry()) {
                        solid.add(child);
                    } else {
                        next.add(child);
                    }
                }
            }
            if (!solid.isEmpty()) {
                return solid;
            }
            frontier = next;
        }
        return List.of();
    }
}
