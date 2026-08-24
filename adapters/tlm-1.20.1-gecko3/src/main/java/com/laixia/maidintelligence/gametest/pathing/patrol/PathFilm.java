package com.laixia.maidintelligence.gametest.pathing.patrol;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.FootingRule;
import com.laixia.maidintelligence.gametest.support.PathwalkTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 俯视快照：把她脚下那一层画成字符图，插进读数带的同一条时间线。
 *
 * <p>专用服务端不渲染，测试里没有截图可拿。但诊断真正需要的也不是截图——
 * 是**判据眼里的世界**：哪一格是墙、哪一格站得住、她此刻站在哪。渲染出来
 * 的画面反而看不出"这格的碰撞盖没盖住格心"。
 *
 * <p>为什么值得画：坐标行回答"她去了哪儿"，可"那儿长什么样"要靠人脑把一串
 * 数字还原成地形，而我在这上面错过好几次——把场景建错（栅栏开在了另一面还
 * 浑然不觉）、把角上的缝算成过不去（从格心量的，斜穿不走格心）。一帧图摆在
 * 那儿，形状对不对一眼就知道。
 *
 * <p>只在**开场**印一帧（证明场景确实建成了想要的样子）、出事时倒出**最近
 * 几帧**。绿的时候不铺开：版面留给坐标。
 *
 * <p>图例：{@code #} 站得住的地面、{@code |} 真墙（格心被高碰撞盖住：柱、
 * 墙、关着的门板立面）、{@code :} 贴边薄片（开着的活板门这类，格心是空的、
 * 身子过得去）、{@code +} 矮地板（下半门板、台阶）、{@code ~} 空、{@code @}
 * 她、{@code o} 别人。
 *
 * <p>{@code |} 与 {@code :} 必须分开画。第一版把两者都画成 {@code |}，于是
 * "她栖在孤檐上、东边两格过不去"这张图看着像被墙堵死——而那两格其实是开着
 * 的活板门，格心通着，判据也认为通着。图与判据用同一把尺（{@code FootingRule}）
 * 才不会自己骗自己。
 */
final class PathFilm {
    /** 画多大一片：半径四格，容得下一个栅栏角连同两侧的柱子。 */
    private static final int RADIUS = 4;

    /** 留底的间隔：二十 tick 一帧，摔之前那两三秒看得见变化。 */
    private static final int EVERY = 20;

    /** 留几帧：够看清"她是怎么走到那一步的"，又不至于刷屏。 */
    private static final int FRAMES = 3;

    /** 矮到能一步踩上去的顶面：半格，与判据同一个数。 */
    private static final double FLOOR_TOP = 0.5D;

    /** 脚下这一层还够得着的地板高度差，与判据的立足线同一个数。 */
    private static final double FLOOR_REACH = 0.6D;

    private final GameTestHelper helper;
    private final EntityMaid maid;
    private final Entity[] cast;
    private final String[] recent = new String[FRAMES];
    private int filmed;

    PathFilm(GameTestHelper helper, EntityMaid maid, Entity... cast) {
        this.helper = helper;
        this.maid = maid;
        this.cast = cast;
    }

    /** 每 tick 调一次：开场那帧当场插表，之后按间隔留底。 */
    void roll(int tick, PathwalkTrace trace) {
        if (tick == 1) {
            trace.aside("--- 开场地形（她脚下这一层）---\n" + frame());
            return;
        }
        if (tick % EVERY == 0) {
            recent[filmed++ % FRAMES] = "--- t=" + tick + " ---\n" + frame();
        }
    }

    /** 出事了：把最近几帧按时间顺序倒进表里。 */
    void spill(PathwalkTrace trace) {
        for (int i = Math.max(0, filmed - FRAMES); i < filmed; i++) {
            trace.aside(recent[i % FRAMES]);
        }
    }

    /** 以她为中心的一帧。 */
    private String frame() {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        BlockPos feet = maid.blockPosition();
        StringBuilder out = new StringBuilder();
        out.append(String.format(
                "  她 rel=(%.2f,%.2f,%.2f)，画的是脚下这一层 y=%d%n",
                maid.getX() - zero.getX(),
                maid.getY() - zero.getY(),
                maid.getZ() - zero.getZ(),
                feet.getY() - zero.getY()));
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            out.append("  ");
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                out.append(mark(feet.offset(dx, 0, dz)));
            }
            out.append('\n');
        }
        return out.toString();
    }

    /**
     * 这一格画成什么。
     *
     * <p>问的是**碰撞形状**而不是方块类型——门板、台阶、栅栏在类型上五花八
     * 门，可脚感只由碰撞决定，寻路的尺（{@code FootingRule}）量的也是它。
     */
    private char mark(BlockPos at) {
        if (at.getX() == maid.blockPosition().getX()
                && at.getZ() == maid.blockPosition().getZ()) {
            return '@';
        }
        for (Entity other : cast) {
            if (other != null && other.blockPosition().getX() == at.getX()
                    && other.blockPosition().getZ() == at.getZ()) {
                return 'o';
            }
        }
        BlockGetter level = helper.getLevel();
        VoxelShape self = level.getBlockState(at).getCollisionShape(level, at);
        if (!self.isEmpty()) {
            if (!FootingRule.coversCenter(level, at)) {
                return ':';
            }
            return self.max(Direction.Axis.Y) <= FLOOR_TOP ? '+' : '|';
        }
        return FootingRule.coveringTopAt(level, at.below())
                >= at.getY() - FLOOR_REACH ? '#' : '~';
    }
}
