package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * 分段动作执行器：路径不是大致方向，是逐段的动作清单。
 *
 * <p>原版跟随的三宗罪在悬空窄道上就是摔因清单：**松散判到**（离节点半格就
 * 算到，提前瞄下一个）、**定向抄近道**（canCutCorner 沿斜线切角，斜线横穿
 * 拐角外的深渊）、**动量不管**。执行侧攒下的十二道补丁每一道都是在三宗罪边
 * 上加箍——这里把跟随整个换掉，补丁收编成段内规则：**逐节点收账**（格心公差
 * 0.35，永不提前瞄、永不斜切）、**分段执行**（走段、登阶段、下坡段、跳跃段，
 * 各有进入与完成判据）、**滞空一律锁定**（见 {@code LeapFlight}）、**段间
 * 速度清账**（落地收腿、崖边收步、唇沿自救兜底）。
 *
 * <p>规划层不动：A* 与 {@code SafeFootingNodeEvaluator} 的三维跳跃图照旧，
 * 这里只负责把图上那条线走成动作。
 */
final class SegmentedPathwalk {
    /** 到位判据：与节点格心的水平距离。原版约 0.45 还带斜切；这里收紧。 */
    private static final double NODE_TOLERANCE = 0.35D;

    /** 同一个节点耗着不动的时限，超了弃路重铺。 */
    private static final int NODE_WATCHDOG_TICKS = 90;

    /** 前沿到台阶立面近于这个就贴脸接管；负到 -0.35 以内算已经贴死。 */
    private static final double STEP_FACE_NEAR = 0.45D;

    /** 前沿到立面远于这个先走近再说，升到一格高之前会撞脸。 */
    private static final double STEP_FACE_FAR = 1.1D;

    /** 慢于这个速度就没有值得带上台阶的动量。 */
    private static final double STEP_WORTH_CARRYING = 0.08D;

    /** 贴脸登阶的固定前速：方向由台阶线给，转身转到哪都不影响落点。 */
    private static final double CLIMB_PACE = 0.12D;

    private final Mob mob;
    private final SureFootedNavigation nav;
    private final LeapFlight flight;
    private final EdgeGuard guard;
    private final LipRescue rescue;
    private final Leaper leaper;
    private final LaneWork lane;

    /** 节点看门狗：一直是同一个"下一节点"就计时。 */
    private BlockPos watchedNode;
    private int watchedSince;

    /** 行车记录：最后走的分支与各安全网的触发计数，卡住时供词用。 */
    private String note = "-";
    private int hardStops;
    private int watchdogs;
    private int climbs;
    private int hops;

    SegmentedPathwalk(Mob mob, SureFootedNavigation nav) {
        this.mob = mob;
        this.nav = nav;
        this.flight = new LeapFlight(mob, nav);
        this.guard = new EdgeGuard(mob, nav);
        this.rescue = new LipRescue(mob, this.flight);
        this.lane = new LaneWork(mob, nav, this.flight);
        this.leaper = new Leaper(mob, nav, this.flight, this.lane);
    }

    /** 崖边看护的统一入口，顺手记账。 */
    private void watchHerStep(boolean hasPath) {
        if (guard.watch(hasPath)) {
            note = "hardstop";
            hardStops++;
        }
    }

