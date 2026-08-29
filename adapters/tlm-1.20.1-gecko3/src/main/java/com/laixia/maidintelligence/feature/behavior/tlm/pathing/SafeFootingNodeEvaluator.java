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

    /** 腾空那一族（跳、斜跳、下崖）：粗筛在它那儿，终审在扫掠仿真。 */
    private final AirborneEdges airborne = new AirborneEdges(this, tight);

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
        // （归属律的"脚格优先"曾在这里试过一刀，横杆桥全族当场断连——
        // 旧图的邻居生成隐式依赖上格节点，动不得。旧图就此冻结为过渡运
        // 行时，归属唯一性由自有寻路引擎（voxel/）原生保证。）
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
            count = airborne.dropOffLanding(
                    outputArray, count, node, direction);
        }
        // 起跳格自己的头顶两格要空：跳起来的那一下发生在自己的柱子里。
        if (!airy(this.level, node.x, node.y + 1, node.z)
                || !airy(this.level, node.x, node.y + 2, node.z)) {
            return census(node, count);
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            count = airborne.jumpLandings(outputArray, count, node, direction);
            count = airborne.diagonalLandings(
                    outputArray, count, node, direction);
            count = tight.squeezeLanding(outputArray, count, node, direction);
        }
        return census(node, count);
    }

    /** 腾空边族的终审要读她的身位尺寸；宿主的 mob 字段是外包 protected。 */
    net.minecraft.world.entity.Mob body() {
        return this.mob;
    }

    /** 真实脚高（门板 0.1875、台阶 0.5 都按真的算），给包内边族用。 */
    double floorAt(BlockPos pos) {
        return getFloorLevel(pos);
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

    /** 钻缝那一族要读同一个世界快照。 */
    BlockGetter world() {
        return this.level;
    }

    /** 把一个跳跃落点定型成可走节点塞进数组；塞不进或已关闭就原样返回。 */
    int emitLanding(Node[] out, int count, int x, int y, int z) {
        return emitLanding(out, count, x, y, z, GAP_JUMP_MALUS);
    }

    /** 定价版：极限跨度这类"能跳但险"的边由调用方抬价。 */
    int emitLanding(Node[] out, int count, int x, int y, int z, float malus) {
        Node landing = this.getNode(x, y, z);
        if (landing == null || landing.closed) {
            return count;
        }
        landing.type = BlockPathTypes.WALKABLE;
        landing.costMalus = Math.max(landing.costMalus, malus);
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
        // 高台面（柱顶、床面、箱盖——perchTop）同理：站上去脚在真实顶面
        // （石锥 0.69、末地烛 1.0），不给真顶的话邻边落差全按格底算，图
        // 承诺的边与她真会走出的弧差出一整个台面高。
        if (FootingRule.selfFloor(self) || FootingRule.perchTop(self)) {
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
