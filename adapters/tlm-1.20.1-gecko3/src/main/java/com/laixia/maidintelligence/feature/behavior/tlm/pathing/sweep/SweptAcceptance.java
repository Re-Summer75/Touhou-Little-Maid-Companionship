package com.laixia.maidintelligence.feature.behavior.tlm.pathing.sweep;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * 跳跃边的终审：把执行侧真会用的那股初速交给扫掠仿真飞一遍，落进目标格
 * 才算这条边成立。
 *
 * <p>手工判据（可走格、头顶空、地板差）继续当**候选粗筛**——快，剪掉绝
 * 大多数不可能；但粗筛说"能"的边未必真能：弧线会撞上筛子看不见的形状
 * （门板的三处缺口、柱尖、压顶的檐），她照图起跳就摔。终审不再枚举形状，
 * 只问物理：**这股初速、这个世界，她落在哪。**
 */
public final class SweptAcceptance {
    /**
     * 起跳点从格心朝落点方向前推的量：执行侧走到崖沿才起跳（脚前 0.6 无
     * 承托的探针），等效比格心近小半格。
     */
    private static final double BRINK_LEAD = 0.4D;

    /** 落点验高的容差，以这条边**自己的立足高度**（toFloor）为中心：沉
     *  门板的 dip 边立足在 toY−0.81、烛上格的立足在 toY+0——真实地板在
     *  哪，落点就该贴着哪。曾换成过"目标格窗"（end.y∈[toY,toY+1) 就算
     *  落进），两头都翻车：上沿放行了"撞邻柱弹落柱顶"的假边（L 岛
     *  nopath），下沿错杀了 dip 边（脚在格底之下 0.81）——toFloor 携带
     *  的边级信息是判定的一部分，丢不得。 */
    private static final double LANDING_SLACK = 0.6D;

    /** 到达语义的水平容差：执行侧按与节点的距离销账，不按落格。 */
    private static final double ARRIVAL_REACH = 0.8D;

    /**
     * 车道扫描的步长与半幅：1/16 格是 MC 形状的粒度（柱面在 6/16），扫
     * 到 ±0.625 —— 再远起跳点就悬到支撑外了，站定预演自己会挡住。
     */
    private static final double LANE_STEP = 0.0625D;

    private static final int LANE_STEPS = 10;

    /**
     * 车道最小宽度：**执行侧兑得起才算一条道**。
     *
     * <p>判到圈最紧一档是 0.10（{@code standMargin}），也就是她站上车道
     * 点时垂直方向仍有 ±0.1 的合法游移；车道若比这更窄，图上"能过"、
     * 人走过去抖一下就撞——挤缝案刚刚交过学费：柱面只剩 5 毫米余量时，
     * 路铺得再对她也一步迈不动。所以车道不取"某个能过的偏移"，取**可行
     * 区间最宽那段的中点**，且那段至少要有 0.1875 宽（三档）。
     */
    private static final double LANE_ROOM = 3 * LANE_STEP;

    /** 车道起跳点要**站得住**才算数：身位塞得进只说明"没撞上"（贴边锚
     *  第一版正是栽在这儿），让物理答——原地站三 tick，掉下去就不算。 */
    private static final int SETTLE_TICKS = 3;

    private SweptAcceptance() {
    }

    /**
     * 这条下崖边迈出去真落得进落点吗。下崖不是跳：水平一小步、竖直零初
     * 速、纯自由落体（执行侧 {@code Leaper.dropSegment} 的直写形态）。落
     * 在细柱尖上（PERCHED）照样算不到——那正是内核替手工判据把守的那类
     * 形状。水落点不进这里：水没有碰撞盒，仿真会一穿到底。
     *
     * <p>两处按真实校准过：**起点是离地点**，不是崖沿——身位半宽压着崖
     * 顶时她还 onGround、每 tick 都被重写推力，重心过沿才真正开始落（贴
     * 沿起仿真会在第二 tick 被崖顶"接住"，误判她落在崖上）。**判定是到达
     * 语义**，不是落格判——执行侧按与节点的距离销账，落点锁把她按在这一
     * 列里，中心偏出格半步照样算到（floor 判会把每条深崖边都错杀）。
     */
    public static boolean dropAccepted(
            BlockGetter level,
            double fromCenterX,
            double fromFloor,
            double fromCenterZ,
            int toX,
            double toFloor,
            int toZ,
            double width,
            double height
    ) {
        double aimX = toX + 0.5D;
        double aimZ = toZ + 0.5D;
        double dirX = aimX - fromCenterX;
        double dirZ = aimZ - fromCenterZ;
        double flat = Math.hypot(dirX, dirZ);
        if (flat < 1.0E-6D) {
            return false;
        }
        dirX /= flat;
        dirZ /= flat;
        double offEdge = 0.5D + width / 2.0D;
        Vec3 start = new Vec3(
                fromCenterX + dirX * offEdge,
                fromFloor,
                fromCenterZ + dirZ * offEdge);
        double push = LeapContract.dropPushFor(flat);
        SweptMotion.Flight flight = SweptMotion.fly(
                level, start,
                new Vec3(dirX * push, 0.0D, dirZ * push),
                width, height, true);
        // PERCHED 照收，同跳边——窄立足是合法落点。
        if (flight.outcome() == SweptMotion.Outcome.AIRBORNE) {
            return false;
        }
        Vec3 end = flight.end();
        return Math.hypot(end.x - aimX, end.z - aimZ) <= ARRIVAL_REACH
                && Math.abs(end.y - toFloor) <= LANDING_SLACK;
    }

