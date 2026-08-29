package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import com.laixia.maidintelligence.feature.behavior.tlm.pathing.sweep.SweptAcceptance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;

/**
 * 腾空的边族：跳（同层、上一格、落进低洼）、斜跳、下崖——凡是要离地的
 * 边都在这儿连。
 *
 * <p>从 {@code SafeFootingNodeEvaluator} 按职责拆出（单文件五百行的布局
 * 纪律），并把原 {@code BrinkEdges} 并入——下崖与跳同属"腾空"，验收也
 * 终将同一个内核。
 *
 * <p>**连边两道门**：手工判据（可走格、头顶空、地板差）是候选粗筛，快，
 * 剪掉绝大多数不可能；粗筛点头的每一条跳边再交扫掠仿真终审
 * （{@link SweptAcceptance}）——执行侧同一股初速在真实碰撞里飞一遍，落
 * 进目标格才连。粗筛枚举不到的形状（门板缺口、柱尖、压顶的檐）在终审里
 * 天然现形，不必再一个 if 一个 if 地追。
 */
final class AirborneEdges {
    /** 下崖边的定价：折合多走几格路，让 A* 有楼梯先走楼梯。 */
    private static final float DROP_OFF_MALUS = 3.0F;

    /** 极限跨度（超出常规三格）的定价：物理够得着，但起跳车道往往贴着
     *  沿口，一格宽台条上执行风险高——有中继或绕路时让 A* 先挑稳的
     *  （中继钉实测：跨四直飞比烛顶中继便宜半格，A* 弃中继选直飞，回程
     *  起跳对齐把她挤出台沿）。没有替代时这条边照用。 */
    private static final float STRETCH_MALUS = 3.0F;

    private final SafeFootingNodeEvaluator owner;
    private final TightEdges tight;

    AirborneEdges(SafeFootingNodeEvaluator owner, TightEdges tight) {
        this.owner = owner;
        this.tight = tight;
    }

    /** 终审：这条跳边按执行侧的初速仿真，真落得进目标格才放行。 */
    private boolean accepted(Node node, int x, int y, int z) {
        return SweptAcceptance.jumpAccepted(
                owner.world(),
                node.x + 0.5D,
                owner.floorAt(node.asBlockPos()),
                node.z + 0.5D,
                x,
                owner.floorAt(new BlockPos(x, y, z)),
                z,
                owner.body().getBbWidth(),
                owner.body().getBbHeight());
    }

