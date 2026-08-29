package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

/**
 * 边的**类型与合同**：从一个锚点到下一个锚点，用什么动作、带什么初速。
 *
 * <p>格图时代路径只有坐标序列，执行器每一步都要从格差**反猜**动作（十几
 * 个段判据、每个判据一串形状特判），猜错一步就是摔。自有路径的边在建图时
 * 就把动作与初速定死——图侧已经拿扫掠仿真按这股初速验收过，执行侧照单执
 * 行即可，猜测这一层整个退役。
 *
 * @param move  动作类型
 * @param speed 水平初速（跳/降的合同值；走类为步速档）
 */
public record Stride(Move move, double speed) {

    /** 动作类型。 */
    public enum Move {
        /** 平走（含贴脚小落差）。 */
        WALK,
        /** 登一格：贴脸带锁跳。 */
        CLIMB,
        /** 跳跃：带锁起跳，速度是合同值。 */
        LEAP,
        /** 下崖：小步迈出，自由落体，落点锁。 */
        DROP,
        /** 挤缝：贴边带里侧身过柱。 */
        SQUEEZE,
        /** 穿角：瞄方块角点斜穿。 */
        CORNER
    }

    public static final Stride WALK_PACE = new Stride(Move.WALK, 1.0D);
}
