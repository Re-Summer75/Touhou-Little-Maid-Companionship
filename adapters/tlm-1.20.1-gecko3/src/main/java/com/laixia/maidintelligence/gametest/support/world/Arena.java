package com.laixia.maidintelligence.gametest.support.world;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * 建场第一步：把本场范围清空。
 *
 * <p>**GameTest 在批次之间不还原世界。** 一个场景只写它自己关心的那几层
 * （地面、栅栏、场沿），没写到的格子留着的是上一批在同一片棋盘位置上摆下
 * 的东西。绿不绿于是取决于"这个位置上先跑的是谁"——间歇、无法复现、每次
 * 都伪装成被测行为自己的毛病。
 *
 * <p>实测代价（围栏角缺角案）：她连红五轮卡在圈外一步没挪，供词是起点解
 * 不出锚（{@code note=- path=null underfoot=none}）。追过两条歧路——先疑
 * 起点解析、再疑场地越界写进邻场，收口后供词一字不差——最后是读数带的俯
 * 视图给的答案：她四周整片都是立障，可那一场的栅栏只在圈上，她站的矮地
 * 一根都不该有。
 *
 * <p>清场范围按**棋盘步长**给：两轴步长都是十三，场地本来就只有这么大，
 * 写出去既污染邻场、也清不干净自己。
 */
public final class Arena {

    /** 棋盘步长减一：本场可用的格子是 0..SPAN，两轴同。 */
    public static final int SPAN = 12;

    private Arena() {
    }

    /**
     * 把 {@code from}..{@code to} 这几层清成空气，再由调用方铺自己的地。
     *
     * <p>范围要盖过**她站得到的所有高度**：地面那一层（会被随后重铺）、
     * 头顶的净空、以及场沿墙的高度。少清一层，上一批的栅栏就正好留在她
     * 的头顶或身位里。
     */
    public static void clear(GameTestHelper helper, int from, int to) {
        for (int x = 0; x <= SPAN; x++) {
            for (int z = 0; z <= SPAN; z++) {
                for (int y = from; y <= to; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }
}
