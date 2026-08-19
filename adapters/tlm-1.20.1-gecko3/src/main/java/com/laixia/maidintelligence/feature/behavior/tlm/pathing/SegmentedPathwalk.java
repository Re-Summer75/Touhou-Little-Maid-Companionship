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
        this.leaper = new Leaper(mob, nav, this.flight);
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
        // 无锁滞空（被打飞、走落差）不接管：交给物理落地，落了再算账。
        if (!mob.onGround() && !mob.isInWater()) {
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
        rescue.pathAlive();
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
            return;
        }
        if (span >= 2 && dy >= -1 && dy <= 1) {
            note = leaper.leapSegment(
                    here, node, dy, dx, dz, span, toX, toZ, flat);
            if (Leaper.WALK.equals(note)) {
                note = "leap-walk";
                walkTowards(node);
                watchHerStep(true);
            }
            return;
        }
        if (dy <= -2 && span == 1 && ((dx == 0) ^ (dz == 0))) {
            note = leaper.dropSegment(path, node, dy, toX, toZ, flat);
            return;
        }
        if (dy == 1) {
            note = "climb";
            climbs++;
            climbSegment(node, here, toX, toZ, flat);
            return;
        }
        // 身在被占格（贴边窄带上，柱在身边）：先沿车道出格——普通走段瞄
        // 格心直线，正对柱面，进了格就推不动（窄道柱读数带实测 t13 反扑）。
        if (FootingRule.tallAtCenter(mob.level(), here)
                && leaper.holdTheLane(here,
                        Integer.signum(dx), Integer.signum(dz))) {
            note = "squeeze";
            return;
        }
        note = dy <= -1 ? "descend" : "walk";
        if (dy <= -1) {
            guard.descentEntry();
        }
        walkTowards(node);
        watchHerStep(true);
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

    /** 同一个节点耗满时限就是卡住了：弃路，让上层下一 tick 重铺。 */
    private boolean watchdogBites(Path path) {
        BlockPos node = path.getNextNodePos();
        if (!node.equals(watchedNode)) {
            watchedNode = node;
            watchedSince = mob.tickCount;
            return false;
        }
        return mob.tickCount - watchedSince > NODE_WATCHDOG_TICKS;
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
            walkTowards(node);
            watchHerStep(true);
            return;
        }
        if (!FootingRule.coversCenter(
                mob.level(), node.below())) {
            nav.stop();
            return;
        }
        BlockPos overhead = here.above(2);
        if (!mob.level().getBlockState(overhead)
                .getCollisionShape(mob.level(), overhead).isEmpty()) {
            walkTowards(node);
            return;
        }
        // 脚下带小数高度（沉地板）时机全对不上，交回撞停爬升。
        if (mob.getY() - Math.floor(mob.getY()) > 0.06D) {
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
        leaper.takeoff(toX / flat * speed, toZ / flat * speed,
                node, speed, toX / flat, toZ / flat);
    }

}
