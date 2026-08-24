package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidNodeEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 立足点必须真实存在——补上寻路分类里那个从不查地板的口子。
 *
 * <p>原版的节点分类只对 {@code OPEN}（空气）格执行"下面有没有地板"的检查；
 * {@code TRAPDOOR} 与 {@code DOOR_OPEN} 这两类"可通行"格**直接被当成立足点**，
 * 下面是深渊也照走。刷怪塔骗怪跳崖用的就是这一手，而她吃同一套判定。实测一比一
 * 复现：八格桥打掉中间两格、缺口里放开着的活板门，她径直走进去摔下去；缺口那两格
 * 的分类读出来就是 {@code TRAPDOOR/TRAPDOOR}。
 *
 * <p>规则一句话：这两类格子要么**自己站得住**（自身碰撞非空且顶面不高于半格——
 * 关着的下半活板门就是合法的桥面），要么**下面有任何碰撞可踩**；两样都没有，
 * 它就是空气（{@code OPEN}），交回给原版对空气的正常处理——没有地板就不成路。
 *
 * <p>刻意只动这两类，不加广义的"顶面结实"检查：台阶、楼梯、地毯的可走性由原版
 * 语义负责，广义检查会把它们误伤成不可走。
 */
public class SafeFootingNodeEvaluator extends MaidNodeEvaluator {

    /**
     * 跨缺口的代价，折成"额外走几格路"。
     *
     * <p>**接近零，刻意的。**A* 把跳跃连线和所有绕行路线一起比总价，这个数只做
     * 平手裁决：路线一样长时宁可脚踏实地。此前定过四（绕路六格以内优先绕），按
     * "能跳就跳、绕路是多余的路径"的要求反转成距离最优——跑酷更近就跑酷。缺口
     * 越宽距离本身越贵（2/3/4 格），窄跳优先于宽跳仍然自动成立。
     */
    private static final float GAP_JUMP_MALUS = 0.5F;

    /**
     * 最宽跨三格。
     *
     * <p>这是人形身体的物理极限，不是保守值：玩家满冲刺贴边起跳也就跨三格。她的
     * 起跳推力（{@code SureFootedNavigation} 的执行侧）按落点距离配速，最远对齐
     * 这个极限；四格宽的缺口推力上限也够不着，仍然拒走。包内共享：执行侧起跳前
     * 重验这段边时用同一个上限。
     */
    static final int MAX_GAP_SPAN = 3;

    /**
     * 上一格的落点最远连跨两格缺口的（落点距离三）。
     *
     * <p>升到一格高只有起跳后头七八 tick，射程天然比同层短一截；跨两格上一格
     * 要满推力的加速跳——起跳推力上限就是冲刺量级，第八 tick 内带着一格多的
     * 升幅前进两格七，正好够着。跨三格还上一格连满推力也赶不到，不连。包内
     * 共享：执行侧核对这段边时用同一个数。
     */
    static final int UP_HOP_MAX_REACH = 3;

    /**
     * 干落最深容忍六格：迈出去的代价是几颗心以内的擦伤，换来的是不再被
     * 高台钉死。落水不限深——水接住一切。包内共享：执行侧核对同一个数。
     */
    static final int DROP_MAX = 6;

    /** 钻缝那一族（贴边挤过、贴角斜穿）：判据同源，单独一处。 */
    private final TightEdges tight = new TightEdges(this);

    /** 下崖那一族：判据同源，单独一处。 */
    private final BrinkEdges brink = new BrinkEdges(this);

    /**
     * 最近一次搜索里，**起点那一格发出了几个邻居**。
     *
     * <p>"一节点、不可达"在原版 A* 里只有一个成因：起点一个邻居都没发出
     * 来。而这个形状（advanced-out）今晚在四条红里出现过，隔离测同一格却
     * 每次都铺得出路——三轮逐格诊断（栅栏圈 25 格、缺角对角线、梁上 8 格）
     * 全绿。差别只能在她当时的状态里，可我为此猜过预算、猜过属性、猜过实
     * 例，全错。
     *
     * <p>与其继续猜，把这个数直接量出来：是 0 就坐实"起点发不出边"，非 0
     * 就说明残路另有成因。两条路当场分开，不用再赌。
     */
    private int startNeighbours = -1;