    /**
     * 这条跳边执行侧真跳能不能到——**存不存在**可行的跳法。
     *
     * <p>起跳点试两处：贴沿（执行侧探针放行的常态）与格心。上一格的落点
     * 对起跳点敏感：贴沿起跳会在弧还没升足时就探进落点格、被落点方块的
     * 立面截停（悬空门板檐实测：脚位 1.16 对檐顶 1.1875，差 0.03 撞面）；
     * 从格心起跳多出的半格路程正好让她在弧顶上方越过立面。执行侧的起跳
     * 时机本有这个自由度，终审只答"有没有一条能落进去的弧"。
     *
     * @param fromFloor 起跳格真实脚高（门板 0.1875、台阶 0.5 都按真的算）
     * @param toFloor   目标格真实地板高
     */
    public static boolean jumpAccepted(
            BlockGetter level,
            double fromCenterX,
            double fromFloor,
            double fromCenterZ,
            int toX,
            double toFloor,
            int toZ,
            double width,
            double height
    ) {
        return !Double.isNaN(laneFor(level, fromCenterX, fromFloor,
                fromCenterZ, toX, toFloor, toZ, width, height));
    }

    /**
     * 这条跳边**走哪条车道**才落得进去：0 是直线，非零是垂直于跳向的让
     * 开量（左手侧为正），{@code NaN} 是无论怎么让都过不去。
     *
     * <p>让开量不是执行侧的临场微调，是**这条边的合同的一部分**：铺完路
     * 由 {@code LeapLanes} 展开成两个真实站位（起跳前侧移到位、落到对岸
     * 的车道点上再走回中线），执行器照常走、照常跳，一行不必改。
     *
     * @param fromFloor 起跳格真实脚高（门板 0.1875、台阶 0.5 都按真的算）
     * @param toFloor   目标格真实地板高
     */
    public static double laneFor(
            BlockGetter level,
            double fromCenterX,
            double fromFloor,
            double fromCenterZ,
            int toX,
            double toFloor,
            int toZ,
            double width,
            double height
    ) {
        double aimX = toX + 0.5D;
        double aimZ = toZ + 0.5D;
        double dirX = aimX - fromCenterX;
        double dirZ = aimZ - fromCenterZ;
        double flat = Math.hypot(dirX, dirZ);
        if (flat < 1.0E-6D) {
            return Double.NaN;
        }
        dirX /= flat;
        dirZ /= flat;
        int band = (int) Math.round(toFloor - fromFloor);
        // 直线先行：绝大多数跳边走中线就成，一次扫掠了事，车道这一维
        // 的代价只落在真需要绕的边上。
        if (flies(level, fromCenterX, fromFloor, fromCenterZ, dirX, dirZ,
                aimX, aimZ, band, 0.0D, width, height,
                toX, toFloor, toZ)) {
            return 0.0D;
        }
        // 直线不通，先问一句**为什么**：中线上空空如也的话，飞不到就是
        // 跨度不够，往旁边让一步同样飞不到——那二十一次弹道纯属白烧。
        // 车道只对"有东西挡着"的边有意义，而这一问只要几次身位箱查询。
        if (!blockedAlong(level, fromCenterX, fromFloor, fromCenterZ,
                dirX, dirZ, flat, width, height)) {
            return Double.NaN;
        }
        // 扫出侧向可行区间。
        boolean[] open = new boolean[2 * LANE_STEPS + 1];
        for (int i = 0; i < open.length; i++) {
            double lane = (i - LANE_STEPS) * LANE_STEP;
            if (lane == 0.0D) {
                continue;
            }
            open[i] = flies(level, fromCenterX, fromFloor, fromCenterZ,
                    dirX, dirZ, aimX, aimZ, band, lane, width, height,
                    toX, toFloor, toZ);
        }
        // 最宽那段的中点。
        int bestFrom = -1;
        int bestLen = 0;
        int runFrom = -1;
        for (int i = 0; i <= open.length; i++) {
            boolean on = i < open.length && open[i];
            if (on && runFrom < 0) {
                runFrom = i;
            } else if (!on && runFrom >= 0) {
                if (i - runFrom > bestLen) {
                    bestLen = i - runFrom;
                    bestFrom = runFrom;
                }
                runFrom = -1;
            }
        }
        if (bestLen * LANE_STEP < LANE_ROOM) {
            return Double.NaN;
        }
        return (bestFrom + (bestLen - 1) / 2.0D - LANE_STEPS) * LANE_STEP;
    }

