package com.laixia.maidintelligence.gametest.pathbench;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation
        .MaidPathNavigation;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .PathClock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Set;

/**
 * 对照组的计时导航：**链一模一样，只是记账**。
 *
 * <p>群体基准的对照女仆没有主人，附加脑（{@code MaidIntentBehavior}）不
 * 入场，升级守卫从没醒过——她们拿的是裸的 {@code MaidPathNavigation}，
 * 也就压根不经过 {@code SureFootedNavigation} 里那两处计时。sw180 实测
 * stock 的 pathcost 全零，就是这么来的。这个子类把同样的两处边界计时补
 * 在原版链上：{@code tick()} 是执行侧（followThePath ＋ 给 MoveControl
 * 下指令），{@code createPath} 汇流层是规划侧（原版 A*）。
 *
 * <p>只在基准局用。守卫按**精确类**换导航（{@code getClass() ==
 * MaidPathNavigation.class}），这个子类不在名单上；就算哪天附加脑在基准
 * 里醒了，第三分支会把它换成 SureFooted——那时 stock 读数归零，账本自己
 * 会揭发。
 */
final class TimedVanillaNavigation extends MaidPathNavigation {
    /** 这一 tick 里嵌在 {@code super.tick()} 内部的规划耗时。原版 tick
     *  自己会做延迟重铺（createPath 嵌在 tick 里），不扣掉就会在
     *  plan+walk 求和时把同一段时间算两遍——SureFooted 那边重铺在计时
     *  边界之外，没有这个问题。 */
    private long nested;

    TimedVanillaNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    public void tick() {
        nested = 0L;
        long clock = System.nanoTime();
        super.tick();
        PathClock.walk(this.mob.getUUID(),
                System.nanoTime() - clock - nested);
    }

    @Override
    protected Path createPath(Set<BlockPos> targets, int regionOffset,
            boolean aboveGround, int accuracy, float range) {
        long clock = System.nanoTime();
        Path path = super.createPath(
                targets, regionOffset, aboveGround, accuracy, range);
        long spent = System.nanoTime() - clock;
        nested += spent;
        PathClock.plan(this.mob.getUUID(), spent);
        return path;
    }
}
