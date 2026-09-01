package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidPathNavigation;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.path.MaidWrappedPathFinder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableSet;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.host
        .HostBridge;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel.Anchor;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel.PathClock;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel.StrideWeb;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel.VoxelAstar;
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

    /** 自有寻路引擎（voxel/）：锚点图+合同路径；自由模式全权执行（玩
     *  家定案"不需要任何原版的寻路内容"），挤缝形态由多边形层兜底。 */
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
        // 搜索预算放大四倍（原版给袭击者的同一开关）：三维跳跃图分支因
        // 子大，默认预算易耗尽退化成部分路径。老实记账：当年为"缺角进
        // 不去"悬案所下，真凶后来查明是邻场越界屏障墙——留它是因为余
        // 量本身站得住，不是它治好过什么。
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
        // 自由模式的**探路也归自有引擎**：sink 拿答案决定擦不擦单，原
        // 版 A* 眼里杆桥是绝路（杆桥案 t43 擦单）。影子只答问询。
        if (freedomMode()) {
            return HostBridge.toward(this.mob, target, edgeVeto);
        }
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
        // 本体 A* 的账也在这层记（坐标版、Path 版、延迟重铺殊途同归），
        // 与体素引擎同一本账同一单位——群体基准的对照组才有数可比。
        long planAt = System.nanoTime();
        Path path = super.createPath(
                targets, regionOffset, aboveGround, accuracy, range
        );
        PathClock.plan(this.mob.getUUID(), System.nanoTime() - planAt);
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
        // 自由模式只走自有引擎（玩家定案）；计时挂边界，两链同一把尺。
        long clock = System.nanoTime();
        if (freedomMode()) {
            strideWalker.run();
            // 路报废影子也撤，sink 才会重问（沉门板案 147 tick 空窗）。
            if (strideWalker.path() == null) {
                this.path = null;
            }
        } else {
            pathwalk.run();
        }
        PathClock.walk(this.mob.getUUID(), System.nanoTime() - clock);
    }

    /** 她在不在自由模式：自有引擎的作用域就到这条边界为止。 */
    private boolean freedomMode() {
        return this.mob instanceof EntityMaid maid
                && com.laixia.maidintelligence.feature.behavior.tlm.freedom
                        .FreedomMaidTask.UID.equals(maid.getTask().getUid());
    }

    /**
     * 移动入口：**只有自有引擎**（玩家定案：整个系统替换，不要兜底）。
     * 部分路径也接——走到诚实的可达前沿本就是合理行为；都没有才是真
     * 无路，不会有旧图替它圆场。
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

    /** 下单流水：谁来问过路、问了多少次；配额拒单也单独记。 */
    private int orderCount;
    private int lastOrderTick = -100;
    private int quotaDenied;
    private int lastDenyTick = -100;

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        orderCount++;
        lastOrderTick = this.tick;
        if (!freedomMode()) {
            strideWalker.follow(null);
            return super.moveTo(x, y, z, speed);
        }
        // **倍率要自己记**：speedModifier 只有 super.moveTo 赋值，自有
        // 分支不走那条路（台账 §6 三跤）。记在最前——各条早退都可能不
        // 重铺，可倍率是这一单的属性，跟铺不铺路无关。
        this.speedModifier = speed;
        // 同一单短窗内不重铺（sink 每 tick 塞回同一目标；窗带相位错
        // 峰）。例外：**末段而单子已漂**（>0.3 格）窗缩到三 tick——走
        // 死旧路再等长窗就是实机点名的"忽然停顿"（台账 §11）。
        double drift = Math.abs(x - lastGoalX) + Math.abs(y - lastGoalY)
                + Math.abs(z - lastGoalZ);
        var held = strideWalker.path();
        boolean lastLeg = held != null && held.alive()
                && held.cursor() >= held.length() - 1;
        if (held != null && held.alive()
                && this.tick - lastPlanTick
                        < (lastLeg && drift > 0.3D
                                ? 3 : 10 + (this.mob.getId() % 5))
                && drift < 1.0D) {
            return true;
        }
        // 路走完了也要节流，但长窗只罚"无事可做"的（sw168 到站空转）。
        // 还在赶路的（全路而人离单子 >1.5 格、半截路而**前沿还没走到**）
        // 只留三 tick 喘息（杆桥"差 5.7 格"与跟随顿挫都是长窗罚出来
        // 的）。半截路**站上前沿之后**回长窗：单子够不着、能走的都走完
        // 了，三 tick 一遍重走末段会把她一点点蹭过沿口（四格缺口案：她
        // 不是跳下去的，是一轮一轮蹭下去的）。
        boolean pressOn = held != null && !held.reaches()
                ? Math.hypot(held.end().at().x - this.mob.getX(),
                        held.end().at().z - this.mob.getZ()) > 1.0D
                : Math.hypot(x - this.mob.getX(),
                        z - this.mob.getZ()) > 1.5D;
        if ((held == null || !held.alive())
                && this.tick - lastPlanTick
                        < (pressOn ? 3 : 10 + (this.mob.getId() % 5))
                && drift < 1.0D) {
            return false;
        }
        // 无路冷却：刚铺失败过就先别重试（同 tick 世界没变）。
        if (this.tick - lastFailTick < 10) {
            return false;
        }
        // 全局预算：拒单只许落在"手里还有活路"的身上（沿旧路降级）。
        // 手里没路的放行——被拒的首单 return false 会让 sink 擦单进冷
        // 却，和 40 tick 写单周期相位锁死，整场饿死（杆桥四轮连红案：
        // 并行副本的探路不占名额却累时间账，烧穿 15ms 后执行侧全拒；
        // 此前的"连拒二十次强闯"保底要 800 tick，比场时还长）。
        boolean afloat = strideWalker.path() != null
                && strideWalker.path().alive();
        if (!VoxelAstar.tryReserve(this.level.getGameTime()) && afloat) {
            quotaDenied++;
            lastDenyTick = this.tick;
            return true;
        }
        lastGoalX = x;
        lastGoalY = y;
        lastGoalZ = z;
        lastPlanTick = this.tick;
        // 图缓存与探路共享（弹道账每单清零=封圈案 93×150ms 真凶）。
        var web = HostBridge.webOf(this.mob, edgeVeto);
        var start = anchorNearby(web);
        if (start == null) {
            strideWalker.follow(null);
            lastFailTick = this.tick;
            return false;
        }
        long planAt = System.nanoTime();
        var path = VoxelAstar.find(web, start,
                        // 新织的图（现仿真弹道，贵）才吃预算档；缓存图
                        // 上纯搜索全额——绕行一次到位不退化成蠕动。
                        new net.minecraft.world.phys.Vec3(x, y, z), 0.45D,
                        HostBridge.freshlyWoven()
                                ? VoxelAstar.headroom(
                                        this.level.getGameTime())
                                : 0L);
        // 计时记这一层：VoxelAstar 是共用静态入口，不知道谁在铺路，账会
        // 串到别人头上（实测对照组记到十九次规划）。
        PathClock.plan(this.mob.getUUID(), System.nanoTime() - planAt);
        if (path == null) {
            // 锚点图无路时问一次**多边形层**（挤缝一族：柱旁 0.075 的
            // 缝在它眼里是正经的地）。只答纯走路；答卷过同一道验收——
            // 终点不贴目标（水平/竖直一格内）就当无路：它给过"跳崖下坑
            // 终点差八格"还标 reaches 的路（四格缺口案 t21）。兜底的职
            // 责是缝，不是崖。兜底也记账：雕刻贵过搜索，别藏在 tick 里。
            long meshAt = System.nanoTime();
            var lane = com.laixia.maidintelligence.feature.behavior
                    .tlm.pathing.mesh.MeshWalk.plan(this.level, this.mob,
                            new net.minecraft.world.phys.Vec3(x, y, z));
            long meshSpent = System.nanoTime() - meshAt;
            PathClock.plan(this.mob.getUUID(), meshSpent);
            VoxelAstar.charge(meshSpent);
            if (lane != null
                    && Math.hypot(lane.end().at().x - x,
                            lane.end().at().z - z) <= 1.0D
                    && Math.abs(lane.end().at().y - y) <= 1.0D) {
                strideWalker.follow(lane);
                this.path = HostBridge.shadowOf(lane,
                        BlockPos.containing(x, y, z));
                return true;
            }
            strideWalker.follow(null);
            lastFailTick = this.tick;
            return false;
        }
        strideWalker.follow(path);
        // 影子路径：原版移动 sink 盯着 getPath() 判活，喂不到就 stop ＋
        // 二十 tick 冷却（实测"走一步停一秒"）。投影一份只读 Path 安抚
        // 它，行为全归自有引擎，影子谁也不执行；target 传**原始目的地**
        // （sink 会拿 getTarget 再下单，传前沿=把她的单换成前沿）。
        this.path = HostBridge.shadowOf(path, BlockPos.containing(x, y, z));
        return true;
    }

    /**
     * 原版移动 sink 走 createPath→moveTo(Path) 链，不是坐标版（第一次
     * 全量切换漏拦它，一轮二百零四红）。自由模式拆出 Path 的原始目标转
     * 进自有引擎，原版路径本体弃用。
     */
    @Override
    public boolean moveTo(Path path, double speed) {
        if (!freedomMode()) {
            return super.moveTo(path, speed);
        }
        if (path == null) {
            strideWalker.follow(null);
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

    /** 诊断口径：只问"她站在哪"，不问那儿走不走得动。 */
    private Anchor anchorNearby() {
        return anchorNearby(null);
    }

    /** 她此刻的立足锚：解析口径全在 {@code AnchorResolver.nearby} 里。 */
    private Anchor anchorNearby(StrideWeb web) {
        return com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
                .AnchorResolver.nearby(this.level, this.mob.blockPosition(),
                        this.mob.getBoundingBox(), this.mob.getY(), web);
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
                    + " deny=" + quotaDenied + "/"
                    + (this.tick - lastDenyTick)
                    + "t lastOrder=" + (this.tick - lastOrderTick)
                    + "t lastPlan=" + (this.tick - lastPlanTick)
                    + "t lastFail=" + (this.tick - lastFailTick)
                    + "t lastGoal=(" + String.format("%.1f,%.1f,%.1f",
                            lastGoalX, lastGoalY, lastGoalZ) + ")"
                    // 卡死现场的三件套：飞行锁还在不在（锁着就不走路）、
                    // 脚下解不解得出锚（解不出就无从起步）、当场重铺能不
                    // 能出路（能出却没在走，问题就在执行侧）。
                    + " at=" + String.format("(%.2f,%.2f)",
                            this.mob.getX(), this.mob.getZ())
                    + " lock=" + pathwalk.flight().locked()
                    + " underfoot=" + describeAnchor(anchorNearby())
                    + " replan=" + describeReplan();
        }
        return pathwalk.diary();
    }

    /** 供词用：把锚点写成一行，没有就说没有。 */
    private static String describeAnchor(Anchor anchor) {
        return anchor == null ? "none"
                : String.format("(%.2f,%.2f,%.2f)b%.2f%s",
                        anchor.at().x, anchor.at().y, anchor.at().z,
                        anchor.breadth(), anchor.kind());
    }

    /** 供词用：当场按上一次的目标重铺一遍，看图给不给得出路。 */
    private String describeReplan() {
        // 供词要跟真铺路**同一口径**：起点解析也得带上边供给，否则探针
        // 说得出路、实际铺不出（或反过来），供词就成了误导。
        var web = new StrideWeb(this.level, this.mob.getBbWidth(),
                this.mob.getBbHeight(), edgeVeto);
        var start = anchorNearby(web);
        if (start == null) {
            return "no-start";
        }
        var probe = VoxelAstar.find(web, start,
                new net.minecraft.world.phys.Vec3(
                        lastGoalX, lastGoalY, lastGoalZ), 0.45D);
        if (probe == null) {
            return "null";
        }
        return probe.length() + "step"
                + (probe.reaches() ? "-reach" : "-frontier");
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
