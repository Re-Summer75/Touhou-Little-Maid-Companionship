package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.motion.BallisticArc;

/**
 * 弧线数学对表实机——金标准是摔落黑匣子从真实游戏带回来的逐 tick 读数。
 *
 * <p>这套方程将成为扫掠仿真的唯一验收内核；它错一位，图与执行两侧就会各
 * 自长出补偿，"图连了边执行走不过"的两张皮重演。所以不测"合理性"，测
 * **逐 tick 吻合**。
 */
public final class BallisticArcVerification {
    private BallisticArcVerification() {
    }

    public static void main(String[] args) {
        verifiesVerticalSpeedMatchesTheFlightRecorder();
        verifiesDisplacementComesBeforeDecay();
        verifiesHorizontalSpeedDecaysByTheSharedDrag();
        verifiesAFullJumpCrestsAboveOneBlockButBelowAFence();
        System.out.println("Ballistic arc verification passed.");
    }

    /**
     * 实机黑匣子（悬空门板案，两卷带子逐字节一致）的竖直速度序列：起跳
     * +0.42，随后每 tick (v − 0.08) × 0.98。
     */
    private static void verifiesVerticalSpeedMatchesTheFlightRecorder() {
        double[] recorded = {
                0.42D, 0.33D, 0.25D, 0.16D, 0.08D, 0.00D,
                -0.08D, -0.15D, -0.23D, -0.30D, -0.37D,
        };
        BallisticArc.Step step = new BallisticArc.Step(
                0.0D, 0.0D, 0.0D, 0.0D, recorded[0], 0.0D);
        for (int tick = 1; tick < recorded.length; tick++) {
            step = step.next();
            require(Math.abs(recorded[tick] - step.vy()) < 0.005D,
                    "第 " + tick + " tick 的竖直速度 " + step.vy()
                            + " 偏离实机带子的 " + recorded[tick]);
        }
    }

    /** 位移在衰减**之前**发生：起跳 +0.42 的下一帧，人已经高了 0.42。 */
    private static void verifiesDisplacementComesBeforeDecay() {
        BallisticArc.Step step = new BallisticArc.Step(
                0.0D, 0.0D, 0.0D, 0.0D, 0.42D, 0.0D).next();
        require(Math.abs(step.y() - 0.42D) < 1.0E-9D,
                "顺序错了：原版先按当前速度位移、再衰减速度，实测升幅是 "
                        + step.y());
    }

    /** 水平分量按 0.91 几何衰减——与执行侧弧线合同同一个数。 */
    private static void verifiesHorizontalSpeedDecaysByTheSharedDrag() {
        BallisticArc.Step step = new BallisticArc.Step(
                0.0D, 0.0D, 0.0D, 0.50D, 0.0D, 0.0D);
        double expected = 0.50D;
        for (int tick = 0; tick < 8; tick++) {
            step = step.next();
            expected *= BallisticArc.HORIZONTAL_DRAG;
            require(Math.abs(step.vx() - expected) < 1.0E-9D,
                    "水平衰减在第 " + tick + " tick 偏离 0.91 几何列");
        }
    }

    /**
     * 满跳升幅在一格与一格半之间：能上一格、上不了栅栏顶——执行侧登阶与
     * 上跳判据一直依赖的物理事实，仿真必须给出同一个数。
     */
    private static void verifiesAFullJumpCrestsAboveOneBlockButBelowAFence() {
        double crest = BallisticArc.crest(0.42D, 16);
        require(crest > 1.0D && crest < 1.5D,
                "满跳最高 " + crest + "，应在一格与一格半之间");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