    void run() {
        if (flight.locked()) {
            flight.steer();
            return;
        }
        // **脚不踏实地就不接管。**无锁滞空（被打飞、走落差）交给物理落地，
        // 落了再算账；而**浮在水里**同样不接管——这一条是补的。
        //
        // 从前写的是 {@code !onGround && !isInWater}，本意是"浅水里走路照
        // 走"。可它把**浮着**也算进了接管：她一掉进水里，脚下三格自然没有
        // 支撑，唇沿自救每 tick 都判成真、登阶每 tick 都想往上跳，而浮力又
        // 一直把她往上托——人就这么"游"上了天。实测三桩同源：悬空门板那条
        // 浮到 y=13.7 撞天花板（gnd=WATER、note=lip-blind、竖直速度恒 +0.02），
        // 倒 T 那条浮到 y=25.49，活板门檐那条 hops 冲到 257。
        //
        // 浅水里趟着走仍然管得到：那种时候她是 onGround 的。真游起来归宿主
        // 的游泳导航——那本来就是它的事。
        if (!mob.onGround()) {
            return;
        }
        Path path = nav.getPath();
        if (path == null || path.isDone()) {
            note = path == null ? "nopath" : "pathdone";
            // 死角自救必须能盲跳：栖在图外的位置时路根本铺不出来，"自救要
            // 有路径给目标"的旧规就是死锁（节奏桥 nopath 冻结整场实测）。
            String net = rescue.deadDone(note);
            if (net != null) {
                note = net;
                hops++;
                return;
            }
            watchHerStep(false);
            return;
        }
        advanceByArrival(path);
        if (path.isDone()) {
            // 供词带终点坐标：部分路径只铺到脚下时，这里就是重铺死循环的
            // 现场——外面看是站着不动、看门狗永远轮不到咬。
            note = "advanced-out@" + path.getEndNode().asBlockPos().toShortString();
            String net = rescue.deadDone(note);
            if (net != null) {
                note = net;
                hops++;
            }
            return;
        }
        if (watchdogBites(path)) {
            note = "watchdog";
            watchdogs++;
            nav.stop();
            return;
        }
        BlockPos node = path.getNextNodePos();
        BlockPos here = mob.blockPosition();
        int dy = node.getY() - here.getY();
        int dx = node.getX() - here.getX();
        int dz = node.getZ() - here.getZ();
        int span = Math.max(Math.abs(dx), Math.abs(dz));
        double toX = node.getX() + 0.5D - mob.getX();
        double toZ = node.getZ() + 0.5D - mob.getZ();
        double flat = Math.hypot(toX, toZ);

        if (rescue.standingOnALip(here)) {
            note = "lip";
            hops++;
            rescue.hopOffTheLip(node, dy, toX, toZ, flat);
        } else if (span >= 2 && dy >= -1 && dy <= 1) {
            note = leaper.leapSegment(
                    here, node, dy, dx, dz, span, toX, toZ, flat);
            if (Leaper.WALK.equals(note)) {
                note = "leap-walk";
                walkTowards(node);
                watchHerStep(true);
            }
        } else if (dy <= -2 && span <= 1 && !(dx != 0 && dz != 0)) {
            // 落点**正下方**（span 0）此前落在这一支之外：判据写的是
            // span == 1 且"恰好一轴为零"，两轴都为零就被排除。可那种时候她
            // 走的是兜底的普通走段——不带锁，而执行器对无锁滞空一概不管。
            // 倒 T 实测：从凸块下横杠，滞空里节点变成正下方，她带着东向走速
            // 直接飞出横杠、摔了八格（读数带 t45→t65，从 y=12.8 到 y=5.0）。
            // 正下方是最该锁的一种，不是最不该锁的。
            note = leaper.dropSegment(path, node, dy, toX, toZ, flat);
        } else if (lane.threadTheCorner(here, node, dx, dz, dy)) {
            // 斜穿两根柱子之间那道缝：瞄方块角点，不瞄目标格心。**排在登阶
            // 之前**——斜着上一格若被登阶段接走，那是贴脸撞跳，穿不过只有
            // 0.075 格的角缝（玩家实测："出来可以，但进去就不行"）。
            note = "corner";
        } else if (dy == 1) {
            note = "climb";
            climbs++;
            climbSegment(node, here, toX, toZ, flat);
        } else if (FootingRule.tallAtCenter(mob.level(), here)
                && lane.holdTheLane(here,
                        Integer.signum(dx), Integer.signum(dz))) {
            // 身在被占格（贴边窄带上，柱在身边）：先沿车道出格——普通走段
            // 瞄格心直线，正对柱面，进了格就推不动（窄道柱读数带实测 t13）。
            note = "squeeze";
        } else if (lane.holdTheNarrowWalk(here, node, dx, dz)) {
            // 一格宽的梁上偏出了车道：先回中线。移动控制朝她的朝向推，而掉
            // 头是渐变的——那条弧不拉回来就是从侧沿出去。
            note = "narrow";
        } else if (dy == 0 && span == 1 && leaper.hopOverASill(here, node)) {
            // 门槛：相邻格之间横着一片整格高的贴边竖片（开着的活板门/门）。
            // 图上能走、身子过不去，走过去就是顶着它站到看门狗咬。
            note = "sill-hop";
            hops++;
        } else if (dy == -1 && span == 1 && leaper.stepDownOntoAShortLanding(
                node, dx, dz, toX, toZ, flat)) {
            // 下一格，可落点只有一格长：锁住走。不锁的话那半秒滞空里她带着
            // 走速平移，正好滑过对沿（倒 T 横杠端头实测）。
            note = "stepdown";
        } else {
            note = dy <= -1 ? "descend" : "walk";
            if (dy <= -1) {
                guard.descentEntry();
            }
            walkTowards(node);
            watchHerStep(true);
        }
        // 段内拒走（拒跳、拒落）是死角，不是进展：那一 tick 不给自救的计数
        // 销账。拒走会弃路，上层下一 tick 立刻重铺同一条，两 tick 一轮——
        // 从前每一轮都当"路还活着"把计数清零，自救就永远等不到出手（读数带
        // 实测：孤柱上 drop-unbooked 与 nopath 对拍一千 tick，deadDone 顶到
        // 41 反复归零，而两格外就是能走的下坡）。到不了的残路会一直重现，
        // 认它是活的等于认命。
        if (!refusedSegment()) {
            rescue.pathAlive();
        }
    }

