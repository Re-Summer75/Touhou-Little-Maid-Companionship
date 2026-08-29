package com.laixia.maidintelligence.feature.behavior.domain.motion;

/**
 * 她在空中的运动规律，逐 tick——扫掠仿真的数学芯。
 *
 * <p>手工枚举的边族在本质上追不上方块形状的组合爆炸：门板三缺口、石锥、
 * 末地烛，每个新形状打一个 if 补丁，补丁之间还互相踩（实测两次：矮门板放
 * 宽误伤悬吊门板、登阶让位裸推）。一劳永逸的形态是**拿她真实的运动方程把
 * 身位箱沿轨迹扫一遍，撞不撞、落不落得稳，让物理自己回答**——这里是那套
 * 方程；碰撞扫掠在适配层（它要读真实世界的 VoxelShape）。
 *
 * <p>三个常数不是拟合值，是原版服务端每 tick 的实际规律，且**先位移、后
 * 衰减**——顺序用实机黑匣子的带子逐 tick 校准过：起跳那 tick 竖直速度
 * +0.42，下一 tick 位移恰是 0.42，随后速度 +0.33 = (0.42 − 0.08) × 0.98。
 * 序列全程对表：+0.42 → +0.33 → +0.25 → +0.16 → +0.08 → +0.00 → −0.08
 * → −0.15 → −0.23 → −0.30 → −0.37，一位不差。
 *
 * <p>水平阻力 0.91 与执行侧的弧线合同（{@code LeapFlight}）同源——那边的
 * 配速反解（{@code paceFor}）早已按它实测校准。**全仓只许这一份**：图、
 * 执行、仿真三处用的必须是同一套数，否则"图连了边执行走不过"那类两张皮
 * 的病还会回来。
 */
public final class BallisticArc {
    /** 每 tick 竖直减速。 */
    public static final double GRAVITY = 0.08D;

    /** 竖直分量的每 tick 衰减。 */
    public static final double VERTICAL_DRAG = 0.98D;

    /** 水平分量的每 tick 衰减（空中）。 */
    public static final double HORIZONTAL_DRAG = 0.91D;

    private BallisticArc() {
    }

    /** 一步之后的状态；位移已经走完、速度已经衰减。 */
    public record Step(
            double x, double y, double z,
            double vx, double vy, double vz
    ) {
        /** 原版次序：先按当前速度位移，再衰减速度。 */
        public Step next() {
            return new Step(
                    x + vx, y + vy, z + vz,
                    vx * HORIZONTAL_DRAG,
                    (vy - GRAVITY) * VERTICAL_DRAG,
                    vz * HORIZONTAL_DRAG
            );
        }
    }

    /**
     * 全弧最高点相对起跳点的升幅（无碰撞时）。
     *
     * <p>闭式解不值得：十几步循环在这里比推导可靠——规律里有乘法衰减与
     * 减法重力的混合，闭式会引进第二份"物理"，对表对象就没了。
     */
    public static double crest(double riseSpeed, int withinTicks) {
        double height = 0.0D;
        double best = 0.0D;
        double vy = riseSpeed;
        for (int tick = 0; tick < withinTicks; tick++) {
            height += vy;
            best = Math.max(best, height);
            vy = (vy - GRAVITY) * VERTICAL_DRAG;
        }
        return best;
    }
}
