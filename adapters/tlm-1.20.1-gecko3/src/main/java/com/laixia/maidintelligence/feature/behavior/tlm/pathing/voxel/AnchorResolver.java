package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * 锚点解析：这一格的高度带里，她能站的支撑面在哪、站得下几个位置。
 *
 * <p>**发现的单位是碰撞盒，不是方块**：任何方块（含模组的任意组合形状）
 * 都摊开成盒集，每个盒的顶面就是一片候选支撑——顶面落在本格高度带
 * [y, y+1) 内的归本格（归属律由此自动成立，不再是背下来的规则）。可站与
 * 否只问三件事：有没有实面（短边够脚踩）、身位箱放不放得下、面积——"这
 * 格是什么方块"这句话在整个解析里问不出口。
 *
 * <p>**一格可以有几个站位**：栅栏柱只占格心那四分之一，贴边的缝塞得下
 * 身位（玩家点名的体素化诉求："柱只占中间四分之一，像玩家一样贴边绕过
 * 去，身子悬在道外也算数"）。格心站不下时就在支撑面上采样，把真正放得
 * 下她的位置找出来——它们是贴边锚（{@link Anchor.Kind#EDGE}）。净空用
 * **身位箱**判，不是拿一个点去问：能不能挤过去，取决于她多宽。
 */
public final class AnchorResolver {

    /** 脚下要有的最小实面：短边低于此宽连尖都算不上，踩不住。 */
    private static final double LEAST_FOOTING = 0.05D;

    /** 贴边采样的步长：柱只占中间四分之一，四分之一格的步长足够摸到
     *  两侧的缝，又不至于把每格的解析变成上百次身位箱扫描。 */
    private static final double PROBE_STEP = 0.25D;

    /** 一面最多采这么多点：形状再刁钻也不该让一格的解析没有上限。 */
    private static final int PROBE_CAP = 25;

    /**
     * 贴边锚（挤缝）总开关——**暂闭**。
     *
     * <p>三案（窄道柱、柱旁车道、崖沿柱）确实靠它第一次转绿，可它同时
     * 把栅栏圈毁了：墙格里那条 0.375 的缝也"站得住"，她挤进去、走到两
     * 面墙交汇的角柱格，就再也出不来（实测 rel 8.07/2.93，note=walk、
     * path=null，整场只下过七次单——行为层都放弃了）。
     *
     * <p>补过三道约束都没挡住：站定预演（缝确实站得稳）、不做诚实前沿
     * （她是穿行进去的）、挤缝加价（无路可走时再贵也得走）。说明缺的不
     * 是约束，是**结构**：缝该成对出现、并且必须连通两侧的正经站位，才
     * 算一条车道；就地采样出"一个能站的点"远远不够。等那张车道图做出
     * 来再开回来。 */
    private static final boolean EDGE_ANCHORS = false;

    /** 脚底往下认多深算"托着她"：贴脚小落差与半砖的边沿都在这一段里。 */
    private static final double FOOTING_REACH = 0.55D;

    /** 站定预演推几 tick：脚下真有支撑的话，一 tick 就该停住。 */
    private static final int SETTLE_TICKS = 3;

    private AnchorResolver() {
    }

    /** 这一格的主锚点（面积最大的那个）；站不了人返回 null。 */
    public static Anchor resolve(BlockGetter level, BlockPos cell,
            double width, double height) {
        Anchor best = null;
        for (Anchor found : resolveAll(level, cell, width, height)) {
            if (best == null || area(found) > area(best)) {
                best = found;
            }
        }
        return best;
    }

    /** 兼容口径：按默认身位（0.6×1.8）解析。 */
    public static Anchor resolve(BlockGetter level, BlockPos cell) {
        return resolve(level, cell, 0.6D, 1.8D);
    }

    /**
     * 她此刻的立足锚：先自己的格，再脚下真实支撑，最后贴身邻格。
     *
     * <p>三级次序都是拿场景换来的。中心格解不出锚**不等于她没站着**——
     * 身位跨在格界上时（柱底座的边沿、烛占着格心的那一格），托着她的那
     * 片面在邻格里；起点若直接退到邻格，回锚当场判她走丢，把站在沿上的
     * 人往回推下去（末地烛中继：起点退到 1.3 格外的东台，regroup b0.79
     * 六 tick 内推她出沿）。
     *
     * <p>但这一档只认**支撑跨在格界外**的情形。中心格解不出锚有两种来
     * 处：支撑在邻格（她跨在格界上，脚下那片面正是答案），和**净空不够**
     * （头顶压着东西，那儿本来就站不了人）。后者用 standingOn 会造出一
     * 个假锚——围栏角实测（四副本齐红）：场沿一格高的角落被两层屏障压
     * 着，假锚让她原地下单四百多次、一步没挪。支撑就在自己格里的，说明
     * 拦住她的不是格界而是净空，那就老实退回邻格。
     *
     * <p>再加一道：脚下这片面还得**在图上连得出边**。她站在哪是一回事，
     * 从那儿走不走得动是另一回事；连不出边的锚等于没有起点。
     *
     * @param web 边供给；传 null 表示只问"她站在哪"（诊断口径）
     */
    public static Anchor nearby(BlockGetter level, BlockPos here, AABB body,
            double feet, StrideSupplier web) {
        Anchor mine = resolve(level, here);
        if (mine != null) {
            return mine;
        }
        Anchor underfoot = standingOn(level, body, feet);
        if (underfoot != null && !underfoot.cell().equals(here)
                && (web == null || !web.from(underfoot).isEmpty())) {
            return underfoot;
        }
        for (int dy = 0; dy >= -1; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Anchor near = resolve(level, here.offset(dx, dy, dz));
                    if (near != null) {
                        return near;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 她是不是正踩在 {@code seat} 这片支撑面上——**问物理，不问距离**。
     *
     * <p>窄立足（杆顶、烛顶、柱尖）上"到没到"这个问题，用中心距离是问
     * 错了：末地烛顶只有 0.25 见方，她身位 0.6 根本摆不进去，站上去本
     * 来就是挂着大半个身子。实测（sw147 读数带 46t）她稳稳落在烛顶、
     * onGround 成立，中心却偏出顶面 0.275——判到圈 0.125 够不着，回锚
     * 于是判她"离出发面 3.01"，把站在烛顶的人往回拽。
     *
     * <p>宽面不走这条：那里她本来就该走到中心去，站上边沿就宣布到站会
     * 让转弯提前发生，一步出沿（横烛桥转角实测三红）。
     */
    public static boolean seatedOn(BlockGetter level, AABB body, double feet,
            Anchor seat) {
        if (Math.abs(seat.at().y - feet) > 0.1D) {
            return false;
        }
        Anchor here = standingOn(level, body, feet);
        if (here == null) {
            return false;
        }
        AABB mine = here.stand();
        AABB theirs = seat.stand();
        return mine.minX < theirs.maxX + 1.0E-3D
                && mine.maxX > theirs.minX - 1.0E-3D
                && mine.minZ < theirs.maxZ + 1.0E-3D
                && mine.maxZ > theirs.minZ - 1.0E-3D;
    }

    /**
     * 她**此刻站在什么上面**——不问"站不站得下"，物理已经替她答过了。
     *
     * <p>{@link #resolveAll} 回答的是"这一格能不能站人"，要过身位箱那一
     * 关；起点解析问的却是另一件事：她人已经在那儿了，图只需如实记下她
     * 的立足点。两者混用的代价实测过（末地烛中继）：她从烛顶滑到柱底座
     * 的东边缘，底座那格因为烛占着格心解不出锚，起点于是退到一格三之外
     * 的东台——回锚判她"走丢 0.79"，把她从只剩 0.1 格支撑的沿上一路推
     * 下悬崖（读数带 110t..116t）。
     *
     * <p>代表点用**她自己的位置**，不是支撑面中心：起点问的是"她在
     * 哪"，挪到面心就等于凭空替她走了一步。
     *
     * @param body 她的碰撞箱
     * @param feet 脚底高度
     */
    public static Anchor standingOn(BlockGetter level, AABB body,
            double feet) {
        AABB best = null;
        int lowest = Mth.floor(feet - FOOTING_REACH);
        int highest = Mth.floor(feet + 0.05D);
        for (int x = Mth.floor(body.minX); x <= Mth.floor(body.maxX); x++) {
            for (int z = Mth.floor(body.minZ); z <= Mth.floor(body.maxZ);
                    z++) {
                for (int y = highest; y >= lowest; y--) {
                    BlockPos at = new BlockPos(x, y, z);
                    VoxelShape shape = level.getBlockState(at)
                            .getCollisionShape(level, at);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    for (AABB box : shape.toAabbs()) {
                        AABB placed = box.move(x, y, z);
                        // 托着她的面：顶面贴着脚底，且水平真压在身位箱下。
                        if (placed.maxY > feet + 0.05D
                                || placed.maxY < feet - FOOTING_REACH
                                || placed.maxX <= body.minX
                                || placed.minX >= body.maxX
                                || placed.maxZ <= body.minZ
                                || placed.minZ >= body.maxZ) {
                            continue;
                        }
                        if (best == null || placed.maxY > best.maxY) {
                            best = placed;
                        }
                    }
                }
            }
        }
        if (best == null) {
            return null;
        }
        double breadth = Math.min(best.maxX - best.minX,
                best.maxZ - best.minZ);
        // 索引格按**支撑面**归，不按她的中心：她跨在格界上时中心那格是
        // 空的，而边生成是从索引格向四邻找目标——用空格当起点，图会把
        // "空格 → 邻台"当成一条走边连起来，中间那格根本没有地板。末地
        // 烛中继实测（sw149 117t）：她站在柱底座西沿 x=4.9，路径铺成
        // (4,10,2)→(3,10,2)，她照着往西走，两步就走进缺口摔下去。按支
        // 撑归格，同一处出来的是"从底座跳到西台"，那才是真路。
        double seatX = (best.minX + best.maxX) / 2.0D;
        double seatZ = (best.minZ + best.maxZ) / 2.0D;
        return new Anchor(
                new Vec3(body.getCenter().x, best.maxY, body.getCenter().z),
                breadth,
                breadth < 0.5D ? Anchor.Kind.NARROW : Anchor.Kind.FLOOR,
                new AABB(best.minX, best.maxY, best.minZ,
                        best.maxX, best.maxY, best.maxZ),
                BlockPos.containing(seatX, best.maxY + 0.05D, seatZ));
    }

    /**
     * 这一格所有站得住人的位置：格心站得下就是它一个；站不下（柱、门板
     * 立在格心）就沿支撑面找贴边的缝，最多两个（柱的两侧）。
     */
    public static List<Anchor> resolveAll(BlockGetter level, BlockPos cell,
            double width, double height) {
        List<Anchor> found = new ArrayList<>(2);
        for (BlockPos host : new BlockPos[]{cell, cell.below()}) {
            VoxelShape shape = level.getBlockState(host)
                    .getCollisionShape(level, host);
            if (shape.isEmpty()) {
                continue;
            }
            for (AABB box : shape.toAabbs()) {
                standOn(level, cell, box.move(
                        host.getX(), host.getY(), host.getZ()),
                        width, height, found);
            }
        }
        return found;
    }

    /** 这个碰撞盒的顶面能不能站人、站哪；找到的都加进 {@code out}。 */
    private static void standOn(BlockGetter level, BlockPos cell,
            AABB placed, double width, double height, List<Anchor> out) {
        double top = placed.maxY;
        // 顶面在本格高度带内才归本格：低了归下格，高了归上格。
        if (top < cell.getY() - 1.0E-3D
                || top >= cell.getY() + 1.0D - 1.0E-6D) {
            return;
        }
        double spanX = placed.maxX - placed.minX;
        double spanZ = placed.maxZ - placed.minZ;
        if (Math.min(spanX, spanZ) < LEAST_FOOTING) {
            return;
        }
        double breadth = Math.min(spanX, spanZ);
        AABB stand = new AABB(placed.minX, top, placed.minZ,
                placed.maxX, top, placed.maxZ);
        double cx = (placed.minX + placed.maxX) / 2.0D;
        double cz = (placed.minZ + placed.maxZ) / 2.0D;
        if (bodyFits(level, cx, top, cz, width, height)) {
            out.add(new Anchor(new Vec3(cx, top, cz), breadth,
                    breadth < 0.5D ? Anchor.Kind.NARROW : Anchor.Kind.FLOOR,
                    stand));
            return;
        }
        // 格心被占（柱、立门板）：沿支撑面找放得下身位的贴边位置。柱只
        // 占中间四分之一，两侧的缝各留得下人——最多收两个，对侧优先，
        // 免得两个锚挤在同一条缝里。
        if (!EDGE_ANCHORS) {
            return;
        }
        Anchor first = null;
        int probes = 0;
        for (double dx = -spanX / 2.0D; dx <= spanX / 2.0D;
                dx += PROBE_STEP) {
            for (double dz = -spanZ / 2.0D; dz <= spanZ / 2.0D;
                    dz += PROBE_STEP) {
                if (++probes > PROBE_CAP) {
                    return;
                }
                double px = cx + dx;
                double pz = cz + dz;
                if (!bodyFits(level, px, top, pz, width, height)) {
                    continue;
                }
                // **站得稳才算站位**：身位塞得进只说明"没撞上"，说不了
                // "站得住"——贴边锚第一版就栽在这儿（采样点落在支撑面
                // 外沿、身位悬在空处，她走上去就掉）。让物理自己回答：
                // 原地站三 tick，掉下去就不算数。
                if (com.laixia.maidintelligence.feature.behavior.tlm.pathing
                        .sweep.SweptMotion.dropAhead(level,
                                new Vec3(px, top, pz), 0.0D, 0.0D,
                                width, height, SETTLE_TICKS) > 0.1D) {
                    continue;
                }
                Anchor edge = new Anchor(new Vec3(px, top, pz), breadth,
                        Anchor.Kind.EDGE, stand);
                if (first == null) {
                    first = edge;
                    out.add(edge);
                } else if (Math.hypot(px - first.at().x, pz - first.at().z)
                        > 0.5D) {
                    out.add(edge);
                    return;
                }
            }
        }
    }

    /**
     * 把已经解出来的站位**按车道挪一挪**：面上离她最近、身位又放得下
     * 的那个点。
     *
     * <p>挤缝不是一格的事，是**一条车道**：柱占了格心，她贴着边过去，
     * 下一格若只认格心，那条"贴边→格心"的斜线又切回柱上（窄道栅栏柱实
     * 测：贴边锚有了，边仍然连不出去）。
     *
     * <p>入参是**解析好的**站位，不在这里重解——重解一次要把整面采样
     * 一遍（格心被占时上百次身位箱扫描），而邻域查询每格要问好几遍：
     * 上一版在这里绕过了边网的缓存，服务器当场卡死两个半小时。
     */
    public static Anchor lane(BlockGetter level, Anchor face,
            double fromX, double fromZ, double width, double height) {
        AABB pad = face.stand();
        double px = Math.max(pad.minX, Math.min(pad.maxX, fromX));
        double pz = Math.max(pad.minZ, Math.min(pad.maxZ, fromZ));
        if (Math.abs(px - face.at().x) < 1.0E-3D
                && Math.abs(pz - face.at().z) < 1.0E-3D) {
            return face;
        }
        if (!bodyFits(level, px, pad.minY, pz, width, height)) {
            return face;
        }
        // 索引格沿用原面：按车道挪过的站位可能落进邻格，账本记的却该是
        // 这一格。
        return new Anchor(new Vec3(px, pad.minY, pz), face.breadth(),
                face.kind(), pad, face.cell());
    }

    /**
     * 她的**身位箱**放不放得下：脚点站这儿，从脚面到头顶不许与任何真实
     * 碰撞相交。点判会说"柱旁边这一点是空的"，可她有宽度——挤缝是宽度
     * 问题，不是点的问题。
     */
    private static boolean bodyFits(BlockGetter level, double x, double footY,
            double z, double width, double height) {
        double half = width / 2.0D;
        AABB body = new AABB(x - half, footY + 0.02D, z - half,
                x + half, footY + height, z + half);
        BlockPos lo = BlockPos.containing(body.minX, body.minY, body.minZ);
        BlockPos hi = BlockPos.containing(body.maxX, body.maxY, body.maxZ);
        for (BlockPos at : BlockPos.betweenClosed(lo, hi)) {
            VoxelShape shape = level.getBlockState(at)
                    .getCollisionShape(level, at);
            if (shape.isEmpty()) {
                continue;
            }
            // **先用外包盒粗筛**：{@code toAabbs} 每次都要新建一张表，
            // 而这个方法在每格解析里都跑——细拆只留给真正挨着她的那几
            // 块（多锚点上线后整轮二十二分钟跑不完一条测试，就是每块都
            // 细拆拆出来的）。
            AABB rough = shape.bounds().move(
                    at.getX(), at.getY(), at.getZ());
            if (!rough.intersects(body)) {
                continue;
            }
            BlockPos frozen = at.immutable();
            for (AABB box : shape.toAabbs()) {
                if (box.move(frozen.getX(), frozen.getY(), frozen.getZ())
                        .intersects(body)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double area(Anchor anchor) {
        return (anchor.stand().maxX - anchor.stand().minX)
                * (anchor.stand().maxZ - anchor.stand().minZ);
    }
}