    /**
     * 中线上有没有挡路的东西：半格一探，身位箱撞上任何真实碰撞就算有。
     *
     * <p>只探起跳脚高这一层——柱、门板、墙这类立着的障碍从地面长起，脚
     * 高处必然撞得到；悬空的檐探不到，可那种形状横着挡，让开一步也过不
     * 去，本就轮不到车道。
     */
    private static boolean blockedAlong(
            BlockGetter level,
            double fromCenterX,
            double fromFloor,
            double fromCenterZ,
            double dirX,
            double dirZ,
            double flat,
            double width,
            double height
    ) {
        for (double step = 0.5D; step < flat; step += 0.5D) {
            if (!SweptMotion.bodyClear(level,
                    new Vec3(fromCenterX + dirX * step, fromFloor,
                            fromCenterZ + dirZ * step),
                    width, height)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这条车道上的弧线飞得成吗：起跳点与瞄点**同时**侧移，整条弧平移进
     * 侧缝，不是斜着切过去。
     *
     * <p>非零车道只认**站着起跳**（lead = 0）：车道点是窄口径站位，执行
     * 侧不给它贴沿预走（窄面上前挪会滑出侧沿），图这边就不该拿贴沿档去
     * 放行一条她跳不出来的弧。图与执行同源，这一条不能松。
     *
     * <p>落点也按车道判：非零车道的落点本来就故意偏出格心，落格判会把
     * 每一条都错杀——用执行侧真在用的**到达语义**（与瞄点的距离）。
     */
    private static boolean flies(
            BlockGetter level,
            double fromCenterX,
            double fromFloor,
            double fromCenterZ,
            double dirX,
            double dirZ,
            double aimX,
            double aimZ,
            int band,
            double lane,
            double width,
            double height,
            int toX,
            double toFloor,
            int toZ
    ) {
        double offX = -dirZ * lane;
        double offZ = dirX * lane;
        // 车道点得站得住：身位塞得进只说明"没撞上"（贴边锚第一版正是栽
        // 在这儿），让物理答——原地站三 tick，掉下去就不算。
        if (lane != 0.0D && SweptMotion.dropAhead(level,
                new Vec3(fromCenterX + offX, fromFloor, fromCenterZ + offZ),
                0.0D, 0.0D, width, height, SETTLE_TICKS) > 0.1D) {
            return false;
        }
        double[] leads = lane == 0.0D
                ? new double[]{BRINK_LEAD, 0.0D}
                : new double[]{0.0D};
        for (double lead : leads) {
            Vec3 start = new Vec3(
                    fromCenterX + offX + dirX * lead,
                    fromFloor,
                    fromCenterZ + offZ + dirZ * lead);
            // 贴沿前探可能把起点推进障碍体内（锚心 8.5 + 0.4 正落在柱心
            // 上）。嵌着起跳这一档不作数——仿真从墙里出发是答不出阻挡
            // 的，放行的会是一条穿墙的假边。
            if (!SweptMotion.bodyClear(level, start, width, height)) {
                continue;
            }
            double runX = aimX + offX - start.x;
            double runZ = aimZ + offZ - start.z;
            double distance = Math.hypot(runX, runZ);
            if (distance < 1.0E-6D) {
                continue;
            }
            double speed = LeapContract.launchSpeed(distance, band);
            SweptMotion.Flight flight = SweptMotion.fly(
                    level, start,
                    new Vec3(runX / distance * speed,
                            LeapContract.JUMP_RISE, runZ / distance * speed),
                    width, height, true);
            // PERCHED（窄立足：柱顶、烛顶）照收——onGround 物理成立就是
            // 合法落点；"栖在尖上必摔"是图不认站位年代的自救乱舞，不是
            // 站不住（玩家实测点名：跑酷图的柱顶就是要踩的中继）。
            if (flight.outcome() == SweptMotion.Outcome.AIRBORNE) {
                continue;
            }
            Vec3 end = flight.end();
            if (Math.abs(end.y - toFloor) > LANDING_SLACK) {
                continue;
            }
            boolean landed = lane == 0.0D
                    ? (int) Math.floor(end.x) == toX
                            && (int) Math.floor(end.z) == toZ
                    : Math.hypot(end.x - (aimX + offX),
                            end.z - (aimZ + offZ)) <= ARRIVAL_REACH;
            if (landed) {
                return true;
            }
        }
        return false;
    }
}