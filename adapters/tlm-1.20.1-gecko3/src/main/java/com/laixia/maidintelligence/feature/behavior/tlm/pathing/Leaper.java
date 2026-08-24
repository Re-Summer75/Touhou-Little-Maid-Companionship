package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * 怎么跳：跳跃段、下崖段、贴边段与唯一的起跳出口。
 *
 * <p>从 {@code SegmentedPathwalk} 按职责拆出（单文件五百行的布局纪律）：
 * 执行器回答"这一 tick 该走哪一段"，这里只回答"这一段腾空怎么腾"。所有
 * 判据与评估器同一把尺（{@code FootingRule}）、同一条采样弧线——规划连
 * 的边，执行侧先验后跳，验不过宁可弃路也不赌。
 */
final class Leaper {
    /** 调用方该转走路段的哨兵返回值。 */
    static final String WALK = "walk-instead";

    /** 起跳竖直速度：原版 jumpFromGround 的数。不走 JumpControl——noJumpDelay
     *  会吞掉落地十 tick 内第二跳的竖直分量，只剩水平推力等于平推下缺口。
     *  包内共享：{@code LipRescue} 的小跳同一个数。 */
    static final double JUMP_RISE = 0.42D;

    /** 同层起跳的滞空时长（tick）：起跳 0.42、重力 0.08 的弹道回到同一高度。 */
    private static final double AIRBORNE_TICKS = 11.3D;

    /** 落一格的滞空时长：同一条弹道再落一格。 */
    private static final double DESCENT_TICKS = 13.4D;

    /** 上一格的滞空时长：取上升段那一档——落点要在弧顶前够着，不是等它掉
     *  回来（弧顶在第五 tick、一格二五，从下面数第三 tick 就过了一格）。 */
    private static final double ASCENT_TICKS = 7.5D;

    /** 配速余量：起跳晚一 tick、助跑差半格这类时序抖动的零头。 */
    private static final double LEAP_MARGIN = 1.05D;

    /**
     * 门槛跳的前速。
     *
     * <p>比按距离配速的结果高一截，因为**前三 tick 是白给的**：她还没升过
     * 门板顶面，水平被撞得清零，真正能走的只有顶面之上那五 tick。按一格的
     * 距离老实配速（约 0.14）在那五 tick 里只能挪半格，够不着门板另一侧。
     */
    private static final double SILL_PUSH = 0.30D;

    /** 推力上限：贴边起跳的满冲刺玩家量级。跨三格缺口按真实弹道要 0.58，
     *  够不着就是够不着——封在这里，落点近沿仍在射程内（0.5 能走三格六）。 */
    private static final double MAX_LEAP_SPEED = 0.50D;

    private final Mob mob;
    private final SureFootedNavigation nav;
    private final LeapFlight flight;
    private final LaneWork lane;

    private int leaps;
    private int drops;

    Leaper(Mob mob, SureFootedNavigation nav, LeapFlight flight,
            LaneWork lane) {
        this.mob = mob;
        this.nav = nav;
        this.flight = flight;
        this.lane = lane;
    }

    int leaps() {
        return leaps;
    }

    int drops() {
        return drops;
    }

