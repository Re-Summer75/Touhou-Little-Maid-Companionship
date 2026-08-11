package com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 在撞上台阶之前起跳，而不是撞停之后。
 *
 * <p>原版的判据在 {@code MoveControl}：高度差超过步高、**并且**水平距离平方小于
 * {@code max(1, 体宽)} 才发出跳跃。也就是说她必须先走到贴着方块面才会跳，而撞上
 * 方块面那一刻 {@code Entity.move} 的碰撞会把水平速度归零。
 *
 * <p>于是上一格的代价是两笔叠加的：
 *
 * <ol>
 *   <li>撞停——水平速度清零；
 *   <li>从零起跳，而空中的水平加速度是 {@code flyingSpeed = 0.02}，只有地面
 *       （石头上约 0.15–0.2）的十分之一，还要持续整整十一 tick。
 * </ol>
 *
 * <p>平地上追她的东西一分不付。一格高的坎因此能吃掉她一格半到两格的领先，而她的
 * 撤退本来就只领先那么多。
 *
 * <p>玩家不吃这个亏，靠的不是走得更快，而是**在碰到之前就跳**：
 * {@code jumpFromGround} 保留水平分量，整段跳跃是带着跑速过去的。这里给她的正是
 * 这个时机，不是额外的机动力——步高一格不动，跳跃高度不动，只把那一跳提前。
 */
public final class StepAhead {
    /**
     * 慢于这个速度就不提前跳。
     *
     * <p>提前量的价值全在"把速度带过去"；站着或蹭着走的时候没有速度可带，那一跳
     * 就只是白白离地十一 tick。
     */
    private static final double WORTH_CARRYING = 0.08D;

    /** 往前看几格。一格是"下一步就撞上"，两格给起跳留出反应。 */
    private static final double LOOK_NEAR = 1.0D;

    private static final double LOOK_FAR = 2.0D;

    private StepAhead() {
    }

    /**
     * 前方一两格是不是一堵刚好一格高的坎；是就现在跳。
     *
     * <p>只认"她跨不上去、但跳得过去"的那一种：脚下那格实心、头上两格空。半砖和
     * 台阶在步高之内，本来就不用跳；两格以上的墙跳也过不去，跳了纯亏。
     */
    public static void leapEarly(EntityMaid maid) {
        if (!maid.onGround() || maid.isInWater() || maid.isPassenger()) {
            return;
        }
        Vec3 motion = maid.getDeltaMovement();
        double speed = Math.hypot(motion.x, motion.z);
        if (speed < WORTH_CARRYING) {
            return;
        }
        Vec3 heading = new Vec3(motion.x / speed, 0.0D, motion.z / speed);
        for (double ahead = LOOK_NEAR; ahead <= LOOK_FAR; ahead += 1.0D) {
            if (stepUpAt(maid, heading, ahead)) {
                maid.getJumpControl().jump();
                return;
            }
        }
    }

    private static boolean stepUpAt(
            EntityMaid maid,
            Vec3 heading,
            double ahead
    ) {
        Level level = maid.level();
        BlockPos foot = BlockPos.containing(
                maid.position().add(heading.scale(ahead))
        );
        // 高于步高才需要跳：脚下那一格是实心的。
        if (level.getBlockState(foot).getCollisionShape(level, foot).isEmpty()) {
            return false;
        }
        // 而且跳得过去：上面两格要放得下她。
        return solidFree(level, foot.above())
                && solidFree(level, foot.above(2));
    }

    private static boolean solidFree(Level level, BlockPos at) {
        return level.getBlockState(at).getCollisionShape(level, at).isEmpty();
    }
}
