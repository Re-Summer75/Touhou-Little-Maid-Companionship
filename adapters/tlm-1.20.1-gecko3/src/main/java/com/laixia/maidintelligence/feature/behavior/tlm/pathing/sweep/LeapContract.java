package com.laixia.maidintelligence.feature.behavior.tlm.pathing.sweep;

import com.laixia.maidintelligence.feature.behavior.domain.motion.BallisticArc;

/**
 * 起跳的合同数：竖直速度、滞空档、配速公式——图与执行**唯一**的来源。
 *
 * <p>这些数从前是执行侧（{@code Leaper}）的私房常数；扫掠仿真接管边验收
 * 之后，图侧要用**同一股初速**仿真"她真跳会落在哪"，两侧各抄一份就是两
 * 张皮的温床（图按自己的数连了边、执行按自己的数跳不到）。搬到这里，谁
 * 用谁引。
 */
public final class LeapContract {
    /** 起跳竖直速度：原版 jumpFromGround 的数。 */
    public static final double JUMP_RISE = 0.42D;

    /** 同层起跳的滞空时长（tick）：起跳 0.42、重力 0.08 的弹道回到同一高度。 */
    public static final double AIRBORNE_TICKS = 11.3D;

    /** 落一格的滞空时长：同一条弹道再落一格。 */
    public static final double DESCENT_TICKS = 13.4D;

    /** 上一格的滞空时长：取上升段那一档——落点要在弧顶前够着，不是等它
     *  掉回来（弧顶在第五 tick、一格二五，从下面数第三 tick 就过了一格）。 */
    public static final double ASCENT_TICKS = 7.5D;

    /** 配速余量：起跳晚一 tick、助跑差半格这类时序抖动的零头。 */
    public static final double LEAP_MARGIN = 1.05D;

    /** 推力上限：贴边起跳的满冲刺玩家量级。跨三格缺口按真实弹道要 0.58，
     *  够不着就是够不着——封在这里，落点近沿仍在射程内（0.5 能走三格六）。 */
    public static final double MAX_LEAP_SPEED = 0.50D;

    /** 推力下限：再短的跳也给出能离沿的一口气。 */
    public static final double MIN_LEAP_SPEED = 0.2D;

    private LeapContract() {
    }

    /** 落差对应的滞空档。 */
    public static double ticksFor(int dy) {
        return dy > 0 ? ASCENT_TICKS
                : dy < 0 ? DESCENT_TICKS
                : AIRBORNE_TICKS;
    }

    /**
     * 要在 {@code ticks} tick 内飞过 {@code distance} 格，起跳该有多快。
     *
     * <p>**空气阻力是复利，不是折扣**：滞空第 t tick 的水平速度是初速乘
     * 0.91 的 t 次方，走过的路是那串等比数列的和
     * <code>v(1-0.91^t)/0.09</code>，不是 v·t。按 v·t 配速就系统性欠冲：
     * 同层短一成七、落一格短两成六、上一格短两成五（欠冲史与实机读数见
     * {@code Leaper} 的碑）。
     */
    public static double paceFor(double distance, double ticks) {
        return distance * (1.0D - BallisticArc.HORIZONTAL_DRAG)
                / (1.0D - Math.pow(BallisticArc.HORIZONTAL_DRAG, ticks));
    }

    /** 一步到位的起跳前速：按落差取档、乘余量、夹上下限。 */
    public static double launchSpeed(double distance, int dy) {
        return Math.min(MAX_LEAP_SPEED, Math.max(MIN_LEAP_SPEED,
                LEAP_MARGIN * paceFor(distance, ticksFor(dy))));
    }

    /**
     * 下崖迈出去的水平推力：按水平距离配、夹在小步区间。跳有滞空档反解，
     * 下崖只有这一小步——推大了长滞空里会飘过一格宽的落点（终审拍过一次
     * 0.15 的中值，深崖仿真直接过冲出格，她就此不肯下崖）。
     */
    public static double dropPushFor(double distance) {
        return Math.max(0.12D, Math.min(0.18D, distance / 12.0D));
    }
}
