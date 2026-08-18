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
    /** 自身碰撞顶面不高于这个值才算"站得住自己"。半格：台阶的高度。 */
    private static final double STANDABLE_TOP = 0.5D;

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
     * 能跳多远跳多远，而且不止同层：沿每个方向扫过去，产出至多三种落点——最近
     * 的同层（跨一到三格）、上一格（只跨一格）、下一格（跨一到三格）——全部
     * 交给 A* 按总距离比价。中途撞上墙就断（墙顶若站得住，那就是上一格的落点）；
     * 弧线头顶不空也断——跳跃不穿墙。
     */
    @Override
    public int getNeighbors(Node[] outputArray, Node node) {
        int count = super.getNeighbors(outputArray, node);
        // 起跳格自己的头顶两格要空：跳起来的那一下发生在自己的柱子里。
        if (!airy(this.level, node.x, node.y + 1, node.z)
                || !airy(this.level, node.x, node.y + 2, node.z)) {
            return count;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            count = jumpLandings(outputArray, count, node, direction);
        }
        return count;
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
            BlockPathTypes feet =
                    this.getBlockPathType(this.level, x, node.y, z);
            if (feet == BlockPathTypes.WALKABLE) {
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
            if (feet != BlockPathTypes.OPEN) {
                // 撞上墙体。墙顶若是真立足点、且隔着一到两格缺口——上一格的
                // 落点（跨两格要满推力的加速跳，执行侧按距离自然配到上限）。
                if (reach >= 2 && reach <= UP_HOP_MAX_REACH
                        && this.getBlockPathType(this.level, x, node.y + 1, z)
                                == BlockPathTypes.WALKABLE
                        && airy(this.level, x, node.y + 2, z)) {
                    count = emitLanding(out, count, x, node.y + 1, z);
                }
                return count;
            }
            // OPEN：弧线的一段，头顶两格必须空，否则整条方向作废。
            if (!airy(this.level, x, node.y + 1, z)
                    || !airy(this.level, x, node.y + 2, z)) {
                return count;
            }
            // 弧下一层按高度分两种：顶面贴着走面（半格内）是**平路**——走路
            // 的事，不连跳跃线（执行侧的崖边检测也认它是地板，两侧一致）；
            // 顶面低出走面半格以上是**低洼**（沉在缺口里的关门板、浅坑），
            // 弧线从上面过是合法跑酷，照连。只用"有没有"判会把低洼里的孤板
            // 当成路，跳跃线被掐死而孤板又连不成路，她两头不是。
            if (coveringTopAt(this.level, new BlockPos(x, node.y - 1, z))
                    >= node.y - 0.6D) {
                return count;
            }
            // 这格下面一层若是真立足点（脚下再低一格有地板的坑），就是下一格
            // 的落点。记最近的一个，但继续扫：更远处可能有同层落点（整个坑
            // 一步跨过），两条边都给 A* ——落进坑里还是飞越坑，按总价定。
            if (reach >= 2 && !tookTheDip
                    && this.getBlockPathType(this.level, x, node.y - 1, z)
                            == BlockPathTypes.WALKABLE) {
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

    /** 这一格是不是纯粹的空气类——飞行弧线能穿过去的那种。 */
    private boolean airy(BlockGetter level, int x, int y, int z) {
        return this.getBlockPathType(level, x, y, z) == BlockPathTypes.OPEN;
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
        if (!self.isEmpty()
                && self.max(Direction.Axis.Y) <= STANDABLE_TOP
                && coversCenter(self)) {
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
                    && self.max(Direction.Axis.Y) <= STANDABLE_TOP) {
                return type;
            }
            // 关着的顶半活板门：贴着格子天花板高度的一整块平板——是墙体，
            // 站的人站在上一格（那一格的可走性由晋升验收保留）。审成空气
            // 她的脚就踩在"空气"的顶盖上，起点格不成立，人定在原地——
            // 玩家实测报的就是这个。竖板（开着的门）底边在地上，不进这支。
            if (!self.isEmpty()
                    && self.min(Direction.Axis.Y) >= STANDABLE_TOP
                    && coversCenter(self)) {
                return BlockPathTypes.BLOCKED;
            }
            return standableBelow(level, pos) ? type : BlockPathTypes.OPEN;
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
        return coversCenter(level, pos.below());
    }

    /**
     * 这一格的碰撞接不接得住站在格子中心的人：碰撞非空，且**盖得住中心**。
     *
     * <p>盖住中心这一条是把竖板和地板分开的那把尺：石头、台阶、楼梯、关着的
     * 活板门都盖住中心；开着的活板门/门是贴着格边的一条竖片，她的重心落在格子
     * 中心时脚下什么都没有——刷怪塔骗的就是这一步。用包围盒判断，接受楼梯这类
     * L 形的近似。
     *
     * <p>包内共享：{@code SureFootedNavigation} 的崖边检测用同一把尺。分类说
     * 某格不是地板、执行侧却因为那片竖板"有碰撞"而不敢起跳，她就会被导航推着
     * 走进缺口——两侧必须对"什么算地板"给出同一个答案。
     */
    static boolean coversCenter(BlockGetter level, BlockPos pos) {
        return coversCenter(level.getBlockState(pos)
                .getCollisionShape(level, pos));
    }

    /**
     * 这一格里盖得住格心的碰撞顶面有多高；没有则负无穷。
     *
     * <p>把"下面有没有东西"升级成"下面的东西有多高"的那把尺：顶面贴着走面
     * （半格内）是平路，不用跳也不该跳；顶面低出脚面半格以上是**低洼**——
     * 沉在缺口里的关门板、浅坑——弧线从上面过是合法跑酷。此前只用布尔的
     * "有没有"，关着的门板沉在缺口里就把跳跃线整个掐死，她两头不是：不能飞
     * （规划不连线）也不能走（孤板连不成路）——玩家实测：关着不跳、开了反而
     * 能跳。
     */
    static double coveringTopAt(BlockGetter level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos)
                .getCollisionShape(level, pos);
        if (!coversCenter(shape)) {
            return Double.NEGATIVE_INFINITY;
        }
        return pos.getY() + shape.max(Direction.Axis.Y);
    }

    /** 同一把尺的形状版：碰撞的包围盒在水平面上盖不盖得住格子中心。 */
    private static boolean coversCenter(VoxelShape shape) {
        if (shape.isEmpty()) {
            return false;
        }
        return shape.min(Direction.Axis.X) <= 0.5D
                && shape.max(Direction.Axis.X) >= 0.5D
                && shape.min(Direction.Axis.Z) <= 0.5D
                && shape.max(Direction.Axis.Z) >= 0.5D;
    }
}
