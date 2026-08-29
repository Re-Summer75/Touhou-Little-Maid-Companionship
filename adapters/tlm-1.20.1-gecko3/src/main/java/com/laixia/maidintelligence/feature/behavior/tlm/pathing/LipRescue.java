package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.slf4j.Logger;

/**
 * 唇沿自救：栖在图连不进去的位置上时，把人跳回图里。
 *
 * <p>从 {@code SegmentedPathwalk} 按职责拆出（单文件五百行的布局纪律）：
 * 执行器回答"这一段怎么走"，这里只回答"这个站位是不是死角、往哪跳能活"。
 * 三张网都是压测里的真冻结换来的：**带路小跳**（凹槽里栖在无心之柱边沿，
 * 有路径就朝下一节点补完最后一格）、**无路盲跳**（居中栖在缺口柱上时方块
 * 坐标落在虚空列，原版建路的起点解析报废、findPath 连续为 null——节奏桥
 * 上冻结整场的形态，没路就自己扫邻格找地板）、**死完路撬栖**（栖在楼梯沿
 * 这类进不了图的位置，A* 每 tick 只能铺出"到脚下为止"的残路，销账即完、
 * 再铺再完——之字梯上原地转 294 tick 的形态）。
 */
final class LipRescue {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 唇沿自救的小跳前速。 */
    private static final double LIP_HOP = 0.22D;

    /** 死完路多少 tick 后认定是栖位死循环而不是正常到达。 */
    private static final int DEAD_DONE_TICKS = 40;

    /** 带着走目标死完路多少 tick 算实机冻结，进日志（每段只报一次）。 */
    private static final int CONFESS_TICKS = 100;

    private final Mob mob;
    private final LeapFlight flight;

    /** 完路死循环看门狗：同一格上路径反复"铺完/铺不出"就计时。 */
    private BlockPos doneSpot;
    private int doneSince;

    /** 实机黑匣子：脑子里有走目标、路却连续死着的 tick 数。 */
    private int wantedDead;
    private boolean confessed;

    /** 供词用：见过的最长死完路连击。正立位冻结没有网，靠它分型。 */
    private int deadDoneMax;

    LipRescue(Mob mob, LeapFlight flight) {
        this.mob = mob;
        this.flight = flight;
    }

    /** 真唇沿：脚下三格内什么都盖不住格心。只查两格会把台阶过渡帧误判进来。 */
    boolean standingOnALip(BlockPos feetCell) {
        // **泡在水里不是站在唇沿上。**"脚下三格没支撑"这句话在水中恒真，
        // 可她那时根本不是站着，是在游——于是盲跳每 tick 都触发，而浮力
        // 又一直把她往上托，人就这么"游"上了天：实测她从悬空门板摔进水
        // 里之后一路浮到 y=13.7 撞在天花板上（note=lip-blind、gnd=WATER、
        // 竖直速度恒为 +0.02），倒 T 那条更是浮到了 y=25。
        //
        // 执行器的早退门槛写的是 !onGround && !isInWater——泡水时它照常接
        // 管，本意是浅水里走路照走；可自救这一支只对"站着"成立。
        if (mob.isInWater()) {
            return false;
        }
        // **滞空也不是站在唇沿上。**"脚下三格没支撑"在空中恒真——她跳
        // 烛顶的半路残了一次路，盲跳就把好好的弧改写成侧向乱跳（中继钉
        // Leg2 实测 note=lip 摔在 z3.6）。自救只对"站着"成立，跳到一半
        // 的交给落点锁和重力。
        if (!mob.onGround()) {
            return false;
        }
        // 柱顶（石锥、末地烛）不再算唇沿：图如今认这个站位（perchTop 分
        // 类+真顶地板），站柱顶是有路可铺的正规立足，盲跳反而会把中继跳
        // 废掉（跑酷图的柱顶就是要踩的中继，玩家实测点名）。当年"栖柱乱
        // 舞摔"的病根是图不认位置时的自救乱动，路一通自救就不该再插手。
        return lipAt(feetCell)
                && lipAt(feetCell.below())
                && lipAt(feetCell.below(2));
    }

    /** 这一格接不住站在格心的人：什么都没盖住格心。 */
    private boolean lipAt(BlockPos pos) {
        return !FootingRule.coversCenter(mob.level()
                .getBlockState(pos).getCollisionShape(mob.level(), pos));
    }

    /** 路还活着：死完路计时清零。 */
    void pathAlive() {
        doneSpot = null;
        doneSince = 0;
        wantedDead = 0;
        confessed = false;
    }