    /**
     * 这一 tick 的段是"验不过、宁可不走"吗。
     *
     * <p>拒跳与三种拒落都带落点坐标进日记，前缀是它们共同的签名。
     */
    private boolean refusedSegment() {
        return note.startsWith("leap-refused") || note.startsWith("drop-deep")
                || note.startsWith("drop-unbooked")
                || note.startsWith("drop-nofloor");
    }

    /** 这一 tick 走的分支，读数带逐行印它。 */
    String note() {
        return note;
    }

    /** 供词：最后分支与安全网计数，卡住的测试把它打进失败信息。 */
    String diary() {
        return "note=" + note + " hardStops=" + hardStops
                + " watchdogs=" + watchdogs + " leaps=" + leaper.leaps()
                + " climbs=" + climbs + " hops=" + hops
                + " drops=" + leaper.drops()
                + " deadDoneMax=" + rescue.deadDoneMax();
    }

    /**
     * 逐节点收账：踩进格心公差算到；**路过也算到**——跳跃与登阶的落点常越
     * 过节点半格，只按公差销账就得走回头，走回头常常又跌回台阶下面，接着
     * 再跳一次（实机的"多余的重跳"）。越过的判据是点积：节点已在"下一节
     * 点方向"的身后、横向没偏出走廊、且不是终点。拐角处下一节点方向转了
     * 九十度、点积近零，仍要求踩点——拐弯的严格性不受影响，斜切依旧不存在
     * （转向永远指向当前节点，这里只是不再把已经路过的节点当没到过）。
     */
    private void advanceByArrival(Path path) {
        while (!path.isDone()) {
            BlockPos node = path.getNextNodePos();
            // 被高柱占的格，到位判据对贴边点算——她永远够不着那种格的格心。
            Vec3 aim = FootingRule.aimPoint(mob.level(), node);
            double toX = aim.x - mob.getX();
            double toZ = aim.z - mob.getZ();
            double flat = Math.hypot(toX, toZ);
            boolean atLevel = Math.abs(mob.getY() - node.getY()) < 1.0D;
            if (flat <= NODE_TOLERANCE && atLevel) {
                path.advance();
                continue;
            }
            if (atLevel && flat <= 1.2D
                    && path.getNextNodeIndex() + 1 < path.getNodeCount()) {
                Node after = path.getNode(path.getNextNodeIndex() + 1);
                double aheadX = after.x - node.getX();
                double aheadZ = after.z - node.getZ();
                double pastX = mob.getX() - (node.getX() + 0.5D);
                double pastZ = mob.getZ() - (node.getZ() + 0.5D);
                if (pastX * aheadX + pastZ * aheadZ > 0.0D) {
                    path.advance();
                    continue;
                }
            }
            return;
        }
    }

    /**
     * 同一个节点耗满时限就是卡住了：弃路，让上层下一 tick 重铺。
     *
     * <p>**咬完必须换班。**弃路之后上层铺的新路，第一个节点往往还是这一个
     * ——计时不重置的话，看门狗对每条新路都当场咬：弃路、重铺、再咬，一
     * tick 一轮，她原地站到天荒地老。守卫就此变成笼子，而且外面看正是玩家
     * 反复报的那个"卡在原地，要打碎脚下方块才有反应"（step-island 读数带
     * t302 起 nopath 与 watchdog 逐 tick 对拍，五百多 tick 没挪过一格）。
     * 重置之后它退回本分：每九十 tick 给一次重铺的机会，真死角交给唇沿自救。
     */
    private boolean watchdogBites(Path path) {
        BlockPos node = path.getNextNodePos();
        if (!node.equals(watchedNode)) {
            watchedNode = node;
            watchedSince = mob.tickCount;
            return false;
        }
        if (mob.tickCount - watchedSince <= NODE_WATCHDOG_TICKS) {
            return false;
        }
        watchedNode = null;
        watchedSince = mob.tickCount;
        return true;
    }

