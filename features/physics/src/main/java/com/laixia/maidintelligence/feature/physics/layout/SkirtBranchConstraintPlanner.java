package com.laixia.maidintelligence.feature.physics.layout;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Connects skirt roots that share one rigid authored mount.
 */
final class SkirtBranchConstraintPlanner {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private SkirtBranchConstraintPlanner() {
    }

    static SkirtBranchConstraintLayout build(
            PhysicsSolverLayout.Node[] nodes,
            IdentityHashMap<BoneModelSnapshot.Bone, BoneRestPose> restPoses
    ) {
        Map<Integer, List<Candidate>> groups = new LinkedHashMap<>();
        for (int nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
            PhysicsSolverLayout.Node node = nodes[nodeIndex];
            if (!eligibleRoot(node, nodes)) {
                continue;
            }
            groups.computeIfAbsent(
                    node.parentIndex(),
                    ignored -> new ArrayList<>()
            ).add(new Candidate(
                    nodeIndex,
                    node.drivenSlot(),
                    restTip(node, restPoses)
            ));
        }

        List<SkirtBranchConstraintLayout.Pair> pairs = new ArrayList<>();
        for (List<Candidate> group : groups.values()) {
            /*
             * Active-node order is stable. Linking each root to its nearest
             * predecessor makes a directed spanning tree whose leader has
             * already committed when the follower enters the frame loop.
             */
            for (int followerIndex = 1;
                 followerIndex < group.size();
                 followerIndex++) {
                Candidate follower = group.get(followerIndex);
                Candidate leader = group.get(0);
                float nearest = follower.restTip().distanceSquared(
                        leader.restTip()
                );
                for (int candidateIndex = 1;
                     candidateIndex < followerIndex;
                     candidateIndex++) {
                    Candidate candidate = group.get(candidateIndex);
                    float distance = follower.restTip().distanceSquared(
                            candidate.restTip()
                    );
                    if (distance < nearest) {
                        nearest = distance;
                        leader = candidate;
                    }
                }
                pairs.add(new SkirtBranchConstraintLayout.Pair(
                        leader.nodeIndex(),
                        leader.drivenSlot(),
                        follower.nodeIndex(),
                        follower.drivenSlot()
                ));
            }
        }
        if (pairs.isEmpty()) {
            return SkirtBranchConstraintLayout.empty(nodes.length);
        }
        return new SkirtBranchConstraintLayout(
                pairs.toArray(SkirtBranchConstraintLayout.Pair[]::new),
                nodes.length
        );
    }

    private static boolean eligibleRoot(
            PhysicsSolverLayout.Node node,
            PhysicsSolverLayout.Node[] nodes
    ) {
        PhysicsBoneSelectionPlan.Decision decision = node.decision();
        int parentIndex = node.parentIndex();
        return node.driven()
                && decision.type() == PhysicsBoneSelectionPlan.PartType.SKIRT
                && decision.chainSegment().present()
                && decision.chainSegment().index() == 0
                && node.constraint().enabled()
                && parentIndex >= 0
                && parentIndex < nodes.length
                && !nodes[parentIndex].driven();
    }

    private static Vector3f restTip(
            PhysicsSolverLayout.Node node,
            IdentityHashMap<BoneModelSnapshot.Bone, BoneRestPose> restPoses
    ) {
        Vector3f localTip = node.kinematics().effectivePivot();
        localTip.fma(
                node.kinematics().segmentLength() / PIXELS_PER_BLOCK,
                node.axisInto(new Vector3f())
        );
        BoneRestPose pose = restPoses.get(node.bone());
        return pose == null
                ? localTip
                : pose.transformPosition(localTip, new Vector3f());
    }

    private record Candidate(
            int nodeIndex,
            int drivenSlot,
            Vector3f restTip
    ) {
    }
}