    private Node searchStart;

    /** 起点邻居数；还没搜过时为 -1。 */
    public int startNeighbours() {
        return startNeighbours;
    }

    /**
     * 起点要落在**真正托着她脚的那一格**上。
     *
     * <p>原版落地时取 {@code floor(y + 0.5)}，这条规则默认地板要么在整数高
     * 度、要么不高过半格——台阶（0.5）、下半活板门（0.1875）、实心方块都成
     * 立。**栅栏和墙是一格半**，顶面落在 4.5 这种半格线上，前提当场就破了：
     * 她站在栅栏顶上时 {@code floor(4.5 + 0.5) = 5}，可托着她的是 y=4 那一格
     * （{@code getFloorLevel} 给的正是 3 + 1.5 = 4.5，与她脚下分毫不差）。
     *
     * <p>差这一格的后果不是"路差一点"，是**整条路从她头顶正上方的幽灵格起
     * 步**：执行侧按自己那一格算出 dy=+1，于是每一 tick 都判成"要往上爬"，
     * 登阶分支连爬四百二十七次也爬不进一个不存在的落脚点，人就钉死在栅栏顶
     * 上（栅栏圈实测；同一刻的现场复铺给出六节点可达路，证明图本身是好的
     * ——错的只有起点）。
     *
     * <p>判据不猜，只问一句：哪一格的地板高度**正好等于她脚下的高度**。两
     * 种取法一致时照旧走原版，不一致时才以这一句为准。
     */
    @Override
    public Node getStart() {
        startNeighbours = -1;
        Node vanilla = super.getStart();
        searchStart = vanilla;
        if (vanilla == null || !this.mob.onGround()) {
            return vanilla;
        }
        int footing = Mth.floor(this.mob.getY());
        if (footing == vanilla.y) {
            return vanilla;
        }
        double feet = this.mob.getY();
        if (Math.abs(getFloorLevel(vanilla.asBlockPos()) - feet) <= FOOTING_SLACK) {
            return vanilla;
        }
        BlockPos mine = new BlockPos(vanilla.x, footing, vanilla.z);
        if (Math.abs(getFloorLevel(mine) - feet) <= FOOTING_SLACK) {
            searchStart = getStartNode(mine);
            return searchStart;
        }
        return vanilla;
    }

    /** 判"这一格的地板正好托着她"的容差：一分格都不到。 */
    private static final double FOOTING_SLACK = 0.05D;

