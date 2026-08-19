package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 什么算地板、什么算墙、什么算板——立足的物理尺，全包只此一把。
 *
 * <p>从 {@code SafeFootingNodeEvaluator} 按职责拆出（单文件五百行的布局
 * 纪律）：评估器回答"图怎么连"，这里只回答"这一格的碰撞对一个站在格心的
 * 人意味着什么"。规划（节点分类、跳跃扫描）与执行（崖边检测、唇沿判定、
 * 跳线重验）必须用同一把尺——两侧对"什么算地板"答案不一，她就会被推进
 * 缺口或者被自己的安全机构钉死。
 */
final class FootingRule {
    /** 自身碰撞顶面不高于这个值才算"站得住自己"。半格：台阶的高度。 */
    static final double STANDABLE_TOP = 0.5D;

    private FootingRule() {
    }

    /**
     * 这一格的碰撞接不接得住站在格子中心的人：碰撞非空，且**盖得住中心**。
     *
     * <p>盖住中心这一条是把竖板和地板分开的那把尺：石头、台阶、楼梯、关着
     * 的活板门都盖住中心；开着的活板门/门是贴着格边的一条竖片，她的重心落
     * 在格子中心时脚下什么都没有——刷怪塔骗的就是这一步。用包围盒判断，
     * 接受楼梯这类 L 形的近似。
     */
    static boolean coversCenter(BlockGetter level, BlockPos pos) {
        return coversCenter(level.getBlockState(pos)
                .getCollisionShape(level, pos));
    }

    /** 同一把尺的形状版：碰撞的包围盒在水平面上盖不盖得住格子中心。 */
    static boolean coversCenter(VoxelShape shape) {
        if (shape.isEmpty()) {
            return false;
        }
        return shape.min(Direction.Axis.X) <= 0.5D
                && shape.max(Direction.Axis.X) >= 0.5D
                && shape.min(Direction.Axis.Z) <= 0.5D
                && shape.max(Direction.Axis.Z) >= 0.5D;
    }