    /**
     * 跳跃段：远节点是评估器连的边，先验后跳。验三样：形状（正轴，或侧移
     * 一格的同层斜线）、中段真缺口（按真实弧线采样）、落点真地板。走到崖
     * 边（脚前半格没有贴脚地板）才起，早了够不着。
     *
     * @return 进日记的分支名；{@link #WALK} 表示这不是跳的事，调用方走路。
     */
    String leapSegment(
            BlockPos here,
            BlockPos node,
            int dy,
            int dx,
            int dz,
            int span,
            double toX,
            double toZ,
            double flat
    ) {
        int minor = Math.min(Math.abs(dx), Math.abs(dz));
        boolean shapeFits = minor == 0
                ? (dx == 0) ^ (dz == 0)
                : minor == 1 && dy == 0;
        boolean spanFits = dy == 1
                ? span <= SafeFootingNodeEvaluator.UP_HOP_MAX_REACH
                : span <= SafeFootingNodeEvaluator.MAX_GAP_SPAN
                        + (minor == 0 ? 1 : 0);
        if (!shapeFits || !spanFits) {
            nav.stop();
            return "leap-refused";
        }
        // 中段逐格验空：正轴走轴线，斜线按真实弧线采样——半路有贴走面的
        // 地板就不是缺口，走路的事；中间格格心被占但贴边塞得下身位（评估
        // 器连的挤边跨越），走贴边折线。
        boolean alongX = Math.abs(dx) >= Math.abs(dz);
        Double lanePerp = null;
        if (flightLineHasFooting(here, dx, dz)) {
            lanePerp = lane.corridorLane(here, dx, dz, alongX);
            if (lanePerp == null) {
                if (minor == 0 && span == 2 && dy == 0) {
                    BlockPos mid = here.offset(
                            Integer.signum(dx), 0, Integer.signum(dz));
                    if (FootingRule.coversCenter(mob.level(), mid)) {
                        trot();
                        if (lane.holdTheLane(mid, dx, dz)) {
                            return "squeeze";
                        }
                    }
                }
                return WALK;
            }
            // 空中车道（柱旁侧缝穿弧）：先侧向对齐车道再谈起跳——弧线要
            // 从缝里穿，起跳点不在车道上就是撞柱（玩家实测：栅栏压线，
            // 明明有空间却拒跳）。对齐**纯侧向**：带前进分量会在对齐完成
            // 前把人推过崖沿（读数带实测 t11 悬空触唇救坠落），前进交给
            // 探针门控的起跳流程。
            double herPerp = alongX ? mob.getZ() : mob.getX();
            if (Math.abs(herPerp - lanePerp) > 0.12D) {
                // 对齐用直写速度：MoveControl 的加速没有到位刹车，带着走
                // 路动量急转侧向，半格内必过冲出道外（一格宽道实测冲到
                // 2.3 坠落）。先把控制器停在原地，再写一股自阻尼的侧向小
                // 速度，前进分量清零。
                mob.getMoveControl().setWantedPosition(
                        mob.getX(), here.getY(), mob.getZ(), 0.0D);
                double nudge = Math.max(-0.09D, Math.min(0.09D,
                        (lanePerp - herPerp) * 0.5D));
                Vec3 motion = mob.getDeltaMovement();
                mob.setDeltaMovement(
                        alongX ? 0.0D : nudge,
                        motion.y,
                        alongX ? nudge : 0.0D
                );
                return "squeeze";
            }
            // 对齐了：目标点改沿车道线，落格心那份侧偏落地后再走回来。
            if (alongX) {
                toZ = lanePerp - mob.getZ();
            } else {
                toX = lanePerp - mob.getX();
            }
            flat = Math.max(0.3D, Math.hypot(toX, toZ));
        }
        if (!FootingRule.standable(mob.level(), node)) {
            nav.stop();
            return "leap-refused";
        }
        // 还没到崖边：朝落点跑，速度收着（助跑不影响绝对值直写的起跳）。
        //
        // 问"脚下有没有承托"要用同一把尺（见 footingUnder）。从前这里减半格
        // 取格子，站在**下半门板**上（脚面 0.1875）时会掉到门板下面那一格
        // ——探针在檐的正中间就报"前面是悬崖"，她当场起跳，起跳点与瞄点全
        // 错（玩家实测：站在下半活板门上必然转身往侧边跳下去）。
        double aheadX = mob.getX() + toX / flat * 0.6D;
        double aheadZ = mob.getZ() + toZ / flat * 0.6D;
        if (FootingRule.footingUnder(mob.level(), aheadX, mob.getY(), aheadZ)
                >= mob.getY() - 0.6D) {
            trot();
            // 自己的格被高柱占着（柱在崖沿格）：助跑不能瞄格心直线——那正
            // 对柱面，推九十 tick 也推不动（读数带实测）。侧向锁进车道、
            // 沿行进轴推进，过了柱崖边探针自然放行起跳。
            BlockPos hereCell = mob.blockPosition();
            if (FootingRule.coversCenter(mob.level(), hereCell)
                    && lane.holdTheLane(hereCell, dx, dz)) {
                return "squeeze";
            }
            walkTowards(node);
            return "leap";
        }
        // 落点瞄点：普通格是格心；柱格落它的贴边点（朝柱心跳就是撞柱弹进
        // 虚空）；穿缝的跳锁沿车道的落点——滞空转向若还朝格心拽，人在柱
        // 格上空就被拉回中线撞柱顶。方向与配速都按真实瞄点重算。
        Vec3 aimLanding = lanePerp == null
                ? FootingRule.aimPoint(mob.level(), node)
                : alongX
                        ? new Vec3(node.getX() + 0.5D, node.getY(), lanePerp)
                        : new Vec3(lanePerp, node.getY(),
                                node.getZ() + 0.5D);
        // 孤台落点（顺行进方向再往前没有贴走面的地板）：瞄点往回收四分之一
        // 格。诚实配速会把她准确送到瞄点，而瞄格心意味着一半的落地误差指向
        // 对沿——落点只有一格长时，那一半就是掉下去（抬高石往返实测：她从
        // 一格宽孤石顶上掠过去，掉进对侧缺口）。**收的是瞄点，不是推力**：
        // 近沿仍留四分之一格余量，欠冲不会借这个口子回来；而滞空刹车那半格
        // 的门槛动不得，放宽一次就把唇沿自救的小跳刹成了机枪。
        if (minor == 0) {
            BlockPos beyond = node.offset(
                    Integer.signum(dx), 0, Integer.signum(dz));
            if (FootingRule.coveringTopAt(mob.level(), beyond.below())
                    < node.getY() - 0.6D) {
                aimLanding = aimLanding.subtract(
                        Integer.signum(dx) * 0.25D, 0.0D,
                        Integer.signum(dz) * 0.25D);
            }
        }
        double tx = aimLanding.x - mob.getX();
        double tz = aimLanding.z - mob.getZ();
        double tf = Math.max(0.3D, Math.hypot(tx, tz));
        // 起跳配速：解真实弹道（见 paceFor），滞空时长按落差取，推力按瞄点
        // 给足。
        //
        // 这里曾按"落点再往前没有地板就收推力"削过一刀（孤台过冲即坠的直
        // 觉）。**那是替系统性欠冲背了黑锅**：线性配速下她本来就贴着落点近
        // 沿落地，再削就是欠冲进缝——step-island 的第一跳被削到 0.2 下限，
        // 十三 tick 只走一格七，悬在 3.7 坠落，孤柱西沿在 4.0。孤台该收的
        // 是瞄点（上面那段），推力这一端不克扣。
        double leap = Math.min(
                MAX_LEAP_SPEED,
                Math.max(0.2D, LEAP_MARGIN * paceFor(tf,
                        dy == 1 ? ASCENT_TICKS
                                : dy == -1 ? DESCENT_TICKS
                                : AIRBORNE_TICKS))
        );
        takeoff(tx / tf * leap, tz / tf * leap,
                aimLanding, leap, tx / tf, tz / tf);
        return "leap";
    }

