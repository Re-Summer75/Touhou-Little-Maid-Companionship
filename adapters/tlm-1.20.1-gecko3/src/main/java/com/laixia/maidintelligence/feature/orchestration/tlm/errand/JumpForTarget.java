package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;

/**
 * 站到底下，然后跳。
 *
 * <p>把跳跃算进"够不够得着"只让她**愿意去**；原版只在寻路要迈台阶时才跳，而悬在
 * 两格高处的东西没有路可走——寻路当场报走不到，她于是对着一件跳一下就能拿到的
 * 东西毫无反应。这一下就是补那一跳的，从拾物差事提炼出来：任何"走到跟前还差
 * 一跳"的行为共用同一个起跳判据，而不是各自再把边界踩一遍。
 *
 * <p>拿东西仍然是调用方（多半是本体）的事：跳到顶点时她的判定范围罩住目标，
 * 该发生的自然发生。这里只管起跳。
 */
public final class JumpForTarget {
    /**
     * 站得多近才值得起跳。
     *
     * <p>一格二。半格只覆盖"目标悬空、她能站到正下方"；目标在方块顶上时她只能停
     * 在旁边，水平约一格——卡在半格上她就永远不跳。再宽就成了朝着目标的方向乱蹦，
     * 跳完仍然够不着的那些由 {@link TargetPatience} 收场。
     */
    private static final double UNDER_IT = 1.2D;

    private JumpForTarget() {
    }

    /**
     * @param needsAJump 这一件是不是"站着够不到、跳一下够得着"的那一档——判据在
     *                   调用方手里（拾物问的是拾取半径，别的行为问别的），这里
     *                   只认结论。
     */
    public static void consider(
            EntityMaid maid,
            Entity aim,
            boolean needsAJump
    ) {
        if (!needsAJump || !maid.onGround()) {
            return;
        }
        double dx = aim.getX() - maid.getX();
        double dz = aim.getZ() - maid.getZ();
        if (dx * dx + dz * dz > UNDER_IT * UNDER_IT) {
            return;
        }
        maid.getJumpControl().jump();
    }
}