    /**
     * 这一格里盖得住格心的碰撞顶面有多高；没有则负无穷。
     *
     * <p>把"下面有没有东西"升级成"下面的东西有多高"的那把尺：顶面贴着走面
     * （半格内）是平路，不用跳也不该跳；顶面低出脚面半格以上是**低洼**——
     * 沉在缺口里的关门板、浅坑——弧线从上面过是合法跑酷。只用布尔的"有没
     * 有"，关着的门板沉在缺口里就把跳跃线整个掐死：不能飞（规划不连线）也
     * 不能走（孤板连不成路），她两头不是——玩家实测：关着不跳、开了反而能跳。
     */
    static double coveringTopAt(BlockGetter level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos)
                .getCollisionShape(level, pos);
        if (!coversCenter(shape)) {
            return Double.NEGATIVE_INFINITY;
        }
        return pos.getY() + shape.max(Direction.Axis.Y);
    }

    /**
     * 这一格自己就是贴脚的矮地板（关着的下半活板门、地毯这类）：碰撞非空、
     * 顶面不高于半格、盖得住格心。
     */
    static boolean selfFloor(VoxelShape shape) {
        return !shape.isEmpty()
                && shape.max(Direction.Axis.Y) <= STANDABLE_TOP
                && coversCenter(shape);
    }

    /**
     * 这一格是贴边竖片（开着的活板门/门这类）：有碰撞但盖不住格心——身子
     * 从旁边过毫无阻碍，格心也站得下人。栅栏柱、玻璃板、墙这些盖住格心的
     * 不算。
     */
    static boolean edgePlate(VoxelShape shape) {
        return !shape.isEmpty() && !coversCenter(shape);
    }

    /** 挤边身位的半宽：她的包围盒正好 0.6。 */
    private static final double SQUEEZE_HALF = 0.30D;

    /** 挤边候选点离格心的偏移：栅栏柱（边在 0.625）两侧留半分缝正好过。 */
    private static final double SQUEEZE_REACH = 0.43D;

    /**
     * 格心被占但边上挤得过去的格子，给出能塞下一个身位的**贴边点**；没有
     * 返回 null。玩家过栅栏柱就是这么走的：柱只占中间四分之一，贴边那条窄
     * 带加上外溢到邻格（甚至虚空上方）的身位正好过人——格级的图看不见这
     * 条缝，判定要下到真实碰撞形状的分辨率（体素化，玩家点名）。
     *
     * <p>候选点在两轴 ±0.425 的边带与四角；每个候选做 0.6 宽、两格高的身位
     * 箱对本格与八邻真实 VoxelShape 的相交测试，脚下那格还要有承托。
     */
    static Vec3 squeezePoint(BlockGetter level, BlockPos cell) {
        return squeezePoint(level, cell, new Vec3(
                cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D));
    }

    /**
     * 方向感知版：几条窄带都站得住时，选离 {@code toward} 最近的那条——
     * 助跑要的是柱子朝落点那一侧的窄带，瞄背面的等于把自己抵在柱上。
     */
    static Vec3 squeezePoint(BlockGetter level, BlockPos cell, Vec3 toward) {
        double[] offsets = {-SQUEEZE_REACH, 0.0D, SQUEEZE_REACH};
        Vec3 best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (double ox : offsets) {
            for (double oz : offsets) {
                if (ox == 0.0D && oz == 0.0D) {
                    continue;
                }
                double px = cell.getX() + 0.5D + ox;
                double pz = cell.getZ() + 0.5D + oz;
                if (!bodyFits(level, cell, px, pz)
                        || !standsOnSomething(level, px, cell.getY(), pz)) {
                    continue;
                }
                double distSq = (px - toward.x) * (px - toward.x)
                        + (pz - toward.z) * (pz - toward.z);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = new Vec3(px, cell.getY(), pz);
                }
            }
        }
        return best;
    }

    /**
     * 过被占格的**侧向车道**：行进轴两旁 ±0.43 的两条窄带，入口、柱旁、
     * 出口三个位置都塞得下身位才算一条真车道。正面窄带是陷阱——能站，
     * 但下一步就抵在柱面上（读数带实测：滑挤成九十 tick 的死舞）。返回
     * 车道的侧向坐标（世界系），没有车道返回 NaN；两条都通选离
     * {@code mobPerp}（她当下的侧向坐标）近的。
     */
    static double squeezeLane(
            BlockGetter level,
            BlockPos cell,
            boolean alongX,
            double mobPerp
    ) {
        double center = 0.5D + (alongX ? cell.getZ() : cell.getX());
        double best = Double.NaN;
        // 先试她当前所在的那一侧：等距时来回换边会让对齐左右摇摆。
        int preferred = mobPerp >= center ? 1 : -1;
        for (int pick = 0; pick <= 1; pick++) {
            int side = pick == 0 ? preferred : -preferred;
            double perp = center + side * SQUEEZE_REACH;
            boolean clear = true;
            for (double para = -SQUEEZE_REACH; para <= SQUEEZE_REACH + 0.01D;
                    para += SQUEEZE_REACH) {
                double px = alongX ? cell.getX() + 0.5D + para : perp;
                double pz = alongX ? perp : cell.getZ() + 0.5D + para;
                if (!bodyFits(level, cell, px, pz)
                        || !standsOnSomething(level, px, cell.getY(), pz)) {
                    clear = false;
                    break;
                }
            }
            if (clear && (Double.isNaN(best)
                    || Math.abs(perp - mobPerp) < Math.abs(best - mobPerp))) {
                best = perp;
            }
        }
        return best;
    }

    /** 身位箱（0.62 宽两格高）与本格及八邻的真实碰撞逐块相交测试。 */
    private static boolean bodyFits(
            BlockGetter level,
            BlockPos cell,
            double px,
            double pz
    ) {
        AABB body = new AABB(
                px - SQUEEZE_HALF, cell.getY() + 0.05D, pz - SQUEEZE_HALF,
                px + SQUEEZE_HALF, cell.getY() + 1.95D, pz + SQUEEZE_HALF
        );
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos at = cell.offset(dx, dy, dz);
                    VoxelShape shape = level.getBlockState(at)
                            .getCollisionShape(level, at);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    for (AABB box : shape.toAabbs()) {
                        if (box.move(at).intersects(body)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * 格心被**站不上去的高碰撞**占着（柱、墙）——矮板是踩上去的，不算。
     * 这种格走不到格心，只有贴边窄带可站可过。
     */
    static boolean tallAtCenter(BlockGetter level, BlockPos cell) {
        VoxelShape shape = level.getBlockState(cell)
                .getCollisionShape(level, cell);
        return coversCenter(shape)
                && shape.max(Direction.Axis.Y) > STANDABLE_TOP;
    }

    /**
     * 走向一个格子该瞄哪：默认瞄格心；只有格心被高物占着而贴边又塞得下
     * 身位时，才瞄贴边点。找不到贴边点也退回格心——瞄点只是优化，可走性
     * 另有判官。规划把被占格连成节点、执行走它、到位判定收账，三处同一个点。
     */
    static Vec3 aimPoint(BlockGetter level, BlockPos cell) {
        if (tallAtCenter(level, cell)) {
            Vec3 strip = squeezePoint(level, cell);
            if (strip != null) {
                return strip;
            }
        }
        return new Vec3(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D);
    }

    /** 贴边点脚下要有承托：点所在的柱子里，下一格顶面得贴着脚。 */
    private static boolean standsOnSomething(
            BlockGetter level,
            double px,
            int y,
            double pz
    ) {
        BlockPos under = BlockPos.containing(px, y - 0.5D, pz);
        VoxelShape shape = level.getBlockState(under)
                .getCollisionShape(level, under);
        return !shape.isEmpty()
                && under.getY() + shape.max(Direction.Axis.Y) >= y - 0.1D;
    }
}
