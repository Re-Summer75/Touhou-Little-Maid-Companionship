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

    /** 自有寻路引擎（voxel/）：锚点图+合同路径。搜到完整路时优先执行；
     *  搜不到的形态（贴边带、穿角——后续阶段并入）回落旧图兜底。玩家定
     *  案"不需要任何原版的寻路内容"——旧图是过渡期的拐杖，不是归宿。 */
    private final com.laixia.maidintelligence.feature.behavior.tlm.pathing
            .voxel.StrideWalker strideWalker;

    /** 实机验伪边的账本：跟着这只女仆，执行侧记账、图侧避让。 */
    private final com.laixia.maidintelligence.feature.behavior.tlm.pathing
            .voxel.EdgeVeto edgeVeto =
            new com.laixia.maidintelligence.feature.behavior.tlm.pathing
                    .voxel.EdgeVeto();

    public SureFootedNavigation(Mob mob, Level level) {
        super(mob, level);
        this.pathwalk = new SegmentedPathwalk(mob, this);
        this.strideWalker = new com.laixia.maidintelligence.feature.behavior
                .tlm.pathing.voxel.StrideWalker(mob, pathwalk.flight(),
                        edgeVeto);
        // 搜索预算放大四倍（原版给袭击者的同一开关）。三维跳跃图的分支
        // 因子比原版平面图大得多，默认预算在大障碍绕行里更容易耗尽、退
        // 化成部分路径。老实记账：这一刀是在追一桩"缺角进不去"的悬案时
        // 下的，当时怀疑预算不足——后来方块名矩阵拍出真凶是邻场越界的
        // 屏障墙（棋盘领地贯穿），与预算无关。留着它是因为余量本身站得
        // 住，不是因为它治好过什么。
        this.setMaxVisitedNodesMultiplier(4.0F);
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
        edgeVeto.clock(this.mob.tickCount);
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }
        // 自由模式只有自有引擎——旧图在自由模式里已经退役（玩家定案：
        // 整个系统替换，不要兜底；**只在自由模式生效**——本体的其他任
        // 务模式不归我们管，照旧走宿主的链）。
        if (freedomMode()) {
            strideWalker.run();
            return;
        }
        pathwalk.run();
    }

    /** 她在不在自由模式：自有引擎的作用域就到这条边界为止。 */
    private boolean freedomMode() {
        return this.mob instanceof EntityMaid maid
                && com.laixia.maidintelligence.feature.behavior.tlm.freedom
                        .FreedomMaidTask.UID.equals(maid.getTask().getUid());
    }

    /**
     * 移动入口：**只有自有引擎**（玩家定案：整个系统替换，不要兜底）。
     * 部分路径也接——走到诚实的可达前沿本就是合理行为；起点格解析不出
     * 锚点就在贴身邻域找一个；都没有才是真无路，那就是无路，不会有旧图
     * 替它圆场。
     */
    /** 上一单的目标与时刻：同目标短窗内不重铺。 */
    private double lastGoalX;
    private double lastGoalY;
    private double lastGoalZ;
    private int lastPlanTick = -100;

    /** 上一次"铺不出路"的时刻：无路也要有冷却。找不到路的女仆若每
     *  tick 重试，几只就能把全服的规划名额吃干净（抬高石实测：别人排
     *  不上队、原地钉住不动），何况同一 tick 里世界也没变——答案不会
     *  变，白算一遍。 */
    private int lastFailTick = -100;

    /** 下单流水：谁来问过路、问了多少次。 */
    private int orderCount;
    private int lastOrderTick = -100;

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        orderCount++;
        lastOrderTick = this.tick;
        if (!freedomMode()) {
            strideWalker.follow(null);
            return super.moveTo(x, y, z, speed);
        }
        // 同一单短窗内不重铺：跟随的 sink 每 tick 把同一个目标塞回来，
        // 每 tick 重跑 A* 不止是白烧预算——执行侧的贴沿窗、回锚闹钟都
        // 需要连续的语境，每 tick 换一条"新路"等于把计时永远清在第一
        // 秒（倒 T 实测五场齐凝在 approach，黄线在脚下也迈不出去）。
        // 节流窗带实体相位（错峰）：几十只女仆不在同一 tick 齐跑 A*。
        if (strideWalker.path() != null && strideWalker.path().alive()
                && this.tick - lastPlanTick < 10 + (this.mob.getId() % 5)
                && Math.abs(x - lastGoalX) + Math.abs(y - lastGoalY)
                        + Math.abs(z - lastGoalZ) < 1.0D) {
            return true;
        }
        // 无路冷却：刚铺失败过就先别重试（同 tick 世界没变，答案不会
        // 变），顺带不去抢别人的规划名额。
        if (this.tick - lastFailTick < 10) {
            return false;
        }
        // 全局预算：这一 tick 的规划额度被别人用完了就先沿旧路走，别把
        // 服务器 tick 顶出尖刺——下一 tick 再排。
        if (!com.laixia.maidintelligence.feature.behavior.tlm.pathing
                .voxel.VoxelAstar.tryReserve(this.level.getGameTime())) {
            return strideWalker.path() != null
                    && strideWalker.path().alive();
        }
        lastGoalX = x;
        lastGoalY = y;
        lastGoalZ = z;
        lastPlanTick = this.tick;
        var start = anchorNearby();
        if (start == null) {
            strideWalker.follow(null);
            lastFailTick = this.tick;
            return false;
        }
        var web = new com.laixia.maidintelligence.feature.behavior.tlm
                .pathing.voxel.StrideWeb(this.level,
                        this.mob.getBbWidth(), this.mob.getBbHeight(),
                        edgeVeto);
        var path = com.laixia.maidintelligence.feature.behavior.tlm
                .pathing.voxel.VoxelAstar.find(web, start,
                        // 到站半径按**踩上那一格**给（半格出头）。0.75 是
                        // 老引擎年代的宽口径：她会停在离站点四分之三格外
                        // 宣布到达，看着就是"差一步不走了"，重铺又立刻再
                        // 说一次到达，静止被记成原地打转（抬高石往返实测
                        // 四红，供词 orders=159 lastFail=499t——单下得好好
                        // 的，是她自己认为到了）。目标格站不住人时不会有
                        // 锚落在这半格内，那就走诚实前沿，仍旧到得了近旁。
                        new net.minecraft.world.phys.Vec3(x, y, z), 0.45D);
        if (path == null) {
            strideWalker.follow(null);
            lastFailTick = this.tick;
            return false;
        }
        strideWalker.follow(path);
        // 影子路径：原版脑子的移动 sink 每 tick 盯着 getPath() 判活，喂
        // 不到东西它就当寻路失败、调 stop（顺手清掉自有路径）、二十 tick
        // 冷却重试——实测抽搐成"走一步停一秒"。投影一份只读的原版 Path
        // 让它安心，行为全归自有引擎，这份影子谁也不执行。
        this.path = shadowOf(path);
        return true;
    }

    /** 自有路径的原版投影（只为让宿主与原版的问询有话可答）。 */
    private Path shadowOf(com.laixia.maidintelligence.feature.behavior.tlm
            .pathing.voxel.VoxelPath vp) {
        java.util.List<net.minecraft.world.level.pathfinder.Node> nodes =
                new java.util.ArrayList<>();
        for (int i = 0; i < vp.length(); i++) {
            BlockPos cell = vp.anchorAt(i).cell();
            nodes.add(new net.minecraft.world.level.pathfinder.Node(
                    cell.getX(), cell.getY(), cell.getZ()));
        }
        BlockPos end = vp.end().cell();
        return new Path(nodes, end, vp.reaches());
    }

    /**
     * 原版脑子的移动 sink 走的是 createPath→moveTo(Path) 这条链，不是坐
     * 标版——第一次全量切换漏拦了它，请求两头落空，她原地呆立（一轮二百
     * 零四红，全部 note=-）。自由模式下把 Path 的原始目标拆出来转进自有
     * 引擎，原版路径本体弃用。
     */
    @Override
    public boolean moveTo(Path path, double speed) {
        if (!freedomMode()) {
            return super.moveTo(path, speed);
        }
        if (path == null) {
            strideWalker.follow(null);
            lastFailTick = this.tick;
            return false;
        }
        BlockPos target = path.getTarget();
        return moveTo(target.getX() + 0.5D, target.getY(),
                target.getZ() + 0.5D, speed);
    }

    @Override
    public boolean moveTo(net.minecraft.world.entity.Entity target,
            double speed) {
        if (!freedomMode()) {
            return super.moveTo(target, speed);
        }
        return moveTo(target.getX(), target.getY(), target.getZ(), speed);
    }

    /** 她此刻的立足锚：先自己的格，再贴身邻格（含上下一层）。 */
    private com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
            .Anchor anchorNearby() {
        BlockPos here = this.mob.blockPosition();
        var mine = com.laixia.maidintelligence.feature.behavior.tlm.pathing
                .voxel.AnchorResolver.resolve(this.level, here);
        if (mine != null) {
            return mine;
        }
        for (int dy = 0; dy >= -1; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    var near = com.laixia.maidintelligence.feature.behavior
                            .tlm.pathing.voxel.AnchorResolver.resolve(
                                    this.level, here.offset(dx, dy, dz));
                    if (near != null) {
                        return near;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void stop() {
        strideWalker.follow(null);
        super.stop();
    }

    @Override
    public boolean isDone() {
        if (!freedomMode()) {
            return super.isDone();
        }
        var path = strideWalker.path();
        return path == null || !path.alive();
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

    /** 实时面板要画规划出的锚点路径。 */
    public com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
            .VoxelPath voxelPath() {
        return strideWalker.path();
    }

    /** 执行器的行车记录，测试断言卡住时当供词打出来。 */
    public String pathwalkDiary() {
        if (freedomMode()) {
            var path = strideWalker.path();
            return "voxel note=" + strideWalker.note()
                    + " path=" + (path == null ? "null"
                            : path.cursor() + "/" + path.length()
                                    + (path.reaches() ? "" : " part")
                                    + " next=" + (path.alive()
                                            ? path.next().at() : "-"))
                    // 下单流水：分得清"没人来下单"与"下了单铺不出路"。
                    + " orders=" + orderCount
                    + " lastOrder=" + (this.tick - lastOrderTick)
                    + "t lastPlan=" + (this.tick - lastPlanTick)
                    + "t lastFail=" + (this.tick - lastFailTick)
                    + "t lastGoal=(" + String.format("%.1f,%.1f,%.1f",
                            lastGoalX, lastGoalY, lastGoalZ) + ")";
        }
        return pathwalk.diary();
    }

    /** 执行器这一 tick 走的分支，读数带逐行印它。 */
    public String pathwalkNote() {
        if (freedomMode()) {
            return strideWalker.note();
        }
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
