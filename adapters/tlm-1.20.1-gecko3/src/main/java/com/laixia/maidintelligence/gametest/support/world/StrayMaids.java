package com.laixia.maidintelligence.gametest.support.world;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 收尸队：把上一场漏下的女仆清走。
 *
 * <p>测试里的 {@code maid.discard()} 几乎都排在断言之后，而**断言失败会
 * 抛异常**——一红，那只女仆就留在世界里继续每 tick 规划、执行、喂读数
 * 带。红越攒越多，残留越多，整轮越跑越慢（实测：后段过载十九次、总耗
 * 时三十二分钟，面板上同时挂着八十条读数带——那不是八十场在跑，是历轮
 * 的尸体）。
 *
 * <p>与其去挪五十一处 discard（诊断串里还引用着她，顺序一动就容易出
 * 错），不如在**生成新女仆时**顺手清掉超龄的：任何一场自己的时限最长
 * 九百 tick，活过 {@value #STALE_TICKS} 的必然是漏下的。并发跑的邻场不
 * 会被误伤——它们的女仆还年轻。
 */
public final class StrayMaids {
    /** 超过这个岁数还活着的，一定是没人收的尸。 */
    private static final int STALE_TICKS = 3000;

    /** 清扫半径：整个测试棋盘都在这个盒子里。 */
    private static final double REACH = 512.0D;

    private StrayMaids() {
    }

    /** 生成新女仆前叫一声，把超龄的收走。 */
    public static void sweep(GameTestHelper helper) {
        Level level = helper.getLevel();
        AABB around = new AABB(helper.absolutePos(
                net.minecraft.core.BlockPos.ZERO)).inflate(REACH);
        for (EntityMaid stray : level.getEntitiesOfClass(
                EntityMaid.class, around,
                maid -> maid.tickCount > STALE_TICKS)) {
            stray.discard();
        }
    }
}
