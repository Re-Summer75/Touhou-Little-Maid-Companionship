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
        int band = (int) Math.round(toFloor - fromFloor);
        for (double lead : new double[]{BRINK_LEAD, 0.0D}) {
            Vec3 start = new Vec3(
                    fromCenterX + dirX * lead,
                    fromFloor,
                    fromCenterZ + dirZ * lead);
            // 贴沿前探可能把起点推进障碍体内（锚心 8.5 + 0.4 正落在柱心
            // 上）。嵌着起跳这一档不作数——仿真从墙里出发是答不出阻挡
            // 的，放行的会是一条穿墙的假边。
            if (!SweptMotion.bodyClear(level, start, width, height)) {
                continue;
            }
            double distance = Math.hypot(aimX - start.x, aimZ - start.z);
            double speed = LeapContract.launchSpeed(distance, band);
            SweptMotion.Flight flight = SweptMotion.fly(
                    level, start,
                    new Vec3(dirX * speed, LeapContract.JUMP_RISE,
                            dirZ * speed),
                    width, height, true);
            // PERCHED（窄立足：柱顶、烛顶）照收——onGround 物理成立就是
            // 合法落点；"栖在尖上必摔"是图不认站位年代的自救乱舞，不是
            // 站不住（玩家实测点名：跑酷图的柱顶就是要踩的中继）。
            if (flight.outcome() == SweptMotion.Outcome.AIRBORNE) {
                continue;
            }
            Vec3 end = flight.end();
            if ((int) Math.floor(end.x) == toX
                    && (int) Math.floor(end.z) == toZ
                    && Math.abs(end.y - toFloor) <= LANDING_SLACK) {
                return true;
            }
        }
        return false;
    }
}