    /**
     * 下崖段：外一格、落两格以上的边（评估器的下崖跟进 + 原版的深台阶落）。
     * 高台尽头与目标之间只隔一段落差时，图里这条边就是"跳下去跟上"的授权
     * ——实机三连报的"站在活板门/边缘上不动"缺的正是它。走近全程收速（迈
     * 出前的每一步都踩在崖沿上），贴沿一小步迈出并锁定落柱，滞空转向只认
     * 它，落地或落水收腿解锁。干落最深六格，落水放行，再深不迈。
     *
     * <p>**干落的单程票要有到站背书，落水不用**：干落跳下去回不了头，整条
     * 路必须真到目标（canReach）——四格拒走的钉子抓过一回：残路只到目标正
     * 下方，跳进去就困在坑底。水不一样：水是可逆的机动空间，而"到站"在水
     * 目标上天然为假（从水里爬上岸的边图里连不上，canReach 永远说不）——
     * 池塘钉的读数带抓到 canReach 闸把落水整个禁死，nopath 与 dropoff 一
     * tick 一换地空转。
     *
     * @return 进日记的分支名。
     */
    String dropSegment(
            Path path,
            BlockPos node,
            int dy,
            double toX,
            double toZ,
            double flat
    ) {
        boolean water = mob.level().getFluidState(node.below()).isSource();
        if (dy < -SafeFootingNodeEvaluator.DROP_MAX) {
            nav.stop();
            return "drop-deep@" + node.toShortString();
        }
        if (!water && !path.canReach()) {
            nav.stop();
            return "drop-unbooked@" + node.toShortString();
        }
        if (!water && !FootingRule.standable(mob.level(), node)) {
            nav.stop();
            return "drop-nofloor@" + node.toShortString();
        }
        if (flat > 1.25D) {
            trot();
            walkTowards(node);
            return "dropoff";
        }
        // 落点在**正下方**时 flat 趋近于零：除它得到的是 NaN，而 NaN 写进
        // 速度就是她当场从世界上消失。这一档本来也不需要水平推力——原地垂
        // 直下去就是了，锁只负责把她按在这一列里别让走速带偏。
        double lane = Math.max(flat, 1.0E-3D);
        double push = flat < 0.05D
                ? 0.0D
                : Math.max(0.12D, Math.min(0.18D, flat / 12.0D));
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(toX / lane * push, motion.y, toZ / lane * push);
        flight.lock(node, push, toX / lane, toZ / lane);
        drops++;
        return "dropoff";
    }

