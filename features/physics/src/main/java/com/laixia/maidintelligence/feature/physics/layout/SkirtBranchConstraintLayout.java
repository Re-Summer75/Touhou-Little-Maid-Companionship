package com.laixia.maidintelligence.feature.physics.layout;

import java.util.Arrays;

/**
 * Immutable directed tethers between adjacent skirt-chain roots.
 *
 * <p>Every follower points to an earlier node in solver order. The runtime can
 * therefore constrain it against a leader that has already committed this
 * frame, without a second skeleton pass or order-dependent backtracking.
 */
public final class SkirtBranchConstraintLayout {
    private final Pair[] pairs;
    private final int[] pairByFollowerNode;

    SkirtBranchConstraintLayout(Pair[] pairs, int activeNodeCount) {
        this.pairs = pairs;
        pairByFollowerNode = new int[Math.max(0, activeNodeCount)];
        Arrays.fill(pairByFollowerNode, -1);
        for (int index = 0; index < pairs.length; index++) {
            int follower = pairs[index].followerNodeIndex();
            if (follower >= 0 && follower < pairByFollowerNode.length) {
                pairByFollowerNode[follower] = index;
            }
        }
    }

    static SkirtBranchConstraintLayout empty(int activeNodeCount) {
        return new SkirtBranchConstraintLayout(
                new Pair[0],
                activeNodeCount
        );
    }

    public int pairCount() {
        return pairs.length;
    }

    public Pair pair(int index) {
        return pairs[index];
    }

    public int pairForFollowerNode(int nodeIndex) {
        return nodeIndex >= 0 && nodeIndex < pairByFollowerNode.length
                ? pairByFollowerNode[nodeIndex]
                : -1;
    }

    public record Pair(
            int leaderNodeIndex,
            int leaderDrivenSlot,
            int followerNodeIndex,
            int followerDrivenSlot
    ) {
    }
}
