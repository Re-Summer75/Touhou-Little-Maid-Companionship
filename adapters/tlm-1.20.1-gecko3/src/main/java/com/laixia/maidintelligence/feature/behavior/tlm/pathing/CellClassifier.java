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
        return type;
    }


    /** 下方那格是不是真能站人。 */
    private static boolean standableBelow(BlockGetter level, BlockPos pos) {
        return FootingRule.coversCenter(level, pos.below());
    }
}