    /**
     * 要在 {@code ticks} tick 内飞过 {@code distance} 格，起跳该有多快。
     *
     * <p>**空气阻力是复利，不是折扣**：滞空第 t tick 的水平速度是初速乘
     * 0.91 的 t 次方（{@code LeapFlight} 的弧线合同就按它对账），走过的路
     * 是那串等比数列的和 <code>v(1-0.91^t)/0.09</code>，不是 v·t。按 v·t
     * 配速就系统性欠冲：同层短一成七、落一格短两成六、上一格短两成五。
     *
     * <p>那份欠冲从前由几个"过冲余量"常数（1.25、1.35）遮着，而余量遮不
     * 住的部分一直在实机里现形——她贴着落点**近沿**落地，脚滑的余地全在
     * 那半格里，落点越短越险。玩家三次报的"跑酷太容易摔跤"就是它；读数
     * 带 t35 把它按住：初速 0.20，十三 tick 只走一格七，人悬在 3.7，孤柱
     * 西沿在 4.0。
     */
    private static double paceFor(double distance, double ticks) {
        return distance * (1.0D - LeapFlight.AIR_DRAG)
                / (1.0D - Math.pow(LeapFlight.AIR_DRAG, ticks));
    }

    /**
     * 门槛跳：相邻同层格之间横着一片**整格高的贴边竖片**（开着的活板门、
     * 开着的门）。图上那格能站也能进——格心空着、脚下有地板——可她的身子
     * 从那一面横穿过不去，走过去就是顶在门板上（读数带实测：卡在 4.70，
     * note=walk，看门狗连咬九次，一条腿都没走完）。竖片只有一格高，跳得过
     * ——玩家原话"可以小心跨越"。
     *
     * <p>**必须上落点锁。**原版碰撞分轴解算：起跳后头几 tick 她还没升过门
     * 板顶面，水平那一分量被撞得清零，而执行器对**无锁滞空一概不管**——于
     * 是她原地起落，一次不成再来一次，实测跳了六十九下还在门板西侧。上了锁
     * 滞空每 tick 都在，弧线合同把被撞掉的水平速度补回来，升过顶面那几 tick
     * 正好把她带过去。
     *
     * <p>抬高一格仍过不去的（栅栏一格半）不跳：那是真墙，白跳会跳成机枪。
     *
     * @return true 表示这一 tick 已接管
     */
    boolean hopOverASill(BlockPos here, BlockPos node) {
        if (!mob.onGround()) {
            return false;
        }
        Vec3 aim = FootingRule.aimPoint(mob.level(), node);
        // 平着问：下沿抬到合法地板之上（台阶、关着的门板都在半格以内），
        // 否则"走上门板"会被读成"过不去"。
        if (FootingRule.walkLineClear(mob.level(), mob.getX(), mob.getZ(),
                aim.x, aim.z,
                here.getY() + FootingRule.STANDABLE_TOP + 0.05D)) {
            return false;
        }
        // 抬一格问：下沿正好在竖片顶面之上。栅栏一格半仍挡着，跳它是白跳。
        if (!FootingRule.walkLineClear(mob.level(), mob.getX(), mob.getZ(),
                aim.x, aim.z, here.getY() + 1.05D)) {
            return false;
        }
        double toX = aim.x - mob.getX();
        double toZ = aim.z - mob.getZ();
        double flat = Math.max(0.3D, Math.hypot(toX, toZ));
        takeoff(toX / flat * SILL_PUSH, toZ / flat * SILL_PUSH,
                aim, SILL_PUSH, toX / flat, toZ / flat);
        return true;
    }

