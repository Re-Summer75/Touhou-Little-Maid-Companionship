package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;

/**
 * 崖沿往下走的那一族边。
 *
 * <p>从 {@code SafeFootingNodeEvaluator} 按职责拆出（单文件五百行的布局
 * 纪律）。那边管跳与钻，这里只管**往下**：外一格是空、脚下两格以上无立足
 * 时，往下扫到第一块真地板，连一条贵三格的下崖边。实机黑匣子抓到的"可达
 * 前沿站桩"缺的就是它：高台尽头的她与目标之间只隔一段落差，图里没有这条
 * 边，A* 的最优解就是原地。
 */
final class BrinkEdges {
    /** 下崖边的定价：折合多走几格路，让 A* 有楼梯先走楼梯。 */
    private static final float DROP_OFF_MALUS = 3.0F;

    private final SafeFootingNodeEvaluator owner;

    BrinkEdges(SafeFootingNodeEvaluator owner) {
        this.owner = owner;
    }

    /**
     * 下崖跟进的落点：外一格身位头位全空、脚下两格以上无立足（一格内是原版
     * 台阶下行的地盘），往下扫到第一块盖得住格心的地板。干落六格以内、或
     * 落进水里（水面下那格是水就算，深潭不限高度），都连成一条贵三格的边。
     */
    int dropOffLanding(
            Node[] out,
            int count,
            Node node,
            Direction direction
    ) {
        int x = node.x + direction.getStepX();
        int z = node.z + direction.getStepZ();
        if (!owner.airy(owner.world(), x, node.y, z)
                || !owner.airy(owner.world(), x, node.y + 1, z)) {
            return count;
        }
        // 脚下一格还有立足的话是原版续走的台阶，不归这条边管。
        if (FootingRule.coveringTopAt(owner.world(), new BlockPos(x, node.y - 1, z))
                > Double.NEGATIVE_INFINITY) {
            return count;
        }
        for (int depth = 2; depth <= SafeFootingNodeEvaluator.DROP_MAX; depth++) {
            int y = node.y - depth;
            if (y <= owner.world().getMinBuildHeight()) {
                return count;
            }
            BlockPos floorPos = new BlockPos(x, y - 1, z);
            if (owner.world().getFluidState(floorPos).isSource()) {
                return emitDropLanding(out, count, x, y, z);
            }
            double top = FootingRule.coveringTopAt(owner.world(), floorPos);
            if (top > Double.NEGATIVE_INFINITY) {
                // 地板要贴脚（半格内），沉得更深的等下一轮扫描去接。
                return top >= y - 0.6D
                        ? emitDropLanding(out, count, x, y, z)
                        : count;
            }
        }
        return count;
    }

    /** 下崖落点定型：可走、贵三格。落点自身的可走性照常验。 */
    int emitDropLanding(Node[] out, int count, int x, int y, int z) {
        if (!owner.walkableCell(x, y, z)
                && !owner.world().getFluidState(new BlockPos(x, y - 1, z))
                        .isSource()) {
            return count;
        }
        Node landing = owner.nodeAt(x, y, z);
        if (landing == null || landing.closed) {
            return count;
        }
        landing.type = BlockPathTypes.WALKABLE;
        landing.costMalus = Math.max(landing.costMalus, DROP_OFF_MALUS);
        if (count < out.length) {
            out[count++] = landing;
        }
        return count;
    }
}