    /**
     * 能跳多远跳多远，而且不止同层：沿每个方向扫过去，产出至多三种落点——最近
     * 的同层（跨一到三格）、上一格（只跨一格）、下一格（跨一到三格）——全部
     * 交给 A* 按总距离比价。中途撞上墙就断（墙顶若站得住，那就是上一格的落点）；
     * 弧线头顶不空也断——跳跃不穿墙。
     *
     * <p>另有一族**下崖边**：外一格、直落两格以上（原版续走的坠落上限只有三，
     * 这里干落容到六、落水不限）。实机黑匣子抓到的"可达前沿站桩"就是缺它：
     * 高台尽头的她与目标之间只隔一段落差，图里没有这条边，A* 的最优解就是
     * 原地，外观是"站在活板门/边缘上不知道该跳"（玩家三连报）。
     */
    @Override
    public int getNeighbors(Node[] outputArray, Node node) {
        int count = super.getNeighbors(outputArray, node);
        count = tight.cornerCuts(outputArray, count, node);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            count = brink.dropOffLanding(outputArray, count, node, direction);
        }
        // 起跳格自己的头顶两格要空：跳起来的那一下发生在自己的柱子里。
        if (!airy(this.level, node.x, node.y + 1, node.z)
                || !airy(this.level, node.x, node.y + 2, node.z)) {
            return census(node, count);
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            count = jumpLandings(outputArray, count, node, direction);
            count = diagonalLandings(outputArray, count, node, direction);
            count = tight.squeezeLanding(outputArray, count, node, direction);
        }
        return census(node, count);
    }

    /** 拆出去的边族要取节点：getNode 是跨包 protected，包内开一扇门。 */
    Node nodeAt(int x, int y, int z) {
        return this.getNode(x, y, z);
    }

    /** 起点那一格的邻居数留个底；别的格子照原样返回。 */
    private int census(Node node, int count) {
        if (node == searchStart) {
            startNeighbours = count;
        }
        return count;
    }


    /**
     * 斜线跳跃：直线落点被占（栅栏、门框、缺角）时，人会斜一点跳到旁边那
     * 格——只连正轴的跳跃图就是"死板"（玩家点名：栅栏旁边明明有站位，她
     * 不会绕）。落点在主轴二到三格、侧移一格处，同层限定；飞行走廊按真实
     * 弧线采样逐格验空。定价比直线贵半格，直线能走时仍走直线。
     */
    private int diagonalLandings(
            Node[] out,
            int count,
            Node node,
            Direction direction
    ) {
        for (int side = -1; side <= 1; side += 2) {
            for (int reach = 2; reach <= MAX_GAP_SPAN; reach++) {
                int dx = reach * direction.getStepX()
                        + side * direction.getStepZ();
                int dz = reach * direction.getStepZ()
                        + side * direction.getStepX();
                int x = node.x + dx;
                int z = node.z + dz;
                if (!walkableCell(x, node.y, z)
                        || this.getFloorLevel(new BlockPos(x, node.y, z))
                                < node.y - 0.6D
                        || !airy(this.level, x, node.y + 1, z)) {
                    continue;
                }
                if (!flightLineClear(node, dx, dz)) {
                    continue;
                }
                count = emitLanding(out, count, x, node.y, z);
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
            if (!airy(this.level, cx, node.y, cz)
                    || !airy(this.level, cx, node.y + 1, cz)
                    || FootingRule.coveringTopAt(this.level,
                                    new BlockPos(cx, node.y - 1, cz))
                            >= node.y - 0.6D) {
                return false;
            }
        }
        return true;
    }



    /**
     * 这格是不是"能站人的路面"——跳跃扫描的认路尺。
     *
     * <p>不能只认 WALKABLE 枚举：关着的下半活板门是 TRAPDOOR、地毯这类矮板
     * 各有各的类型，**按枚举认路等于"路面必须是普通方块"**——玩家实测：跑酷
     * 线上放一片活板门（开关皆然）整条线就断，而台阶楼梯（枚举恰好 WALKABLE）
     * 没事。物理的尺只有一把：类型可走，或这格自己就是贴脚的矮地板。
     */

    boolean walkableCell(int x, int y, int z) {
        BlockPathTypes type = this.getBlockPathType(this.level, x, y, z);
        if (type == BlockPathTypes.WALKABLE
                || type == BlockPathTypes.TRAPDOOR) {
            return true;
        }
        BlockPos pos = new BlockPos(x, y, z);
        return FootingRule.selfFloor(this.level.getBlockState(pos)
                .getCollisionShape(this.level, pos));
    }

    /** 这个方向上跳得到的落点们，写进邻居数组，返回新的计数。 */
    private int jumpLandings(
            Node[] out,
            int count,
            Node node,
            Direction direction
    ) {
        boolean tookTheDip = false;
        for (int reach = 1; reach <= MAX_GAP_SPAN + 1; reach++) {
            int x = node.x + reach * direction.getStepX();
            int z = node.z + reach * direction.getStepZ();
            if (walkableCell(x, node.y, z)) {
                if (this.getFloorLevel(new BlockPos(x, node.y, z))
                        >= node.y - 0.6D) {
                    // 地板贴着走面的真路面：同层落点（相邻格是走路的事）；
                    // 这条方向到此为止。
                    if (reach >= 2 && airy(this.level, x, node.y + 1, z)) {
                        count = emitLanding(out, count, x, node.y, z);
                    }
                    return count;
                }
                // 地板沉进低洼的"可走"格（沉门板的上方格）：弧线的一段，
                // 不是路的尽头。头顶照验，不在这儿落——飞越的落点在后头。
                // 扫描在这儿终止的话，关着的门板反而比开着的更挡跳，玩家
                // 实测的怪相就是它。
                if (!airy(this.level, x, node.y + 1, z)
                        || !airy(this.level, x, node.y + 2, z)) {
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
                        && this.getFloorLevel(new BlockPos(x, node.y, z))
                                >= node.y - 1.25D) {
                    count = emitLanding(out, count, x, node.y, z);
                    tookTheDip = true;
                }
                continue;
            }
            if (!airy(this.level, x, node.y, z)) {
                BlockPos hard = new BlockPos(x, node.y, z);
                // 空中车道：柱类高障（栅栏）旁的侧缝在三维里是空的，弧线
                // 可以从缝里穿——格判死会把"明明有空间"的跳整条掐掉（玩家
                // 实测点名）。缝够身位、头上两格也空，就当弧线的一段继续；
                // 缝里站得住还发一个贴边落点，孤柱格也能当落脚点。
                if (FootingRule.tallAtCenter(this.level, hard)
                        && tight.lonePost(hard, direction)
                        && airy(this.level, x, node.y + 1, z)
                        && airy(this.level, x, node.y + 2, z)) {
                    boolean alongX = direction.getStepX() != 0;
                    boolean stood = reach >= 2
                            && FootingRule.squeezePoint(this.level, hard)
                                    != null;
                    if (stood) {
                        count = emitLanding(out, count, x, node.y, z);
                    }
                    double laneOf = FootingRule.squeezeLane(
                            this.level, hard, alongX,
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
                        && getBlockPathType(this.level, x, node.y + 1, z)
                                != BlockPathTypes.WALKABLE
                        && FootingRule.selfFloor(this.level.getBlockState(lid)
                                .getCollisionShape(this.level, lid));
                // 反向的另一半：**起跳格自己是门板**时，原版也不往上迈——
                // 从 TRAPDOOR 类型的格子它不出登阶边，而这一族又只在
                // reach>=2 发边，两不管。实机黑匣子：她落上门板后规划器给
                // 出"1 节点、不可达"的残桩，advanced-out 循环到看门狗——路
                // 都没有，执行侧修得再对也轮不到。
                boolean lidTakeoff = reach == 1
                        && node.type == BlockPathTypes.TRAPDOOR;
                if ((reach >= 2 || lowLid || lidTakeoff)
                        && reach <= UP_HOP_MAX_REACH
                        && walkableCell(x, node.y + 1, z)
                        && airy(this.level, x, node.y + 2, z)
                        && this.getFloorLevel(new BlockPos(x, node.y + 1, z))
                                <= node.y + 1.25D) {
                    count = emitLanding(out, count, x, node.y + 1, z);
                }
                return count;
            }
            // 可穿行：弧线的一段，头顶两格必须空，否则整条方向作废。
            if (!airy(this.level, x, node.y + 1, z)) {
                // 作废之前先问一句：挡住弧线的那东西，是不是一片**她跳得上
                // 去的檐**？悬空的关门板、贴边台阶就是这种——脚下那一层空
                // 着（檐下面就是虚空），扫描本该一路穿过去，而上一格恰好站
                // 得住人。这条边从前**根本不存在**：撞墙才连上跳，而檐撞不
                // 到，于是她眼里那儿没有路（玩家实测：门板嵌在两格柱的上一
                // 格下半，理论上跳得上去，她却认为不可以）。
                if (reach >= 2 && reach <= UP_HOP_MAX_REACH
                        && walkableCell(x, node.y + 1, z)
                        && airy(this.level, x, node.y + 2, z)
                        && this.getFloorLevel(new BlockPos(x, node.y + 1, z))
                                <= node.y + 1.25D) {
                    count = emitLanding(out, count, x, node.y + 1, z);
                }
                return count;
            }
            if (!airy(this.level, x, node.y + 2, z)) {
                return count;
            }
            // 弧下一层按高度分两种：顶面贴着走面（半格内）是**平路**——走路
            // 的事，不连跳跃线（执行侧的崖边检测也认它是地板，两侧一致）；
            // 顶面低出走面半格以上是**低洼**（沉在缺口里的关门板、浅坑），
            // 弧线从上面过是合法跑酷，照连。只用"有没有"判会把低洼里的孤板
            // 当成路，跳跃线被掐死而孤板又连不成路，她两头不是。
            if (FootingRule.coveringTopAt(this.level, new BlockPos(x, node.y - 1, z))
                    >= node.y - 0.6D) {
                return count;
            }
            // 这格下面一层若是真立足点（脚下再低一格有地板的坑），就是下一格
            // 的落点。记最近的一个，但继续扫：更远处可能有同层落点（整个坑
            // 一步跨过），两条边都给 A* ——落进坑里还是飞越坑，按总价定。
            if (reach >= 2 && !tookTheDip
                    && walkableCell(x, node.y - 1, z)) {
                count = emitLanding(out, count, x, node.y - 1, z);
                tookTheDip = true;
            }
        }
        return count;
    }

    /** 钻缝那一族要读同一个世界快照。 */
    BlockGetter world() {
        return this.level;
    }

    /** 把一个跳跃落点定型成可走节点塞进数组；塞不进或已关闭就原样返回。 */
    int emitLanding(Node[] out, int count, int x, int y, int z) {
        Node landing = this.getNode(x, y, z);
        if (landing == null || landing.closed) {
            return count;
        }
        landing.type = BlockPathTypes.WALKABLE;
        landing.costMalus = Math.max(landing.costMalus, GAP_JUMP_MALUS);
        if (count < out.length) {
            out[count++] = landing;
        }
        return count;
    }

    /**
     * 这一格飞行弧线能不能穿过去。
     *
     * <p>不能只认 OPEN 枚举：开着的活板门是贴边竖片（宿主按"高于半格"判成
     * BLOCKED），可格心整条是空的，身子从旁边过毫无阻碍——按枚举认，跑酷线
     * 上立一片开门板整条线就断（玩家实测，与关门板同罪不同因）。物理的尺：
     * 类型是空气，或者格心无碰撞且无流体（栅栏柱盖住格心，仍是真墙）。
     */
    boolean airy(BlockGetter level, int x, int y, int z) {
        if (this.getBlockPathType(level, x, y, z) == BlockPathTypes.OPEN) {
            return true;
        }
        BlockPos pos = new BlockPos(x, y, z);
        return !FootingRule.coversCenter(level, pos)
                && level.getFluidState(pos).isEmpty();
    }

    /**
     * 沉在格子里的地板（关着的下半活板门这类），地板高度就在格子自身。
     *
     * <p>原版只看下一格：门板悬空侧放时，下一格是空气，起点地板被算到一格
     * 以下，迈向同层邻块的台阶差被算成两格高（超过 1.125 的接受上限）——
     * **起点连不出任何邻居**，站上门板的人就此冻住，哪条路都铺不出来。
     * 玩家实测：同层侧放的关闭下半门，她站上去就动不了。桥面测试没抓到它，
     * 因为那条路在她还站在石面上时就建好了，过门板段沿用旧路径；起点落在
     * 门板上时每次建路都撞死在这一步。
     */
    @Override
    protected double getFloorLevel(BlockPos pos) {
        VoxelShape self = this.level.getBlockState(pos)
                .getCollisionShape(this.level, pos);
        if (FootingRule.selfFloor(self)) {
            return pos.getY() + self.max(Direction.Axis.Y);
        }
        return super.getFloorLevel(pos);
    }
    /**
     * 宿主的分类要改判，改判的规矩在 {@link CellClassifier}。
     *
     * <p>这里只做转接：分类是"跟宿主词汇打交道"，连边是几何，两件事分开
     * 长各自的注释与钉子。
     */
    @Override
    public BlockPathTypes getBlockPathType(
            BlockGetter level,
            int x,
            int y,
            int z
    ) {
        return CellClassifier.reclassify(level, new BlockPos(x, y, z),
                super.getBlockPathType(level, x, y, z));
    }
}