    /**
     * 下一格台阶，而落点只有一格长：锁住落点走，别让它变成一次自由落体。
     *
     * <p>普通的下一格是走路的事——迈出去、掉半秒、落地。可**执行器对无锁滞
     * 空一概不管**，那半秒里她带着走速平移，落点若只有一格长（倒 T 的横杠
     * 端头、贴着缺口的凸台），这一段平移正好把她送过对沿（读数带实测：从倒
     * T 那一竖下来，摔在三格缺口里的 7.10）。
     *
     * <p>判据只问一句：顺行进方向再往前，落点那一层还有没有地板。楼梯、连
     * 续下坡都有，不进这一支；孤零零的一格才有。
     *
     * @return true 表示这一 tick 已接管
     */
    boolean stepDownOntoAShortLanding(BlockPos node, int dx, int dz,
            double toX, double toZ, double flat) {
        if (!mob.onGround() || flat > 1.6D || flat < 0.05D) {
            return false;
        }
        BlockPos beyond = node.offset(
                Integer.signum(dx), 0, Integer.signum(dz));
        if (FootingRule.coveringTopAt(mob.level(), beyond.below())
                >= node.getY() - 0.6D) {
            return false;
        }
        double push = Math.max(0.10D, Math.min(0.16D, flat / 12.0D));
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(toX / flat * push, motion.y, toZ / flat * push);
        flight.lock(node, push, toX / flat, toZ / flat);
        return true;
    }

    /** 起跳直写 + 落点锁定，一切跳跃的唯一出口。登阶段也从这儿起跳。 */
    void takeoff(
            double vx,
            double vz,
            BlockPos landing,
            double speed,
            double dirX,
            double dirZ
    ) {
        takeoff(vx, vz,
                new Vec3(landing.getX() + 0.5D, landing.getY(),
                        landing.getZ() + 0.5D),
                speed, dirX, dirZ);
    }

    /** 亚格瞄点版：穿缝的跳落在车道坐标上。 */
    private void takeoff(
            double vx,
            double vz,
            Vec3 landing,
            double speed,
            double dirX,
            double dirZ
    ) {
        mob.setDeltaMovement(vx, JUMP_RISE, vz);
        flight.lock(landing, speed, dirX, dirZ);
        leaps++;
    }
    private boolean flightLineHasFooting(BlockPos here, int dx, int dz) {
        int steps = 8 * Math.max(Math.abs(dx), Math.abs(dz));
        int lastX = here.getX();
        int lastZ = here.getZ();
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int cx = (int) Math.floor(here.getX() + 0.5D + dx * t);
            int cz = (int) Math.floor(here.getZ() + 0.5D + dz * t);
            if ((cx == here.getX() && cz == here.getZ())
                    || (cx == here.getX() + dx && cz == here.getZ() + dz)
                    || (cx == lastX && cz == lastZ)) {
                continue;
            }
            lastX = cx;
            lastZ = cz;
            BlockPos cell = new BlockPos(cx, here.getY(), cz);
            if (FootingRule.coversCenter(mob.level(), cell)
                    || FootingRule.coveringTopAt(mob.level(), cell.below())
                            >= here.getY() - 0.6D) {
                return true;
            }
        }
        return false;
    }

    /** 带着的冲劲收到崖边步速。 */
    private void trot() {
        Vec3 motion = mob.getDeltaMovement();
        double speed = Math.hypot(motion.x, motion.z);
        if (speed > EdgeGuard.EDGE_TROT) {
            mob.setDeltaMovement(
                    motion.x / speed * EdgeGuard.EDGE_TROT,
                    motion.y,
                    motion.z / speed * EdgeGuard.EDGE_TROT
            );
        }
    }

    /** 走段目标：节点格心。 */
    private void walkTowards(BlockPos node) {
        mob.getMoveControl().setWantedPosition(
                node.getX() + 0.5D, node.getY(), node.getZ() + 0.5D,
                nav.pace()
        );
    }
}
