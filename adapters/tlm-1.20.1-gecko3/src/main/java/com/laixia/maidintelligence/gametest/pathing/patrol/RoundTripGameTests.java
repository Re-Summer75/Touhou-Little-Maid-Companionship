package com.laixia.maidintelligence.gametest.pathing.patrol;

import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 直桥上的往返跑：从左到右、掉头、再回来，起点随机。
 *
 * <p>玩家点破的盲区：此前所有钉子都是固定出生点、单程驱动，而实机的摔出在
 * **回程**（跳跃边从另一侧扫、起跳落在不同相位）和**掉头**（终点处带着冲劲
 * 一百八十度转身，桥的尽头没有护栏）。这里让她在同一条桥上来回跑满整个观察
 * 窗，起点在出生格里随机偏移——每一趟的 tick 相位都不一样，边际时序问题跑
 * 几趟就会自己现形。
 *
 * <p>围墙加到视线高度：批次里邻居测试有怪物，矮墙挡不住她的战斗感知，出生
 * 两 tick 就朝邻居的僵尸跃出自己的场景——摔的不是跑酷的罪。
 *
 * <p>驾驶与仪表在 {@link BridgePatrol}；带拐弯的行道在
 * {@code WindingPatrolGameTests}。这里只剩地形。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class RoundTripGameTests {
    private static final int DECK = BridgePatrol.DECK;

    private RoundTripGameTests() {
    }

    /** 节奏缺口桥上往返：一格、两格的缺口每趟都要双向各跳一遍。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 470)
    public static void backAndForthAcrossTheRhythmGaps(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 9; x++) {
            if (x == 2 || x == 6 || x == 7) {
                continue;
            }
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        BridgePatrol.sideRails(helper, 9, DECK + 6);
        BridgePatrol.roundTrip(helper, DECK + 1, DECK + 1);
    }

    /**
     * 空中台阶上往返：上一格、下一格的斜边每趟双向各走一遍。
     *
     * <p>**跑六遍。**起点是每次随机抽的（见 {@link BridgePatrol#roundTrip}），
     * 而这条路的落点只有一格宽——起跳点差半格，落点就从格心挪到沿上。一次
     * 过说明的是"这一抽能过"，不是"这条路能过"：实测抓到过一趟起点靠西的
     * 样本，她从孤石顶上掠过去掉进对侧缺口，而前一轮同一条测试是绿的。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "raisedstone", timeoutTicks = 470,
            attempts = 6, requiredSuccesses = 6)
    public static void backAndForthOverTheRaisedStone(GameTestHelper helper) {
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int z = 0; z <= 2; z++) {
            for (int x = 0; x <= 2; x++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
            helper.setBlock(new BlockPos(4, DECK, z), Blocks.STONE);
            helper.setBlock(new BlockPos(4, DECK + 1, z), Blocks.STONE);
            for (int x = 6; x <= 9; x++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        BridgePatrol.sideRails(helper, 9, DECK + 6);
        BridgePatrol.roundTrip(helper, DECK + 1, DECK + 1);
    }

    /**
     * 沉槽往返：两条悬空桥之间沉着四格长的一格宽低槽，必须走进去再登出来。
     *
     * <p>玩家截图场景的一比一：低格夹在高一格的悬空块之间，出口是要登的
     * 台阶，而台阶块下面是空的——崖边探针从脚下半格穿过它照进虚空，把
     * 一步就能上去的台阶误判成崖，刹停弃路每 tick 重演，她被自己的安全
     * 机构钉死在槽里（截图里那圈困惑粒子）。槽长四格是刻意的：三格以内
     * 在飞越射程里会被整槽跳过去（第一版真被她跳了十五趟），进不了槽就
     * 测不到出槽。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 470)
    public static void throughTheSunkenSlotBetweenBridges(
            GameTestHelper helper
    ) {
        for (int x = -1; x <= 10; x++) {
            for (int z = -1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int y = 2; y <= DECK + 6; y++) {
            for (int x = -1; x <= 10; x++) {
                helper.setBlock(new BlockPos(x, y, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
            for (int z = -1; z <= 3; z++) {
                helper.setBlock(new BlockPos(-1, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(10, y, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 2; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 1), Blocks.GLASS);
        }
        for (int x = 3; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, DECK, 1), Blocks.GLASS);
        }
        for (int x = 7; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 1), Blocks.GLASS);
        }
        BridgePatrol.roundTrip(helper, DECK + 2, DECK + 2);
    }
}