    /** 走段：目标是节点的瞄点——格心，或被高柱占的格的贴边点（同一把尺）。 */
    private void walkTowards(BlockPos node) {
        Vec3 aim = FootingRule.aimPoint(mob.level(), node);
        mob.getMoveControl().setWantedPosition(
                aim.x, node.getY(), aim.z, nav.pace()
        );
    }

    /**
     * 登阶段：相邻高一格。远了先走近；到窗口带速对齐跳；贴脸接管原版撞跳。
     * 两种跳都**只带速度大小、方向对齐台阶线**（转身没转完的斜动量整个丢弃）
     * 并且**落点锁定**——之字梯每步都带拐弯，滞空里路标已指向拐角，不锁就
     * 在半空被拽出侧沿。
     */
    private void climbSegment(
            BlockPos node,
            BlockPos here,
            double toX,
            double toZ,
            double flat
    ) {
        double face = flat - 0.5D - mob.getBbWidth() / 2.0D;
        if (face > STEP_FACE_FAR) {
            // 窄条上的接近不裸推：转身的弧线与宿主"撞面即跳"的辅助会把她
            // 带出侧沿（悬空门板贴登阶块，玩家二轮实测）。缘由与手法见
            // {@link LaneWork#approachAlongTheLane}；宽地面照旧裸推。
            if (!lane.approachAlongTheLane(here, node)) {
                walkTowards(node);
                watchHerStep(true);
            }
            return;
        }
        // 台阶面用同一把尺：悬空的关门板自己就是地板，脚下是虚空也站得住。
        if (!FootingRule.standable(mob.level(), node)) {
            nav.stop();
            return;
        }
        BlockPos overhead = here.above(2);
        if (!mob.level().getBlockState(overhead)
                .getCollisionShape(mob.level(), overhead).isEmpty()) {
            walkTowards(node);
            return;
        }
        // 脚下带小数高度（沉地板）时机全对不上，交回撞停爬升——但**她自
        // 己的格子就是矮地板（关门板、地毯）时不交**。交回去的是裸推撞面
        // 加宿主跳跃辅助，而她的疾跑旗多半还亮着（上一跳的前速早过了跑步
        // 线）：原版疾跑起跳沿**朝向**加一记 0.2 的前冲，朝向恰在转身半途
        // 时这一记就是侧向火箭；无锁滞空执行器不管，气流每 tick 沿同一个
        // 错误朝向续力，恒速 0.12 漂出侧沿。实机黑匣子两卷带子逐字节一致
        // ——门板上登阶必摔，玩家三轮点名的就是这一幕。矮地板上用自己的
        // 对齐锁定起跳就够：0.19 的脚高对 0.42 的起跳升幅绰绰有余。
        if (mob.getY() - Math.floor(mob.getY()) > 0.06D
                && !FootingRule.selfFloor(mob.level().getBlockState(here)
                        .getCollisionShape(mob.level(), here))) {
            walkTowards(node);
            return;
        }
        Vec3 motion = mob.getDeltaMovement();
        double speed = Math.hypot(motion.x, motion.z);
        if (face < STEP_FACE_NEAR) {
            if (face < -0.35D) {
                walkTowards(node);
                return;
            }
            leaper.takeoff(toX / flat * CLIMB_PACE, toZ / flat * CLIMB_PACE,
                    node, CLIMB_PACE, toX / flat, toZ / flat);
            return;
        }
        // 远窗口带速跳：动量要基本对准台阶线，斜着的侧向分量会漂出窄台。
        if (speed < STEP_WORTH_CARRYING
                || motion.x * toX + motion.z * toZ < 0.9D * speed * flat) {
            walkTowards(node);
            return;
        }
        // 预判台阶的后路：登上去马上就是崖或拐弯（一格长的凸台）就不带速
        // ——带上去的那份动量正好把她送出对沿（实测：桥上短凸段侧滑）。
        BlockPos beyond = node.offset(
                Integer.signum(node.getX() - here.getX()), 0,
                Integer.signum(node.getZ() - here.getZ()));
        if (FootingRule.coveringTopAt(mob.level(), beyond.below())
                < node.getY() - 0.6D) {
            speed = CLIMB_PACE;
        }
        leaper.takeoff(toX / flat * speed, toZ / flat * speed,
                node, speed, toX / flat, toZ / flat);
    }

}
