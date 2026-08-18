package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidPathNavigation;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.path.MaidWrappedPathFinder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

/**
 * 本体的地面导航，换上会查立足点的节点评估。
 *
 * <p>除评估器外与 {@code MaidPathNavigation} 一字不差——三个能力开关照抄本体，
 * 少一个都是行为回归（开门、过门、浮水）。
 *
 * <p>接线走 {@code EntityMaid.setNavigation}（本体公开的接口，游泳管理器换水陆
 * 导航用的就是它），在环境钩子上做**升级守卫**：发现她拿着的是裸的
 * {@code MaidPathNavigation} 就换成这一个。本体的 {@code MaidNavigationManager}
 * 出水或重置时会把存着的裸实例装回去，下一 tick 这里再升级——一 tick 的窗口，
 * 不与它互抢；水下导航类型不同，不受影响。
 */
public final class SureFootedNavigation extends MaidPathNavigation {
    public SureFootedNavigation(Mob mob, Level level) {
        super(mob, level);
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
     * 跳跃连线的执行侧：走到缺口边上，按落点距离配好推力，起跳。
     *
     * <p>评估器只负责在图里连出"跨一到三格缺口"的边（{@code SafeFootingNodeEvaluator}
     * 的 getNeighbors），A* 据代价选不选它；选了之后路径里就有一段两到四格远的
     * 节点，而原版的路径跟随只会朝着它走——走到边上一脚踩空。原版的自动跳只在
     * **撞上台阶**时触发，缺口没有可撞的东西，所以这一下要自己起。
     *
     * <p>触发条件收得很紧：下一个节点在跳跃射程内、同一层、且**脚前方半格站不
     * 住人**（已经站在崖边）。跳早了够不着，跳晚了已经掉下去了。起跳前还要把
     * 这段边重验成"真的跳跃边"（见 maybeJumpAGap），跳出去之后落点锁死到落地
     * （见 steerTheLeap）——两道保险都是给"走目标半路会变"的差事上的。
     */
    @Override
    public void tick() {
        super.tick();
        if (leapAim != null) {
            steerTheLeap();
            return;
        }
        maybeJumpAGap();
        if (leapAim == null) {
            maybeHopOffTheLip();
        }
        if (leapAim == null) {
            maybeJumpAStep();
            maybeWatchHerStep();
        }
    }

    /**
     * 唇沿自救：站在无心之柱的边沿上，一记小跳把最后一格补完。
     *
     * <p>往返压测钓出来的摔法：上跳落深了半格，她的方块坐标已经进了缺口柱
     * ——脚下格与再下一格都盖不住格心，人靠邻格的唇沿托着。从这格看下一
     * 节点只隔一格，跳跃机构按"相邻格是走路的事"让路，可走路要横越缺口柱
     * 的无支撑段，移动控制会把她推离支撑沿坠下去。站姿本身已经非法，救法
     * 不是走是跳：朝下一节点（一格内的真地板）直写一记小跳，落点锁定接管。
     */
    private void maybeHopOffTheLip() {
        if (isDone() || path == null || !mob.onGround()
                || mob.isInWater()) {
            return;
        }
        // 真唇沿：脚下三格内什么都盖不住格心（缺口柱一路空到底）。只查两格
        // 会把正常上下台阶的过渡帧误判进来——那一帧脚下两格是空的，但第三
        // 格就是台阶的地板；在台阶口平白起跳，弹进缺口，四条台阶钉齐红过。
        BlockPos feetCell = mob.blockPosition();
        if (SafeFootingNodeEvaluator.coversCenter(mob.level(), feetCell)
                || SafeFootingNodeEvaluator.coversCenter(
                        mob.level(), feetCell.below())
                || SafeFootingNodeEvaluator.coversCenter(
                        mob.level(), feetCell.below(2))) {
            return;
        }
        BlockPos next = path.getNextNodePos();
        int dy = next.getY() - feetCell.getY();
        if (dy < -1 || dy > 1) {
            return;
        }
        if (!SafeFootingNodeEvaluator.coversCenter(
                mob.level(), next.below())) {
            return;
        }
        double toX = next.getX() + 0.5D - mob.getX();
        double toZ = next.getZ() + 0.5D - mob.getZ();
        double flat = Math.hypot(toX, toZ);
        // 只救近邻：远节点是正经跳跃机构的事。下限放得很低——站在唇沿上
        // 连贴身的节点也走不过去（盒角救济会把寻路起点甩到缺口对岸，节点
        // 与意图两头拉锯，她被钉在唇上原地打转），一跳解结。
        if (flat < 0.35D || flat > 2.0D) {
            return;
        }
        double hop = 0.22D;
        mob.setDeltaMovement(
                toX / flat * hop, JUMP_RISE, toZ / flat * hop
        );
        leapAim = new Vec3(
                next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D
        );
        leapSpeed = hop;
        leapDirX = toX / flat;
        leapDirZ = toZ / flat;
        leapAloft = false;
        leapTick = mob.tickCount;
    }

    /** 崖边收步的速度上限；低于它的移动本来就冲不出去。 */
    private static final double EDGE_TROT = 0.13D;

    /** 下坡入口的速度上限：滞空漂移正好落在本格内，不替 AI 多走半格。 */
    private static final double DOWNHILL_TROT = 0.2D;

    /**
     * 崖边收步：脚前方将踏进两格以上的落差、身上又带着冲劲——先问一句这是
     * 不是**正走向一次要起的跳**。
     *
     * <p>是助跑（下个节点就是崖对面一条验证过的跳跃线、动量也对着它）就只把
     * 速度收到走路量级放行，起跳机构马上接手。**不是**——斜切拐角、终点滑行、
     * 转身把动量甩向崖外、被谁推了一把——就把这步**刹死并弃路**：移动控制每
     * tick 都在往前推，光收速挡不住她一点点蠕出去；弃路后下一 tick 从站定的
     * 脚下重铺，新路径从近处节点起步，不会再斜切。玩家报的"转身直接从转身
     * 方向跳下去"就是收速拦不住的那一类。
     *
     * <p>不伤三样：跳跃起跳是绝对值直写不吃助跑（且起跳那 tick 有落点锁，
     * 走不到这儿）；台阶提前跳朝上一格的方向探，脚前是台阶立面不是落差；
     * 一格台阶下坡探两层，照常走。
     */
    private void maybeWatchHerStep() {
        if (!mob.onGround() || mob.isInWater()) {
            return;
        }
        Vec3 motion = mob.getDeltaMovement();
        double speed = Math.hypot(motion.x, motion.z);
        if (speed <= EDGE_TROT + 0.02D) {
            return;
        }
        double aheadX = mob.getX() + motion.x / speed * 0.8D;
        double aheadZ = mob.getZ() + motion.z / speed * 0.8D;
        BlockPos toe = BlockPos.containing(
                aheadX, mob.getY() - 0.5D, aheadZ
        );
        // 脚前是地板——正常走。脚前**上方**是要登的台阶立面也正常走：悬空
        // 结构的高块下面是空的，探针从脚下半格打出去会穿过它照进虚空，把
        // 一步就能登上去的台阶误判成崖——凹槽（低格夹在两块高一格之间）里
        // 她就被这误判钉死：朝出口迈步就被刹停弃路，永远出不去。
        if (SafeFootingNodeEvaluator.coversCenter(mob.level(), toe)
                || SafeFootingNodeEvaluator.coversCenter(
                        mob.level(), toe.above())) {
            return;
        }
        // 只降一格的台阶：带着冲劲迈下去会滞空两三 tick——刹车只在着地时
        // 运作，这几 tick 是全速漂移，落点比 AI 预期远，随后的"回头拉回"
        // 就是转身摔的祸根。落脚格是尽头（再往前是崖）收到走路量级，稳落
        // 格心；落脚格还有续路也收到小跑——惯性不许替她多走半格。
        if (SafeFootingNodeEvaluator.coversCenter(
                mob.level(), toe.below())) {
            BlockPos onwardToe = BlockPos.containing(
                    mob.getX() + motion.x / speed * 1.8D,
                    mob.getY() - 1.5D,
                    mob.getZ() + motion.z / speed * 1.8D
            );
            boolean deadEnd = !SafeFootingNodeEvaluator.coversCenter(
                            mob.level(), onwardToe)
                    && !SafeFootingNodeEvaluator.coversCenter(
                            mob.level(), onwardToe.below());
            double cap = deadEnd ? EDGE_TROT : DOWNHILL_TROT;
            if (speed > cap) {
                mob.setDeltaMovement(
                        motion.x / speed * cap,
                        motion.y,
                        motion.z / speed * cap
                );
            }
            return;
        }
        if (runningUpToALeap(motion, speed)) {
            mob.setDeltaMovement(
                    motion.x / speed * EDGE_TROT,
                    motion.y,
                    motion.z / speed * EDGE_TROT
            );
            return;
        }
        mob.setDeltaMovement(0.0D, motion.y, 0.0D);
        stop();
    }

    /**
     * 这股朝崖的动量是不是一次要起的跳的助跑：下个节点在崖对面、是条验证过
     * 的跳跃线（正轴、跨度在射程内、落点脚下真地板）、动量也大体对着它。
     */
    private boolean runningUpToALeap(Vec3 motion, double speed) {
        if (isDone() || path == null) {
            return false;
        }
        BlockPos next = path.getNextNodePos();
        BlockPos here = mob.blockPosition();
        int dy = next.getY() - here.getY();
        if (dy < -1 || dy > 1) {
            return false;
        }
        double toX = next.getX() + 0.5D - mob.getX();
        double toZ = next.getZ() + 0.5D - mob.getZ();
        double flat = Math.hypot(toX, toZ);
        if (flat < 1.2D
                || motion.x * toX + motion.z * toZ < 0.7D * speed * flat) {
            return false;
        }
        int dx = next.getX() - here.getX();
        int dz = next.getZ() - here.getZ();
        int span = Math.max(Math.abs(dx), Math.abs(dz));
        boolean cardinal = (dx == 0) ^ (dz == 0);
        boolean spanFits = dy == 1
                ? span <= SafeFootingNodeEvaluator.UP_HOP_MAX_REACH
                : span >= 2
                        && span <= SafeFootingNodeEvaluator.MAX_GAP_SPAN + 1;
        if (!cardinal || !spanFits) {
            return false;
        }
        return SafeFootingNodeEvaluator.coversCenter(
                mob.level(), next.below());
    }

    /** 同层起跳的滞空时长（tick），推力按它配速。 */
    private static final double AIRBORNE_TICKS = 11.0D;

    /**
     * 推力上限：贴边起跳的满冲刺玩家量级。
     *
     * <p>压过 0.46：跨满三格缺口（落点距离四）按配速要 0.497，旧上限把它剪
     * 回刀刃余量——过冲加成全被剪没，往返压测里这一档每十几趟欠冲一次。
     * 拒走线不归它管（四格缺口是评估器不连线），提它不扩射程只厚余量。
     */
    private static final double MAX_LEAP_SPEED = 0.50D;

    /** 起跳后一直没离地就放弃锁定的时限（卡在什么东西上了）。 */
    private static final int LEAP_GIVE_UP_TICKS = 30;

    /**
     * 起跳竖直速度：原版 jumpFromGround 的数。
     *
     * <p>不走 JumpControl——原版跳完有十 tick 的 noJumpDelay，期间再跳竖直
     * 分量直接被吞，只剩水平推力，等于把她平着推下缺口。爬完台阶紧接着到
     * 崖边是野外地形的常态，"偶尔一瞬间速度不够摔下去"的一半就是它。直写
     * 速度，这一跳的竖直和水平都归这里管。
     */
    private static final double JUMP_RISE = 0.42D;

    /** 滞空每 tick 的自然衰减（原版空气阻力），弧线合同按它算。 */
    private static final double AIR_DRAG = 0.91D;

    /** 这一跳锁死的落点；空中转向只认它。null = 没在跳。 */
    private Vec3 leapAim;

    /** 起跳时定的初速，弧线合同的本金。 */
    private double leapSpeed;

    /** 起跳方向（单位向量的水平分量），判断落点还在不在前方用。 */
    private double leapDirX;
    private double leapDirZ;

    /** 起跳之后真的离过地没有——落地清锁要靠它区分"还没起来"。 */
    private boolean leapAloft;

    /** 起跳那一刻的 tickCount，配合放弃时限。 */
    private int leapTick;

    private void maybeJumpAGap() {
        if (isDone() || path == null || !mob.onGround()) {
            return;
        }
        BlockPos next = path.getNextNodePos();
        BlockPos here = mob.blockPosition();
        int dy = next.getY() - here.getY();
        double toX = next.getX() + 0.5D - mob.getX();
        double toZ = next.getZ() + 0.5D - mob.getZ();
        double flat = Math.sqrt(toX * toX + toZ * toZ);
        // 相邻节点归普通走路管，远过射程的不是我们连出来的边。射程按落差分：
        // 上一格只有起跳后头七八 tick 是高的，射程短；下一格滞空更久，射程长。
        if (flat < 1.2D) {
            return;
        }
        if (dy == 0 ? flat > 4.6D
                : dy == 1 ? flat > 3.6D
                : dy == -1 ? flat > 4.8D
                : true) {
            return;
        }
        // 脚前方半格没有贴脚面的地板 = 已经站在崖边，正对着要跨的缺口。用的
        // 是评估器同一把尺（盖得住格心的顶面有多高）：竖板（开着的活板门）
        // 盖不住格心不算地板；顶面低出脚面半格以上的（沉在缺口里的关门板）
        // 是低洼，照样算崖边——从上面飞过去是规划连好的线，起跳就得认账。
        double aheadX = mob.getX() + toX / flat * 0.6D;
        double aheadZ = mob.getZ() + toZ / flat * 0.6D;
        BlockPos aheadFloor = BlockPos.containing(
                aheadX, mob.getY() - 0.5D, aheadZ
        );
        if (SafeFootingNodeEvaluator.coveringTopAt(mob.level(), aheadFloor)
                >= mob.getY() - 0.6D) {
            return;
        }
        // 起跳前把"这真是我们连的跳跃边"重验一遍，几何像不能当数。跟随这类
        // 每 tick 重定目标的差事会在半路换路径，换的那一帧"下个节点"可能是
        // 斜向的、绕角的、或者根本没有落点的远节点——照着它跳就是跳向空中。
        // 验三样：正轴对齐、中间每格都是真缺口、落点脚下是真地板。
        int dx = next.getX() - here.getX();
        int dz = next.getZ() - here.getZ();
        int span = Math.max(Math.abs(dx), Math.abs(dz));
        // 相邻格永远是走路的事，跳跃机构静默让路。这条不能并进下面的异常
        // 停：站在沉地板（关着的活板门）上时，脚在格子半腰，到 +1 相邻格
        // 的格心能量出一点四格"远"，崖边探针又从脚下半格穿到桥底的空气里
        // ——假崖边加跨度一，一旦按异常停路，她就被钉在门板上一步步蠕。
        if (span <= 1) {
            return;
        }
        boolean cardinal = (dx == 0) ^ (dz == 0);
        boolean spanFits = dy == 1
                ? span <= SafeFootingNodeEvaluator.UP_HOP_MAX_REACH
                : span <= SafeFootingNodeEvaluator.MAX_GAP_SPAN + 1;
        if (!cardinal || !spanFits) {
            // 站在崖边、下个节点又远又不是跳跃边——继续走就是走下去。停下，
            // 让上层下一 tick 从脚下重新铺路。
            stop();
            return;
        }
        int sx = Integer.signum(dx);
        int sz = Integer.signum(dz);
        for (int i = 1; i < span; i++) {
            BlockPos cell = here.offset(sx * i, 0, sz * i);
            // 飞行层上有墙体不是缺口；格下的立足物按高度分——顶面贴着脚面
            // （半格内）是平路，不配推力；低出半格以上是低洼，弧线从上面过
            // 是合法跑酷。与评估器对"缺口"的定义一字不差。
            if (SafeFootingNodeEvaluator.coversCenter(mob.level(), cell)
                    || SafeFootingNodeEvaluator.coveringTopAt(
                                    mob.level(), cell.below())
                            >= here.getY() - 0.6D) {
                return;
            }
        }
        if (!SafeFootingNodeEvaluator.coversCenter(
                mob.level(), next.below())) {
            stop();
            return;
        }
        // 起跳推力按落点距离配速。同层与下一格稍偏过冲（落点再往前仍是可走
        // 地面，过冲落得更稳，欠冲落进缺口）；**上一格不过冲**——上跳的落点
        // 常是一格宽的孤台，过冲半格就是从另一头下去，它的余量在竖直弧顶，
        // 水平瞄格心。滞空时长随落差变：上一格必须在弧线还高着的头七八 tick
        // 内赶到，配快步；下一格多飘两三 tick，配缓步——缓步兼防飞过窄落点。
        // 速度直写、竖直不走 JumpControl（见 JUMP_RISE），滞空期间按弧线合同
        // 逐 tick 核账（见 restoreTheArc）——走速、战斗距离、任何别的账都
        // 不经过这里。
        // 跨三的同层/下行跳把过冲再加一成：同层弧线第 ~12 tick 脚面就回落
        // 到唇沿高度，跨三的时间余量只剩一两 tick，路径重算的相位抖动偶尔
        // 吃掉它——二十轮压测里跨三场景各摔过一次（5%），跨一跨二从不失手。
        // 早一 tick 到，落点深入格内半格，多出的热度落地收腿吸掉。
        double airborneTicks = dy == 1 ? 7.5D
                : dy == -1 ? 13.5D
                : AIRBORNE_TICKS;
        double overshoot = dy == 1 ? 1.0D
                : span >= 3 ? 1.35D
                : 1.25D;
        double leap = Math.min(
                MAX_LEAP_SPEED,
                Math.max(0.2D, flat / airborneTicks * overshoot)
        );
        mob.setDeltaMovement(
                toX / flat * leap,
                JUMP_RISE,
                toZ / flat * leap
        );
        leapAim = new Vec3(
                next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D
        );
        leapSpeed = leap;
        leapDirX = toX / flat;
        leapDirZ = toZ / flat;
        leapAloft = false;
        leapTick = mob.tickCount;
    }

    /**
     * 滞空期间落点锁死。
     *
     * <p>空中转向每 tick 都在听走目标的——跟随的主人半路飞到另一侧，走目标
     * 一换，空中加速度就把她往新方向拽，本来够着的落点变成够不着，摔进缺口。
     * 跳出去的这十几 tick 里没有别的目标：转向一律指回起跳时锁的落点，落地
     * （或超时没起来）才解锁，让路重新归上层管。
     */
    /** 落地那一 tick 把水平余速收到这个量级——跳是跳，走是走。 */
    private static final double LANDING_TROT = 0.15D;

    private void steerTheLeap() {
        if (!mob.onGround()) {
            leapAloft = true;
            restoreTheArc();
        } else if (leapAloft
                || mob.tickCount - leapTick > LEAP_GIVE_UP_TICKS) {
            // 落地收腿。合同余速不收，落点到终点只剩两三格的场景（桥尾、
            // 窄台）她就带着跳劲冲过目标格，从另一头冲下去——读数带上是
            // 跳全成功、摔在落地之后。收到走路量级，路还长自会重新提速。
            Vec3 landed = mob.getDeltaMovement();
            double trot = Math.hypot(landed.x, landed.z);
            if (trot > LANDING_TROT) {
                mob.setDeltaMovement(
                        landed.x / trot * LANDING_TROT,
                        landed.y,
                        landed.z / trot * LANDING_TROT
                );
            }
            leapAim = null;
            return;
        }
        mob.getMoveControl().setWantedPosition(
                leapAim.x, leapAim.y, leapAim.z, this.speedModifier
        );
    }

    /** 慢于这个速度就不提前跳台阶——没有速度可带，提前就没有价值。 */
    private static final double STEP_WORTH_CARRYING = 0.08D;

    /** 前沿到台阶立面近于这个就不提前跳：原版从零起跳也上得去，别抢。 */
    private static final double STEP_FACE_NEAR = 0.45D;

    /** 前沿到立面远于这个也不跳：升到一格高之前就会撞脸，白跳。 */
    private static final double STEP_FACE_FAR = 1.1D;

    /** 脚下高度的小数部分超过这个就不算整块地板。 */
    private static final double FLUSH_FOOTING = 0.06D;

    /**
     * 上一格的坎，在撞上之前就起跳——战斗撤退里 {@code StepAhead} 的那一课，
     * 下放到走路。
     *
     * <p>原版的顺序是撞停再跳：贴上方块面的那一刻碰撞把水平速度清零，然后从零
     * 起跳，空中加速度只有地面的十分之一，一格坎白吃一格半的路程。玩家不吃这个
     * 亏靠的是在碰到之前就跳，速度带着过去。这里按路径判（战斗那边按航向判，
     * 各自成立）：下个节点恰好高一格、离立面的距离正好、身上带着朝它去的速度。
     *
     * <p>**只提前时机，不加冲量**：水平分量原样带走，竖直给原版起跳的数——这是
     * "不跳过头"的第一道保证。战斗那版是为开阔地追逃调的，下放到走路还得再收
     * 三道口（每一道都是测试里真摔出来的）：
     *
     * <ol>
     *   <li>**整块地板才提前跳**：脚下带小数高度（关着的活板门、台阶顶）时，
     *       起跳时机全对不上——起早了半空撞立面、速度清零掉回原地、条件还在又
     *       起——原地弹跳，最后一跳还带着动量滑出桥尾。交回原版撞停。
     *   <li>**终点前最后一段不提前跳**：带着速度落在终点格上会滑过头，桥尾、
     *       背靠悬崖的窄台，滑过头就是掉下去。慢一步，稳一步。
     *   <li>**起跳窗口按离立面几步算**：太近了原版自己会跳（从零起也上得去），
     *       太远了升到一格高之前就撞脸。
     * </ol>
     *
     * <p>台阶顶必须是真地板（评估器的尺），自己头顶要有起跳空间；差一样就交回
     * 原版的撞停再跳，慢而不险。
     */
    private void maybeJumpAStep() {
        if (isDone() || path == null || !mob.onGround()
                || mob.isInWater()) {
            return;
        }
        if (mob.getY() - Math.floor(mob.getY()) > FLUSH_FOOTING) {
            return;
        }
        if (path.getNextNodeIndex() >= path.getNodeCount() - 1) {
            return;
        }
        BlockPos next = path.getNextNodePos();
        BlockPos here = mob.blockPosition();
        if (next.getY() != here.getY() + 1) {
            return;
        }
        double toX = next.getX() + 0.5D - mob.getX();
        double toZ = next.getZ() + 0.5D - mob.getZ();
        double flat = Math.sqrt(toX * toX + toZ * toZ);
        double face = flat - 0.5D - mob.getBbWidth() / 2.0D;
        if (face < -0.35D || face > STEP_FACE_FAR) {
            return;
        }
        if (!SafeFootingNodeEvaluator.coversCenter(
                mob.level(), next.below())) {
            return;
        }
        BlockPos overhead = here.above(2);
        if (!mob.level().getBlockState(overhead)
                .getCollisionShape(mob.level(), overhead).isEmpty()) {
            return;
        }
        if (face < STEP_FACE_NEAR) {
            // 贴脸登阶：接管原版的撞跳。原版那一跳照单全收当下的水平速度，
            // 而她在凹槽里打转时速度朝向是乱的——一格宽的槽里一跳就出侧沿
            // （玩家实测：下方块后在槽里旋转，接着直接跳出去）。方向由台阶
            // 线给，前速固定给小步，转身转到哪一律不影响这一跳落在哪。
            // **落点也要锁**：之字梯每一步都带转弯，滞空那十几 tick 空中
            // 转向还听路标的，路标已经指向拐角，人就在半空被拽出侧沿——
            // 悬空之字梯实测摔的就是这一下。锁到台阶格心，落地才解锁。
            mob.setDeltaMovement(
                    toX / flat * 0.12D, JUMP_RISE, toZ / flat * 0.12D
            );
            return;
        }
        // 远窗口的带速提前跳，只留给直路：终点前最后一段不跳（带速落终点
        // 格滑过头），拐角不跳（直线动量把她送出拐角侧沿）。
        if (path.getNextNodeIndex() >= path.getNodeCount() - 1) {
            return;
        }
        net.minecraft.world.level.pathfinder.Node after =
                path.getNode(path.getNextNodeIndex() + 1);
        if (Integer.signum(after.x - next.getX())
                        != Integer.signum(next.getX() - here.getX())
                || Integer.signum(after.z - next.getZ())
                        != Integer.signum(next.getZ() - here.getZ())) {
            return;
        }
        Vec3 motion = mob.getDeltaMovement();
        double speed = Math.hypot(motion.x, motion.z);
        // 动量要基本对准台阶方向（夹角余弦 ≥0.9）才配提前跳：斜着接近时的
        // 侧向分量会把她漂出窄台的侧沿。
        if (speed < STEP_WORTH_CARRYING
                || motion.x * toX + motion.z * toZ < 0.9D * speed * flat) {
            return;
        }
        // 只带速度的大小，方向对齐台阶线——转身没转完的斜动量整个丢弃。
        mob.setDeltaMovement(
                toX / flat * speed, JUMP_RISE, toZ / flat * speed
        );
    }

    /**
     * 弧线是合同：滞空第 t tick 的水平速度就该是初速乘 0.91 的 t 次方。
     *
     * <p>实测的"偶尔一瞬间速度不够"另一半在这儿：起跳那一 tick 她还站在地上，
     * 结算时地面摩擦（约 0.546）把刚写进去的冲量吞掉一半，弧线从第二 tick 起
     * 就是欠冲的，全靠空中那点微弱加速度找补，差一口气就掉下去。现在每 tick
     * 对账：低于合同就补回合同值，方向指向锁定落点——地面摩擦、碰擦、谁写了
     * 速度都一样。**只补不削**：比合同快的那一点是空中加速度挣的余量，留着。
     */
    private void restoreTheArc() {
        int t = mob.tickCount - leapTick;
        double meant = leapSpeed * Math.pow(AIR_DRAG, t);
        Vec3 velocity = mob.getDeltaMovement();
        double horizontal = Math.sqrt(
                velocity.x * velocity.x + velocity.z * velocity.z
        );
        if (horizontal >= meant - 1.0E-3D) {
            return;
        }
        double aimX = leapAim.x - mob.getX();
        double aimZ = leapAim.z - mob.getZ();
        double flat = Math.sqrt(aimX * aimX + aimZ * aimZ);
        // 到了落点头顶、或者已经飞过了它（落点在身后），都别再推——朝落点补
        // 就成了往回拽。滑翔落地，剩下的交给重力。
        if (flat < 0.4D
                || aimX * leapDirX + aimZ * leapDirZ <= 0.0D) {
            return;
        }
        mob.setDeltaMovement(
                aimX / flat * meant, velocity.y, aimZ / flat * meant
        );
    }

    /**
     * 该升级就升级，已经是就什么都不做。
     *
     * <p>按**精确类**判断而不是 instanceof：这个类自己继承
     * {@code MaidPathNavigation}，instanceof 会把自己也当成要换的对象。
     */
    public static void upgrade(EntityMaid maid) {
        PathNavigation current = maid.getNavigation();
        if (current != null
                && current.getClass() == MaidPathNavigation.class) {
            maid.setNavigation(new SureFootedNavigation(maid, maid.level()));
        }
    }
}