    /** 这个方向上跳得到的落点们，写进邻居数组，返回新的计数。 */
    int jumpLandings(
            Node[] out,
            int count,
            Node node,
            Direction direction
    ) {
        boolean tookTheDip = false;
        for (int reach = 1;
                reach <= SafeFootingNodeEvaluator.MAX_GAP_SPAN + 1; reach++) {
            int x = node.x + reach * direction.getStepX();
            int z = node.z + reach * direction.getStepZ();
            if (owner.walkableCell(x, node.y, z)) {
                if (owner.floorAt(new BlockPos(x, node.y, z))
                        >= node.y - 0.6D) {
                    // 地板贴着走面的真路面：同层落点（相邻格是走路的事）；
                    // 这条方向到此为止。
                    if (reach >= 2
                            && owner.airy(owner.world(), x, node.y + 1, z)
                            && accepted(node, x, node.y, z)) {
                        count = owner.emitLanding(out, count, x, node.y, z,
                                reach > SafeFootingNodeEvaluator.MAX_GAP_SPAN
                                        ? STRETCH_MALUS
                                        : 0.5F);
                    }
                    return count;
                }
                // 地板沉进低洼的"可走"格（沉门板的上方格）：弧线的一段，
                // 不是路的尽头。头顶照验，不在这儿落——飞越的落点在后头。
                // 扫描在这儿终止的话，关着的门板反而比开着的更挡跳，玩家
                // 实测的怪相就是它。
                if (!owner.airy(owner.world(), x, node.y + 1, z)
                        || !owner.airy(owner.world(), x, node.y + 2, z)) {
                    return count;
                }
                // **沉得不深的，它自己就是落点。**关着的下半活板门盖在台阶
                // 上时顶面只比走面低 0.81 格——那是一步小落差，不是要飞越
                // 的坑，而"算不算同层落点"的门槛只有 0.6，正好把它漏在中间。
                //
                // 漏掉的后果不是"路差一点"：这条边**根本不存在**，于是她连
                // 第一跳都不起（玩家实测：L 岛高的那一格顶上放一块关着的下
                // 半活板门，她站在出发台上一动不动，路是 1 nodes/不可达）。
                //
                // 照旧继续往外扫：更远处若有真正的同层落点，两条边都交给
                // A* 按总价挑——落在门板上还是飞越过去，由它定。
                if (reach >= 2 && !tookTheDip
                        && owner.floorAt(new BlockPos(x, node.y, z))
                                >= node.y - 1.25D
                        && accepted(node, x, node.y, z)) {
                    count = owner.emitLanding(out, count, x, node.y, z);
                    tookTheDip = true;
                }
                continue;
            }
            if (!owner.airy(owner.world(), x, node.y, z)) {
                BlockPos hard = new BlockPos(x, node.y, z);
                // 空中车道：柱类高障（栅栏）旁的侧缝在三维里是空的，弧线
                // 可以从缝里穿——格判死会把"明明有空间"的跳整条掐掉（玩家
                // 实测点名）。缝够身位、头上两格也空，就当弧线的一段继续；
                // 缝里站得住还发一个贴边落点，孤柱格也能当落脚点。
                if (FootingRule.tallAtCenter(owner.world(), hard)
                        && tight.lonePost(hard, direction)
                        && owner.airy(owner.world(), x, node.y + 1, z)
                        && owner.airy(owner.world(), x, node.y + 2, z)) {
                    boolean alongX = direction.getStepX() != 0;
                    boolean stood = reach >= 2
                            && FootingRule.squeezePoint(owner.world(), hard)
                                    != null;
                    if (stood) {
                        count = owner.emitLanding(out, count, x, node.y, z);
                    }
                    double laneOf = FootingRule.squeezeLane(
                            owner.world(), hard, alongX,
                            0.5D + (alongX ? node.z : node.x));
                    if (!Double.isNaN(laneOf)) {
                        continue;
                    }
                    if (stood) {
                        return count;
                    }
                    // 无缝无站位（整石这类）：落回普通墙的上跳逻辑。
                }
                // 撞上真墙（盖得住格心的碰撞）。墙顶若是真立足点、且隔着一到
                // 两格缺口——上一格的落点（跨两格要满推力的加速跳）。落点地
                // 板必须跳得上去：起跳弧顶一格二五，栅栏顶一格五就是够不着
                // 的谎言边——实测她对着柱顶跳到看门狗咬。
                //
                // 贴脸那一格（reach==1）向来交给原版的登阶，可**原版只认
                // WALKABLE**：关着的活板门盖在台阶上时那一格是 TRAPDOOR，
                // 登阶当场拒绝；而这一族又要求 reach>=2。两边都不管，这条
                // 边根本不存在——玩家实测的 L 岛正是这样：去程跳得过去，回
                // 程要从岛的低处迈上那块门板时她一动不动（note=nopath）。
                // 只补原版不认的那一种，免得同一条边发两遍。
                //
                // 只认**真正的矮地板**（关着的下半活板门、地毯这类：自身碰
                // 撞不高于半格且盖得住格心）。第一版放宽成"所有原版不认的可
                // 走格"，当场打伤两条——悬吊门板那条里她照着往上跳，一头撞
                // 在天花板上（t=268，rel y=14.01，天花板 14.00）。宽一分就
                // 会连出她根本站不上去的边。
                BlockPos lid = new BlockPos(x, node.y + 1, z);
                boolean lowLid = reach == 1
                        && owner.getBlockPathType(owner.world(),
                                        x, node.y + 1, z)
                                != BlockPathTypes.WALKABLE
                        && FootingRule.selfFloor(
                                owner.world().getBlockState(lid)
                                        .getCollisionShape(
                                                owner.world(), lid));
                // 反向的另一半：**起跳格自己是门板**时，原版也不往上迈——
                // 从 TRAPDOOR 类型的格子它不出登阶边，而这一族又只在
                // reach>=2 发边，两不管。实机黑匣子：她落上门板后规划器给
                // 出"1 节点、不可达"的残桩，advanced-out 循环到看门狗——路
                // 都没有，执行侧修得再对也轮不到。
                boolean lidTakeoff = reach == 1
                        && node.type == BlockPathTypes.TRAPDOOR;
                if ((reach >= 2 || lowLid || lidTakeoff)
                        && reach <= SafeFootingNodeEvaluator.UP_HOP_MAX_REACH
                        && owner.walkableCell(x, node.y + 1, z)
                        && owner.airy(owner.world(), x, node.y + 2, z)
                        && owner.floorAt(new BlockPos(x, node.y + 1, z))
                                <= node.y + 1.25D
                        && accepted(node, x, node.y + 1, z)) {
                    count = owner.emitLanding(out, count, x, node.y + 1, z);
                }
                return count;
            }
            // 可穿行：弧线的一段，头顶两格必须空，否则整条方向作废。
            if (!owner.airy(owner.world(), x, node.y + 1, z)) {
                // 作废之前先问一句：挡住弧线的那东西，是不是一片**她跳得上
                // 去的檐**？悬空的关门板、贴边台阶就是这种——脚下那一层空
                // 着（檐下面就是虚空），扫描本该一路穿过去，而上一格恰好站
                // 得住人。这条边从前**根本不存在**：撞墙才连上跳，而檐撞不
                // 到，于是她眼里那儿没有路（玩家实测：门板嵌在两格柱的上一
                // 格下半，理论上跳得上去，她却认为不可以）。
                if (reach >= 2
                        && reach <= SafeFootingNodeEvaluator.UP_HOP_MAX_REACH
                        && owner.walkableCell(x, node.y + 1, z)
                        && owner.airy(owner.world(), x, node.y + 2, z)
                        && owner.floorAt(new BlockPos(x, node.y + 1, z))
                                <= node.y + 1.25D
                        && accepted(node, x, node.y + 1, z)) {
                    count = owner.emitLanding(out, count, x, node.y + 1, z);
                }
                return count;
            }
            if (!owner.airy(owner.world(), x, node.y + 2, z)) {
                return count;
            }
            // 弧下一层按高度分两种：顶面贴着走面（半格内）是**平路**——走路
            // 的事，不连跳跃线（执行侧的崖边检测也认它是地板，两侧一致）；
            // 顶面低出走面半格以上是**低洼**（沉在缺口里的关门板、浅坑），
            // 弧线从上面过是合法跑酷，照连。只用"有没有"判会把低洼里的孤板
            // 当成路，跳跃线被掐死而孤板又连不成路，她两头不是。
            if (FootingRule.coveringTopAt(owner.world(),
                    new BlockPos(x, node.y - 1, z)) >= node.y - 0.6D) {
                return count;
            }
            // 这格下面一层若是真立足点（脚下再低一格有地板的坑），就是下一格
            // 的落点。记最近的一个，但继续扫：更远处可能有同层落点（整个坑
            // 一步跨过），两条边都给 A* ——落进坑里还是飞越坑，按总价定。
            if (reach >= 2 && !tookTheDip
                    && owner.walkableCell(x, node.y - 1, z)
                    && accepted(node, x, node.y - 1, z)) {
                count = owner.emitLanding(out, count, x, node.y - 1, z);
                tookTheDip = true;
            }
        }
        return count;
    }

