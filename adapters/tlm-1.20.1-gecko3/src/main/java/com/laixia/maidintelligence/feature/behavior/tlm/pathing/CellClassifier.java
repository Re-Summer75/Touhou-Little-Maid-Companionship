package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 这一格算什么：把宿主的分类词汇改判成立足事实。
 *
 * <p>从 {@code SafeFootingNodeEvaluator} 按职责拆出（单文件五百行的布局
 * 纪律）。那边回答"从这一格能连到哪儿"，这里只回答"这一格本身是地板、
 * 是墙、是可穿行的空、还是一片贴边的竖片"——两件事都在长，但长的方向
 * 不同：连边是几何，分类是**跟宿主词汇打交道**。
 *
 * <p>宿主的三个口子都在这儿堵上，每一个都由实机报告定罪：可通行类被直接
 * 当立足点（她走进缺口）、顶半门板被审成空气（起点格不成立、人定在原地）、
 * 贴边竖片被一律判 BLOCKED（明明有站位却拒跳）。
 */
final class CellClassifier {
    private CellClassifier() {
    }

    /**
     * @param hosted 宿主给出的原始分类
     */
    static BlockPathTypes reclassify(
            BlockGetter level,
            BlockPos pos,
            BlockPathTypes hosted
    ) {
        BlockPathTypes type = hosted;
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
        // 第三个口子：**高台面格**（末地烛、避雷针这类）被宿主一律判
        // BLOCKED——碰撞高过半格就算墙。可柱顶、床面、箱盖都站得住人，
        // 只是要跳上去；跑酷图里柱顶就是要踩的中继（玩家实测点名："有不
        // 完整方块不代表不可以站或过"）。按碰撞形状整类还原成可走——她
        // 站在这种格里脚踩台面（地板语义见评估器的 getFloorLevel，给的是
        // 真实顶面），落不落得上由扫掠仿真终审把关，这里只负责不把路判死。
        //
        // （此处曾反着写过一版：柱格一律改判 BLOCKED。那是把执行侧自救乱
        // 舞的病投影到图上——她当年摔是图不认站位、sidestep 在柱旁 0.31
        // 的缝里把她蹭下去，不是柱顶站不住。碑留此。）
        if (type == BlockPathTypes.BLOCKED
                && FootingRule.perchTop(level.getBlockState(pos)
                        .getCollisionShape(level, pos))) {
            return BlockPathTypes.WALKABLE;
        }
        return type;
    }

    /**
     * 下方那格是不是真能站人——**给"这一格悬空、靠下面托底"的晋升用**。
     *
     * <p>盖住格心还不够，还得看下面那格的顶托在哪儿。细高柱按**顶面在哪
     * 层**分家：石锥顶 0.69 探不出柱格，站顶的人脚在柱格自己的高度里——
     * 节点属于柱格（它自己按高台面改判成可走），上格不晋升（拒的不是"站
     * 不住"，是"节点安错了格"）；**末地烛顶恰在一格整**，站顶的人脚踩
     * y=1.0、按方块归属已在上格里——上格就是该晋升的那格，烛顶就是它的
     * 地板（玩家实测的中继跳，节点归属错一格边就连不出来）。
     */
    private static boolean standableBelow(BlockGetter level, BlockPos pos) {
        VoxelShape below = level.getBlockState(pos.below())
                .getCollisionShape(level, pos.below());
        if (!FootingRule.coversCenter(below)) {
            return false;
        }
        // （"顶过半格的支撑上格不晋升"的归属律曾在这里试过一刀，横杆桥
        // 全族当场断连——旧图的连通隐式依赖上格双表达，动不得。归属唯一
        // 性由自有寻路引擎（voxel/）原生保证，旧图冻结现状。）
        return !FootingRule.slimPillar(below)
                || below.max(Direction.Axis.Y) >= 0.99D;
    }
}