    /** 供词：这一世见过的最长死完路连击（tick）。 */
    int deadDoneMax() {
        return deadDoneMax;
    }

    /** 正立位冻结多少 tick 后开始侧移换起点；之后每隔多少 tick 再试。 */
    private static final int SIDESTEP_TICKS = 60;
    private static final int SIDESTEP_RETRY = 40;

    /**
     * 正立位活体冻结的**侧移自救**（兼黑匣子）：脑子里有走目标、路却连续
     * 死着、脚下又是好好的地板——此前只记录不救（"站得端正的死完路是真
     * 到头"），实机证明不尽然：走目标远在天边时这是真冻结，玩家得打掉她
     * 脚下的方块（强迫换起点格）或引来怪物才能救活（玩家实测）。救法照抄
     * 偏方的原理：**自己迈去邻格换起点**——挑一块能站的邻格（优先朝走目
     * 标那侧），格一换建路就活。
     *
     * <p>两道门挡常态：目标就在脚边的（到站后的残留走目标）不算；走目标
     * **缺席的 tick 不清零**只不计数——实机的重试环里 sink 每次失败都会
     * 擦掉走目标、上层下一 tick 再补写，见缺席就清零的计数器永远数不满。
     *
     * @return true 表示这一 tick 在侧移，调用方记账收手。
     */
    private boolean sidestepAliveFreeze(String branch) {
        // 交战中她的脚归战斗管：远程站桩输出时走目标常驻远处、导航空转，
        // 侧移网会把射手推歪（弩局实测：一轮之后再没打中过）。
        if (mob.getBrain()
                .getMemory(MemoryModuleType.ATTACK_TARGET)
                .isPresent()) {
            wantedDead = 0;
            return false;
        }
        var walk = mob.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (walk == null) {
            return false;
        }
        double toX = walk.getTarget().currentPosition().x - mob.getX();
        double toZ = walk.getTarget().currentPosition().z - mob.getZ();
        double flat = Math.hypot(toX, toZ);
        if (flat <= 3.0D) {
            wantedDead = 0;
            return false;
        }
        ++wantedDead;
        if (wantedDead >= CONFESS_TICKS && !confessed) {
            confessed = true;
            LOGGER.warn(
                    "[maid-pathing] alive-freeze: {} at ({}, {}, {}) "
                            + "branch={} walkTarget={} navTarget={} "
                            + "feetCell={}",
                    mob.getName().getString(),
                    String.format("%.2f", mob.getX()),
                    String.format("%.2f", mob.getY()),
                    String.format("%.2f", mob.getZ()),
                    branch,
                    walk.getTarget().currentBlockPosition().toShortString(),
                    mob.getNavigation().getTargetPos(),
                    mob.blockPosition().toShortString()
            );
        }
        if (wantedDead < SIDESTEP_TICKS
                || (wantedDead - SIDESTEP_TICKS) % SIDESTEP_RETRY != 0) {
            return false;
        }
        BlockPos here = mob.blockPosition();
        BlockPos best = null;
        double bestDot = -Double.MAX_VALUE;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] side : sides) {
            BlockPos cell = here.offset(side[0], 0, side[1]);
            if (!FootingRule.coversCenter(mob.level(), cell.below())
                    || FootingRule.coversCenter(mob.level(), cell)
                    || FootingRule.coversCenter(mob.level(), cell.above())) {
                continue;
            }
            double dot = side[0] * toX / flat + side[1] * toZ / flat;
            if (dot > bestDot) {
                bestDot = dot;
                best = cell;
            }
        }
        if (best == null) {
            return false;
        }
        mob.getMoveControl().setWantedPosition(
                best.getX() + 0.5D, best.getY(), best.getZ() + 0.5D, 1.0D);
        return true;
    }

    /**
     * 死完路自救：路铺不出（nopath）或一铺出来就被销光（advanced-out 到
     * 脚下）的 tick 都进这里。真唇沿立即盲跳；不是唇沿但同一格上死完路持续
     * 了四十 tick、脚下格心又没真地板的栖位也盲跳。站得端正的死完路是真到
     * 头（到达目标后的常态），不碰。
     *
     * @return 触发的网名（进供词），没触发返回 null。
     */
    String deadDone(String branch) {
        BlockPos here = mob.blockPosition();
        if (standingOnALip(here)) {
            hopToAnyFooting(here);
            return "lip-blind";
        }
        if (sidestepAliveFreeze(branch)) {
            return "sidestep";
        }
        // 栖在细柱顶、路铺不出、侧移又无处可去（孤柱四邻皆空）：**站稳
        // 待命**。上层的跟随目标还在每 tick 推移动控制，微推与柱面弹回把
        // 她搓成原地陀螺（实机：主人站在不可达处，她在末地烛顶不断旋
        // 转）。停灯清速，人站定面向主人，等他回到可达域路自然就活。
        if (mob.onGround() && FootingRule.slimPillar(mob.level()
                .getBlockState(here.below())
                .getCollisionShape(mob.level(), here.below()))) {
            mob.getMoveControl().setWantedPosition(
                    mob.getX(), mob.getY(), mob.getZ(), 0.0D);
            var motion = mob.getDeltaMovement();
            mob.setDeltaMovement(0.0D, motion.y, 0.0D);
            return "perch-park";
        }
        if (!here.equals(doneSpot)) {
            doneSpot = here;
            doneSince = 0;
            return null;
        }
        deadDoneMax = Math.max(deadDoneMax, doneSince + 1);
        if (++doneSince <= DEAD_DONE_TICKS) {
            return null;
        }
        doneSince = 0;
        if (FootingRule.coversCenter(mob.level(), here.below())) {
            return null;
        }
        hopToAnyFooting(here);
        return "unperch";
    }

    /**
     * 带路小跳：站在无心之柱边沿、路径目标就在贴身，一记小跳补完最后一格。
     *
     * <p>竖直量按落差配：下行目标不配全高起跳——满弧十六 tick 能把小跳漂出
     * 两格远，飞过对角下方的落点摔进虚空（之字梯顶台下行实测）。短弧短滞空，
     * 前速也按滞空配小。
     */
    void hopOffTheLip(BlockPos node, int dy, double toX, double toZ,
            double flat) {
        if (dy < -1 || dy > 1 || flat < 0.35D || flat > 2.0D
                || !FootingRule.coversCenter(
                        mob.level(), node.below())) {
            // 带路小跳够不着（节点太远/太高）不等于没得救：静默返回会吞
            // 掉这一 tick 的一切接管，她带着残速站在唇沿上自然溜出沿（中
            // 继钉回程实测：脚的方块坐标漂进台条旁的挂格、lip 分支空转四
            // tick、vy −0.16 坠落）。降级为就近盲跳——邻格里有真地板就
            // 跳回去。
            hopToAnyFooting(mob.blockPosition());
            return;
        }
        double rise = dy < 0 ? 0.25D : Leaper.JUMP_RISE;
        double airTicks = dy < 0 ? 14.0D : 9.0D;
        double hop = Math.min(LIP_HOP, Math.max(0.1D, flat / airTicks));
        mob.setDeltaMovement(toX / flat * hop, rise, toZ / flat * hop);
        flight.lock(node, hop, toX / flat, toZ / flat);
    }

    /**
     * 无路可依的盲救：扫四个正邻的三个高度，挑第一块"脚下真地板、身位头位
     * 无碰撞"的格子短弧跳过去。同层优先，其次下一格，最后上一格。
     */
    private void hopToAnyFooting(BlockPos here) {
        BlockPos best = null;
        int bestRank = Integer.MAX_VALUE;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] rises = {0, -1, 1};
        for (int[] side : sides) {
            for (int dy : rises) {
                BlockPos cell = here.offset(side[0], dy, side[1]);
                if (!FootingRule.coversCenter(
                                mob.level(), cell.below())
                        || FootingRule.coversCenter(
                                mob.level(), cell)
                        || FootingRule.coversCenter(
                                mob.level(), cell.above())) {
                    continue;
                }
                int rank = dy == 0 ? 0 : dy < 0 ? 1 : 2;
                if (rank < bestRank) {
                    bestRank = rank;
                    best = cell;
                }
            }
        }
        if (best == null) {
            return;
        }
        double toX = best.getX() + 0.5D - mob.getX();
        double toZ = best.getZ() + 0.5D - mob.getZ();
        double flat = Math.max(0.3D, Math.hypot(toX, toZ));
        double rise = best.getY() > here.getY()
                ? Leaper.JUMP_RISE : 0.25D;
        double hop = Math.min(LIP_HOP, Math.max(0.1D, flat / 12.0D));
        mob.setDeltaMovement(toX / flat * hop, rise, toZ / flat * hop);
        flight.lock(best, hop, toX / flat, toZ / flat);
    }
}