    /**
     * 斜线跳跃：直线落点被占（栅栏、门框、缺角）时，人会斜一点跳到旁边那
     * 格——只连正轴的跳跃图就是"死板"（玩家点名：栅栏旁边明明有站位，她
     * 不会绕）。落点在主轴二到三格、侧移一格处，同层限定；飞行走廊按真实
     * 弧线采样逐格验空。定价比直线贵半格，直线能走时仍走直线。
     */
    int diagonalLandings(
            Node[] out,
            int count,
            Node node,
            Direction direction
    ) {
        // **对角登阶**（dx=±1、dz=±1、dy=+1）：正轴两侧都被高障封死、唯
        // 一的出路斜上一格时，这是仅有的边——栅栏圈的缺角门槛正是这个形
        // 状（门槛与圈外同高、圈内高一格、两正邻全是栅栏柱），没有它，站
        // 在门槛上的人进不了圈。粗筛只问落点站得住、头顶空；斜弧撞不撞角
        // 上的柱由扫掠仿真终审拿真实碰撞回答。（这条边是在追缺角悬案时补
        // 的；那案的真凶后来查明是邻场越界的屏障墙，但这条边描述的几何在
        // 真实地形里存在，凭自身价值留下。）
        for (int side = -1; side <= 1; side += 2) {
            int dx = direction.getStepX() + side * direction.getStepZ();
            int dz = direction.getStepZ() + side * direction.getStepX();
            int x = node.x + dx;
            int z = node.z + dz;
            if (owner.walkableCell(x, node.y + 1, z)
                    && owner.airy(owner.world(), x, node.y + 2, z)
                    && owner.floorAt(new BlockPos(x, node.y + 1, z))
                            <= node.y + 1.25D
                    && accepted(node, x, node.y + 1, z)) {
                count = owner.emitLanding(out, count, x, node.y + 1, z);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int reach = 2;
                    reach <= SafeFootingNodeEvaluator.MAX_GAP_SPAN; reach++) {
                int dx = reach * direction.getStepX()
                        + side * direction.getStepZ();
                int dz = reach * direction.getStepZ()
                        + side * direction.getStepX();
                int x = node.x + dx;
                int z = node.z + dz;
                if (!owner.walkableCell(x, node.y, z)
                        || owner.floorAt(new BlockPos(x, node.y, z))
                                < node.y - 0.6D
                        || !owner.airy(owner.world(), x, node.y + 1, z)) {
                    continue;
                }
                if (!flightLineClear(node, dx, dz)
                        || !accepted(node, x, node.y, z)) {
                    continue;
                }
                count = owner.emitLanding(out, count, x, node.y, z);
                break;
            }
        }
        return count;
    }

