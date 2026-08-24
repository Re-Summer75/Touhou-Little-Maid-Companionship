package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidPathNavigation;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.path.MaidWrappedPathFinder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

/**
 * 本体的地面导航，换上会查立足点的节点评估，跟随整个交给分段执行器。
 *
 * <p>规划层：{@code SafeFootingNodeEvaluator}（立足验收 + 三维跳跃图）。
 * 执行层：{@code SegmentedPathwalk}——原版 followThePath 的松散判到与定向
 * 抄近道在悬空窄道上就是摔因清单，tick 里不再调用它，路径按段执行。本体
 * {@code MaidPathNavigation} 除评估器接线外没有自己的跟随逻辑，替换零损失；
 * 能力开关（开门、过门、浮水）照抄本体，少一个都是行为回归。
 *
 * <p>接线走 {@code EntityMaid.setNavigation}（本体公开的接口，游泳管理器换水陆
 * 导航用的就是它），在环境钩子上做**升级守卫**：发现她拿着的是裸的
 * {@code MaidPathNavigation} 就换成这一个。本体的 {@code MaidNavigationManager}
 * 出水或重置时会把存着的裸实例装回去，下一 tick 这里再升级——一 tick 的窗口，
 * 不与它互抢；水下导航类型不同，不受影响。
 */
public final class SureFootedNavigation extends MaidPathNavigation {
    /** 空气目标向下找地面只找这么深；再深就不是"落地"而是"改派"。 */
    private static final int GROUNDING_REACH = 8;

    private final SegmentedPathwalk pathwalk;

    public SureFootedNavigation(Mob mob, Level level) {
        super(mob, level);
        this.pathwalk = new SegmentedPathwalk(mob, this);
    }

    /**
     * 悬空目标不许改派到深渊底。
     *
     * <p>原版对空气目标的落地规则是**垂直扫到第一个非空气**：桥旁悬空的走
     * 目标（跟随的皮筋把远目的地夹回绳边、或意图给的近旁点位）会被降到四五
     * 十格下的海面，从此建路全空、她被派去海底——实机黑匣子的供词正是
     * walkTarget y110、navTarget y63、nopath 站桩，玩家看到的就是"站在活板
     * 门上不知道要跳"。落地只在贴脚 {@value GROUNDING_REACH} 格内做；更深
     * 的悬空目标保留原坐标当灯塔，A* 自然给出"沿路走到离它最近的点"——
     * 她照常过桥、跳缺口，走到能走到的最近处，意图层自会换下一件事。
     */
    @Override
    public Path createPath(BlockPos target, int accuracy) {
        if (!this.level.getBlockState(target).isAir()) {
            return super.createPath(target, accuracy);
        }
        BlockPos.MutableBlockPos drop = target.below().mutable();
        int floor = Math.max(
                this.level.getMinBuildHeight(),
                target.getY() - GROUNDING_REACH
        );
        while (drop.getY() > floor
                && this.level.getBlockState(drop).isAir()) {
            drop.move(0, -1, 0);
        }
        if (!this.level.getBlockState(drop).isAir()) {
            return super.createPath(drop.above(), accuracy);
        }
        // 深渊上的灯塔：绕开原版那次落地，按原坐标建"尽力接近"的路。
        return this.createPath(ImmutableSet.of(target), 8, false, accuracy);
    }

    /** 建路入口的黑匣子计数：连续返空多少 tick 后开口。 */
    private static final int NULL_CONFESS_TICKS = 100;
    private int nullStreak;
    private boolean nullConfessed;

    /**
     * 建路入口的黑匣子：实机站桩的供词是 nopath，但"createPath 连续返空"
     * 与"根本没人来建路"（行为层冷却把请求掐了）是两种病。所有建路殊途同归
     * 到这一层；返空连击破百就把早退闸门的状态各报一遍，产地当场坐实。
     */
    @Override
    protected Path createPath(
            java.util.Set<BlockPos> targets,
            int regionOffset,
            boolean aboveGround,
            int accuracy,
            float range
    ) {
        Path path = super.createPath(
                targets, regionOffset, aboveGround, accuracy, range
        );
        if (path != null) {
            nullStreak = 0;
            nullConfessed = false;
            return path;
        }
        if (++nullStreak >= NULL_CONFESS_TICKS && !nullConfessed) {
            nullConfessed = true;
            com.mojang.logging.LogUtils.getLogger().warn(
                    "[maid-pathing] null-path: onGround={} inLiquid={} "
                            + "passenger={} targets={} at ({}, {}, {})",
                    this.mob.onGround(),
                    this.isInLiquid(),
                    this.mob.isPassenger(),
                    targets,
                    String.format("%.2f", this.mob.getX()),
                    String.format("%.2f", this.mob.getY()),
                    String.format("%.2f", this.mob.getZ())
            );
        }
        return null;
    }

    @Override
    protected PathFinder createPathFinder(int range) {
        this.nodeEvaluator = new SafeFootingNodeEvaluator();
        this.nodeEvaluator.setCanOpenDoors(true);
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanFloat(true);
        return new MaidWrappedPathFinder(this.nodeEvaluator, range);
    }

    /**
     * 原版 tick 的骨架只保留计时与延迟重铺，跟随与转向全部交给分段执行器
     * ——原版那套（followThePath 的松散判到、canCutCorner 的斜切、滞空里
     * 每 tick 听路标的空中转向）一概不再运行。
     */
    @Override
    public void tick() {
        ++this.tick;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }
        pathwalk.run();
    }

    /** 走速档位（构造后由 moveTo 的调用方每次传入），执行器配速用。 */
    double pace() {
        return this.speedModifier;
    }

    /** 最近一次搜索里起点发出的邻居数（-1 表示还没搜过）。 */
    public int startNeighbours() {
        return this.nodeEvaluator instanceof SafeFootingNodeEvaluator safe
                ? safe.startNeighbours()
                : -1;
    }

    /** 执行器的行车记录，测试断言卡住时当供词打出来。 */
    public String pathwalkDiary() {
        return pathwalk.diary();
    }

    /** 执行器这一 tick 走的分支，读数带逐行印它。 */
    public String pathwalkNote() {
        return pathwalk.note();
    }

    /**
     * 该升级就升级，已经是就什么都不做。
     *
     * <p>按**精确类**判断而不是 instanceof：这个类自己继承
     * {@code MaidPathNavigation}，instanceof 会把自己也当成要换的对象。
     */
    public static void upgrade(EntityMaid maid) {
        PathNavigation current = maid.getNavigation();
        if (current == null || current instanceof SureFootedNavigation) {
            return;
        }
        if (current.getClass() == MaidPathNavigation.class) {
            maid.setNavigation(new SureFootedNavigation(maid, maid.level()));
            return;
        }
        // 换过去的不止一套。宿主游泳时会装上水下寻路，而**那一套不查立足
        // 点**；上岸之后它并不总换回来，于是她拿着一把只认水的尺在陆地上
        // 走，走到沿边就下去了（实测供词：活板门檐第四趟摔下去，
        // diary=n/a(导航是 MaidUnderWaterPathNavigation)——那不是没有供词，
        // 是导航压根不是我们的）。
        //
        // 判据只读**世界的事实**：脚踩着地、身上没水，那就是走路，走路归
        // 我们管。还在水里的时候不抢——那种时候水下那套本来就是对的。
        if (!maid.isInWater() && maid.onGround()) {
            maid.setNavigation(new SureFootedNavigation(maid, maid.level()));
        }
    }
}
