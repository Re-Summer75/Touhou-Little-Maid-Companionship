package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
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

    /** 同层起跳的滞空时长（tick），推力按它配速。 */
    private static final double AIRBORNE_TICKS = 11.0D;

    /** 推力上限：贴边起跳的满冲刺玩家量级。跨满三格按配速要 0.497。 */
    private static final double MAX_LEAP_SPEED = 0.50D;

    private final Mob mob;
    private final SureFootedNavigation nav;
    private final LeapFlight flight;

    private int leaps;
    private int drops;

    Leaper(Mob mob, SureFootedNavigation nav, LeapFlight flight) {
        this.mob = mob;
        this.nav = nav;
        this.flight = flight;
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
            lanePerp = corridorLane(here, dx, dz, alongX);
            if (lanePerp == null) {
                if (minor == 0 && span == 2 && dy == 0) {
                    BlockPos mid = here.offset(
                            Integer.signum(dx), 0, Integer.signum(dz));
                    if (FootingRule.coversCenter(mob.level(), mid)) {
                        trot();
                        if (holdTheLane(mid, dx, dz)) {
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
        if (!FootingRule.coversCenter(mob.level(), node.below())) {
            nav.stop();
            return "leap-refused";
        }
        // 还没到崖边：朝落点跑，速度收着（助跑不影响绝对值直写的起跳）。
        double aheadX = mob.getX() + toX / flat * 0.6D;
        double aheadZ = mob.getZ() + toZ / flat * 0.6D;
        BlockPos aheadFloor = BlockPos.containing(
                aheadX, mob.getY() - 0.5D, aheadZ
        );
        if (FootingRule.coveringTopAt(mob.level(), aheadFloor)
                >= mob.getY() - 0.6D) {
            trot();
            // 自己的格被高柱占着（柱在崖沿格）：助跑不能瞄格心直线——那正
            // 对柱面，推九十 tick 也推不动（读数带实测）。侧向锁进车道、
            // 沿行进轴推进，过了柱崖边探针自然放行起跳。
            BlockPos hereCell = mob.blockPosition();
            if (FootingRule.coversCenter(mob.level(), hereCell)
                    && holdTheLane(hereCell, dx, dz)) {
                return "squeeze";
            }
            walkTowards(node);
            return "leap";
        }
        // 起跳：推力按落差配速。上一格瞄格心不过冲（孤台过冲即坠，余量在
        // 竖直弧顶）；同层与下一格稍偏过冲，跨三以上再加一成（唇沿时序余量
        // 只剩一两 tick，压测里每十几趟欠冲一次的那一档）。
        double airborneTicks = dy == 1 ? 7.5D
                : dy == -1 ? 13.5D
                : AIRBORNE_TICKS;
        double overshoot = dy == 1 ? 1.0D
                : span >= 3 ? 1.35D
                : 1.25D;
        // 落点瞄点：普通格是格心；柱格落它的贴边点（朝柱心跳就是撞柱弹进
        // 虚空）；穿缝的跳锁沿车道的落点——滞空转向若还朝格心拽，人在柱
        // 格上空就被拉回中线撞柱顶。方向与配速都按真实瞄点重算。
        Vec3 aimLanding = lanePerp == null
                ? FootingRule.aimPoint(mob.level(), node)
                : alongX
                        ? new Vec3(node.getX() + 0.5D, node.getY(), lanePerp)
                        : new Vec3(lanePerp, node.getY(),
                                node.getZ() + 0.5D);
        double tx = aimLanding.x - mob.getX();
        double tz = aimLanding.z - mob.getZ();
        double tf = Math.max(0.3D, Math.hypot(tx, tz));
        double leap = Math.min(
                MAX_LEAP_SPEED,
                Math.max(0.2D, tf / airborneTicks * overshoot)
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
        if (!water && !FootingRule.coversCenter(mob.level(), node.below())) {
            nav.stop();
            return "drop-nofloor@" + node.toShortString();
        }
        if (flat > 1.25D) {
            trot();
            walkTowards(node);
            return "dropoff";
        }
        double push = Math.max(0.12D, Math.min(0.18D, flat / 12.0D));
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(toX / flat * push, motion.y, toZ / flat * push);
        flight.lock(node, push, toX / flat, toZ / flat);
        drops++;
        return "dropoff";
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

    /**
     * 贴边段（车道保持）：被占格两旁的窄带是**车道**——入口、柱旁、出口
     * 三点全塞得下身位才算（{@code FootingRule.squeezeLane}）。过柱期间
     * 每 tick 沿行进轴推进半格多、侧向锁死在车道坐标上，过完由调用方交
     * 还格心线。单航点折线绕不过居中柱：正面窄带能站但下一步抵在柱面，
     * 滑挤会跳成九十 tick 的死舞（读数带实测）。
     *
     * @return true 已接管这一 tick 的移动；false 没有车道，调用方另想。
     */
    boolean holdTheLane(BlockPos occupied, int dx, int dz) {
        boolean alongX = Math.abs(dx) >= Math.abs(dz);
        double lane = FootingRule.squeezeLane(
                mob.level(), occupied, alongX,
                alongX ? mob.getZ() : mob.getX());
        if (Double.isNaN(lane)) {
            return false;
        }
        double forward = 0.6D;
        double px = alongX
                ? mob.getX() + Math.signum(dx) * forward
                : lane;
        double pz = alongX
                ? lane
                : mob.getZ() + Math.signum(dz) * forward;
        mob.getMoveControl().setWantedPosition(
                px, occupied.getY(), pz, nav.pace());
        return true;
    }

    /**
     * 走廊里的挡格若**只有一个**、是柱类高障、且旁有顺轴车道——给出车道
     * 坐标；矮板挡线（走路的事）、多柱、无缝，都返回 null。与评估器的
     * 空中车道同一把尺。
     */
    private Double corridorLane(BlockPos here, int dx, int dz,
            boolean alongX) {
        int steps = 8 * Math.max(Math.abs(dx), Math.abs(dz));
        int lastX = here.getX();
        int lastZ = here.getZ();
        Double lane = null;
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
            if (!FootingRule.coversCenter(mob.level(), cell)) {
                continue;
            }
            if (!FootingRule.tallAtCenter(mob.level(), cell)
                    || lane != null) {
                return null;
            }
            double found = FootingRule.squeezeLane(mob.level(), cell,
                    alongX, alongX ? mob.getZ() : mob.getX());
            if (Double.isNaN(found)) {
                return null;
            }
            lane = found;
        }
        return lane;
    }

    /**
     * 起跳格心到落点格心的弧线扫过的中间格，有没有贴走面的立足（格内碰撞
     * 盖住格心、或下一格的顶面贴着走面）。有就不是缺口。与评估器的斜线采
     * 样同一条线。
     */
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
