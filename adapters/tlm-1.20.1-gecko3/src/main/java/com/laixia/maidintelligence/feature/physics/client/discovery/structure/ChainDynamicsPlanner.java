package com.laixia.maidintelligence.feature.physics.client.discovery.structure;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves real driven parent-child components and bakes segment profiles.
 */
public final class ChainDynamicsPlanner {
    private ChainDynamicsPlanner() {
    }

    public static void apply(
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        Map<PhysicsBoneGeometry.Node, List<PhysicsBoneGeometry.Node>> children =
                new IdentityHashMap<>();
        List<PhysicsBoneGeometry.Node> roots = new ArrayList<>();
        for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
            PhysicsBoneSelectionPlan.Decision decision =
                    plan.current(node.bone());
            if (decision == null || !decision.driven()) {
                continue;
            }
            PhysicsBoneGeometry.Node parent = drivenParent(
                    node,
                    plan,
                    decision.chainId()
            );
            if (parent == null) {
                roots.add(node);
            } else {
                children.computeIfAbsent(
                        parent,
                        ignored -> new ArrayList<>()
                ).add(node);
            }
        }
        for (PhysicsBoneGeometry.Node root : roots) {
            int count = maximumDepth(root, children) + 1;
            assign(root, root.path(), 0, count, children, plan);
        }
    }

    private static PhysicsBoneGeometry.Node drivenParent(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneSelectionPlan.Builder plan,
            String chainId
    ) {
        PhysicsBoneGeometry.Node cursor = node.parent();
        while (cursor != null) {
            PhysicsBoneSelectionPlan.Decision candidate =
                    plan.current(cursor.bone());
            if (candidate != null && candidate.driven()) {
                return candidate.chainId().equals(chainId)
                        ? cursor
                        : null;
            }
            if (cursor.hasGeometry()) {
                return null;
            }
            cursor = cursor.parent();
        }
        return null;
    }

    private static int maximumDepth(
            PhysicsBoneGeometry.Node node,
            Map<PhysicsBoneGeometry.Node, List<PhysicsBoneGeometry.Node>>
                    children
    ) {
        int maximum = 0;
        for (PhysicsBoneGeometry.Node child :
                children.getOrDefault(node, List.of())) {
            maximum = Math.max(
                    maximum,
                    1 + maximumDepth(child, children)
            );
        }
        return maximum;
    }

    private static void assign(
            PhysicsBoneGeometry.Node node,
            String rootPath,
            int index,
            int count,
            Map<PhysicsBoneGeometry.Node, List<PhysicsBoneGeometry.Node>>
                    children,
            PhysicsBoneSelectionPlan.Builder plan
    ) {
        PhysicsBoneSelectionPlan.Decision decision =
                plan.current(node.bone());
        PhysicsBoneSelectionPlan.ChainSegment segment =
                new PhysicsBoneSelectionPlan.ChainSegment(
                        rootPath,
                        index,
                        count
                );
        plan.decide(
                node.bone(),
                ChainDynamicsCurve.apply(decision, segment)
        );
        for (PhysicsBoneGeometry.Node child :
                children.getOrDefault(node, List.of())) {
            assign(
                    child,
                    rootPath,
                    index + 1,
                    count,
                    children,
                    plan
            );
        }
    }
}
