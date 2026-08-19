package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidNodeEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    /** 下崖边的定价：折合多走几格路，让 A* 有楼梯先走楼梯。 */
    private static final float DROP_OFF_MALUS = 3.0F;

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
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            count = dropOffLanding(outputArray, count, node, direction);
        }
        // 起跳格自己的头顶两格要空：跳起来的那一下发生在自己的柱子里。
        if (!airy(this.level, node.x, node.y + 1, node.z)
                || !airy(this.level, node.x, node.y + 2, node.z)) {
            return count;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            count = jumpLandings(outputArray, count, node, direction);
            count = diagonalLandings(outputArray, count, node, direction);
            count = squeezeLanding(outputArray, count, node, direction);
        }
        return count;
    }

    /**
     * 挤边跨越：正前一格格心被占（栅栏柱这类）但贴边塞得下身位、再往前一
     * 格又是正经路面——连一条穿过被占格的两格边。玩家绕柱贴边走的就是这
     * 条缝：柱只占中间四分之一，格级的图却把整格判死（体素化，玩家点名）。
     * 塞不塞得下由 {@code FootingRule.squeezePoint} 对真实碰撞形状逐候选
     * 点做身位箱测试，执行侧用同一个点走贴边折线。
     */
    private int squeezeLanding(
            Node[] out,
            int count,
            Node node,
            Direction direction
    ) {
        int mx = node.x + direction.getStepX();
        int mz = node.z + direction.getStepZ();
        BlockPos mid = new BlockPos(mx, node.y, mz);
        if (!FootingRule.coversCenter(this.level, mid)
                || walkableCell(mx, node.y, mz)
                || FootingRule.squeezePoint(this.level, mid) == null) {
            return count;
        }
        int fx = node.x + 2 * direction.getStepX();
        int fz = node.z + 2 * direction.getStepZ();
        if (walkableCell(fx, node.y, fz)
                && this.getFloorLevel(new BlockPos(fx, node.y, fz))
                        >= node.y - 0.6D
                && airy(this.level, fx, node.y + 1, fz)) {
            count = emitLanding(out, count, fx, node.y, fz);
        }
        // 被占格自己也是节点：贴边窄条站得住人，站上去还能接着起跳——
        // 柱子在崖沿格时，挤边和跳跃必须能组合（玩家实测：柱在边缘就又
        // 站桩了）。执行侧走它时瞄同一个贴边点。
        return emitLanding(out, count, mx, node.y, mz);
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
     * 下崖跟进的落点：外一格身位头位全空、脚下两格以上无立足（一格内是原版
     * 台阶下行的地盘），往下扫到第一块盖得住格心的地板。干落六格以内、或
     * 落进水里（水面下那格是水就算，深潭不限高度），都连成一条贵三格的边。
     */
    private int dropOffLanding(
            Node[] out,
            int count,
            Node node,
            Direction direction
    ) {
        int x = node.x + direction.getStepX();
        int z = node.z + direction.getStepZ();
        if (!airy(this.level, x, node.y, z)
                || !airy(this.level, x, node.y + 1, z)) {
            return count;
        }
        // 脚下一格还有立足的话是原版续走的台阶，不归这条边管。
        if (FootingRule.coveringTopAt(this.level, new BlockPos(x, node.y - 1, z))
                > Double.NEGATIVE_INFINITY) {
            return count;
        }
        for (int depth = 2; depth <= DROP_MAX; depth++) {
            int y = node.y - depth;
            if (y <= this.level.getMinBuildHeight()) {
                return count;
            }
            BlockPos floorPos = new BlockPos(x, y - 1, z);
            if (this.level.getFluidState(floorPos).isSource()) {
                return emitDropLanding(out, count, x, y, z);
            }
            double top = FootingRule.coveringTopAt(this.level, floorPos);
            if (top > Double.NEGATIVE_INFINITY) {
                // 地板要贴脚（半格内），沉得更深的等下一轮扫描去接。
                return top >= y - 0.6D
                        ? emitDropLanding(out, count, x, y, z)
                        : count;
            }
        }
        return count;
    }

    /** 下崖落点定型：可走、贵三格。落点自身的可走性照常验。 */
    private int emitDropLanding(Node[] out, int count, int x, int y, int z) {
        if (!walkableCell(x, y, z)
                && !this.level.getFluidState(new BlockPos(x, y - 1, z))
                        .isSource()) {
            return count;
        }
        Node landing = this.getNode(x, y, z);
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

    /**
     * 这格是不是"能站人的路面"——跳跃扫描的认路尺。
     *
     * <p>不能只认 WALKABLE 枚举：关着的下半活板门是 TRAPDOOR、地毯这类矮板
     * 各有各的类型，**按枚举认路等于"路面必须是普通方块"**——玩家实测：跑酷
     * 线上放一片活板门（开关皆然）整条线就断，而台阶楼梯（枚举恰好 WALKABLE）
     * 没事。物理的尺只有一把：类型可走，或这格自己就是贴脚的矮地板。
     */
    private boolean walkableCell(int x, int y, int z) {
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
                continue;
            }
            if (!airy(this.level, x, node.y, z)) {
                BlockPos hard = new BlockPos(x, node.y, z);
                // 空中车道：柱类高障（栅栏）旁的侧缝在三维里是空的，弧线
                // 可以从缝里穿——格判死会把"明明有空间"的跳整条掐掉（玩家
                // 实测点名）。缝够身位、头上两格也空，就当弧线的一段继续；
                // 缝里站得住还发一个贴边落点，孤柱格也能当落脚点。
                if (FootingRule.tallAtCenter(this.level, hard)
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
                if (reach >= 2 && reach <= UP_HOP_MAX_REACH
                        && walkableCell(x, node.y + 1, z)
                        && airy(this.level, x, node.y + 2, z)
                        && this.getFloorLevel(new BlockPos(x, node.y + 1, z))
                                <= node.y + 1.25D) {
                    count = emitLanding(out, count, x, node.y + 1, z);
                }
                return count;
            }
            // 可穿行：弧线的一段，头顶两格必须空，否则整条方向作废。
            if (!airy(this.level, x, node.y + 1, z)
                    || !airy(this.level, x, node.y + 2, z)) {
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

    /** 把一个跳跃落点定型成可走节点塞进数组；塞不进或已关闭就原样返回。 */
    private int emitLanding(Node[] out, int count, int x, int y, int z) {
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
    private boolean airy(BlockGetter level, int x, int y, int z) {
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

    @Override
    public BlockPathTypes getBlockPathType(
            BlockGetter level,
            int x,
            int y,
            int z
    ) {
        BlockPathTypes type = super.getBlockPathType(level, x, y, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (type == BlockPathTypes.TRAPDOOR
                || type == BlockPathTypes.DOOR_OPEN) {
            VoxelShape self = level.getBlockState(pos)
                    .getCollisionShape(level, pos);
            // 关着的下半活板门这类：自己就是地板。
            if (!self.isEmpty()
                    && self.max(Direction.Axis.Y) <= FootingRule.STANDABLE_TOP) {
                return type;
            }
            // 关着的顶半活板门：贴着格子天花板高度的一整块平板——是墙体，
            // 站的人站在上一格（那一格的可走性由晋升验收保留）。审成空气
            // 她的脚就踩在"空气"的顶盖上，起点格不成立，人定在原地——
            // 玩家实测报的就是这个。竖板（开着的门）底边在地上，不进这支。
            if (!self.isEmpty()
                    && self.min(Direction.Axis.Y) >= FootingRule.STANDABLE_TOP
                    && FootingRule.coversCenter(self)) {
                return BlockPathTypes.BLOCKED;
            }
            return standableBelow(level, pos) ? type : BlockPathTypes.OPEN;
        }
        // 宿主把"碰撞高于半格"的非门方块一律判 BLOCKED——对箱子这类盖住格
        // 心的成立，对**贴边竖片**（开着的活板门）不成立：竖片占的是格边，
        // 格心站得下人、身子从旁边过毫无阻碍。玩家实测：开门板立在落点格，
        // 有站位却拒跳。竖片按脚下有无地板还原成可通行或空气。
        if (type == BlockPathTypes.BLOCKED) {
            VoxelShape self = level.getBlockState(pos)
                    .getCollisionShape(level, pos);
            if (FootingRule.edgePlate(self)) {
                return standableBelow(level, pos)
                        ? BlockPathTypes.TRAPDOOR
                        : BlockPathTypes.OPEN;
            }
        }
        // 第二个口子，也是实测里真正让她走进缺口的那一个：空气格的"地板检查"
        // 只看下方格的**分类**——凡不是空气/水/岩浆就算地板，于是开着的活板门
        // （分类 TRAPDOOR，实体只是贴边竖着的一片）把它上方的空气晋升成了
        // WALKABLE，她在桥面高度径直走进缺口。轨迹读数：tick 10 时 x=3.5、
        // y 仍在桥面——走的就是这一格。晋升出来的立足点必须验收。
        if (type == BlockPathTypes.WALKABLE
                && level.getBlockState(pos)
                        .getCollisionShape(level, pos)
                        .isEmpty()
                && !standableBelow(level, pos)) {
            return BlockPathTypes.OPEN;
        }
        return type;
    }

    /** 下方那格是不是真能站人。 */
    private static boolean standableBelow(BlockGetter level, BlockPos pos) {
        return FootingRule.coversCenter(level, pos.below());
    }
}