    /**
     * 起跳格心到落点格心的弧线扫过的每一格：身位头位要空，**线下也不能有
     * 贴走面的立足**——线从路面正上方穿过的"跳"其实是走路的事，连了这种
     * 斜边她就会照直线走进旁边的谷角（竖向 V 实测回归）。与执行侧同一条
     * 采样、同一把尺。
     */
    private boolean flightLineClear(Node node, int dx, int dz) {
        int steps = 8 * Math.max(Math.abs(dx), Math.abs(dz));
        int lastX = node.x;
        int lastZ = node.z;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int cx = (int) Math.floor(node.x + 0.5D + dx * t);
            int cz = (int) Math.floor(node.z + 0.5D + dz * t);
            if ((cx == node.x && cz == node.z)
                    || (cx == node.x + dx && cz == node.z + dz)
                    || (cx == lastX && cz == lastZ)) {
                continue;
            }
            lastX = cx;
            lastZ = cz;
            if (!owner.airy(owner.world(), cx, node.y, cz)
                    || !owner.airy(owner.world(), cx, node.y + 1, cz)
                    || FootingRule.coveringTopAt(owner.world(),
                                    new BlockPos(cx, node.y - 1, cz))
                            >= node.y - 0.6D) {
                return false;
            }
        }
        return true;
    }

    /**
     * 下崖跟进的落点：外一格身位头位全空、脚下两格以上无立足（一格内是原版
     * 台阶下行的地盘），往下扫到第一块盖得住格心的地板。干落六格以内、或
     * 落进水里（水面下那格是水就算，深潭不限高度），都连成一条贵三格的边。
     * 实机黑匣子抓到的"可达前沿站桩"缺的就是它：高台尽头的她与目标之间只
     * 隔一段落差，图里没有这条边，A* 的最优解就是原地。
     */
    int dropOffLanding(
            Node[] out,
            int count,
            Node node,
            Direction direction
    ) {
        int x = node.x + direction.getStepX();
        int z = node.z + direction.getStepZ();
        if (!owner.airy(owner.world(), x, node.y, z)
                || !owner.airy(owner.world(), x, node.y + 1, z)) {
            return count;
        }
        // 脚下一格还有立足的话是原版续走的台阶，不归这条边管。
        if (FootingRule.coveringTopAt(owner.world(),
                new BlockPos(x, node.y - 1, z)) > Double.NEGATIVE_INFINITY) {
            return count;
        }
        for (int depth = 2;
                depth <= SafeFootingNodeEvaluator.DROP_MAX; depth++) {
            int y = node.y - depth;
            if (y <= owner.world().getMinBuildHeight()) {
                return count;
            }
            BlockPos floorPos = new BlockPos(x, y - 1, z);
            if (owner.world().getFluidState(floorPos).isSource()) {
                return emitDropLanding(out, count, x, y, z);
            }
            double top = FootingRule.coveringTopAt(owner.world(), floorPos);
            if (top > Double.NEGATIVE_INFINITY) {
                // 地板要贴脚（半格内），沉得更深的等下一轮扫描去接。干落
                // 再过一道终审：迈出去的那条自由落体真落进这一格才连边
                // （柱尖、半路的檐都在仿真里现形）。
                return top >= y - 0.6D
                                && SweptAcceptance.dropAccepted(
                                        owner.world(),
                                        node.x + 0.5D,
                                        owner.floorAt(node.asBlockPos()),
                                        node.z + 0.5D,
                                        x, owner.floorAt(new BlockPos(x, y, z)),
                                        z,
                                        owner.body().getBbWidth(),
                                        owner.body().getBbHeight())
                        ? emitDropLanding(out, count, x, y, z)
                        : count;
            }
        }
        return count;
    }

    /** 下崖落点定型：可走、贵三格。落点自身的可走性照常验。 */
    private int emitDropLanding(Node[] out, int count, int x, int y, int z) {
        if (!owner.walkableCell(x, y, z)
                && !owner.world().getFluidState(new BlockPos(x, y - 1, z))
                        .isSource()) {
            return count;
        }
        Node landing = owner.nodeAt(x, y, z);
        if (landing == null || landing.closed) {
            return count;
        }
        landing.type = BlockPathTypes.WALKABLE;
        landing.costMalus = Math.max(landing.costMalus, DROP_OFF_MALUS);
        if (count < out.length) {
            out[count++] = landing;
        }
        return count;
    }
}
