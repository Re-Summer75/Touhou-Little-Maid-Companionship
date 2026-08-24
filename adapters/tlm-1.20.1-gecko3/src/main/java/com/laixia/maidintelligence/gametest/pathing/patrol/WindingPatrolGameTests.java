package com.laixia.maidintelligence.gametest.pathing.patrol;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 拐弯的行道上往返：路不是一条直线，掉头也不在一条线上。
 *
 * <p>从 {@code RoundTripGameTests} 按职责拆出（单文件五百行的布局纪律）。
 * 直桥那三条的两端是两个 x 门槛，这两条不是——之字梯每一步都带九十度转
 * 弯，玻璃行的远端拐进另一条 z 行。判据也跟着换了主敌：深渊上摔是主敌，
 * 这两条上**原地打转**才是（浅地形摔不死人，之字梯摔与卡各占一半）。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class WindingPatrolGameTests {
    private static final int DECK = BridgePatrol.DECK;
    private static final int WATCHED = BridgePatrol.WATCHED_TICKS;

    private WindingPatrolGameTests() {
    }

    /**
     * 悬空之字梯：单块交替升高、每一步都带转弯，爬上去再爬回来。
     *
     * <p>玩家截图场景的一比一。之字梯每一步都是"贴脸登阶 + 拐弯"：登阶跳
     * 滞空的十几 tick 里，路标已经指向拐角方向，空中转向若还听路标的，她
     * 就在半空被拽出侧沿。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "windingpatrol", timeoutTicks = 470)
    public static void upAndDownTheZigzagStairsSheStays(
            GameTestHelper helper
    ) {
        for (int x = -1; x <= 6; x++) {
            for (int z = -1; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int y = 2; y <= DECK + 8; y++) {
            for (int x = -1; x <= 6; x++) {
                helper.setBlock(new BlockPos(x, y, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
            }
            for (int z = -1; z <= 6; z++) {
                helper.setBlock(new BlockPos(-1, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
            }
        }
        // 底台，之字梯（东、东、南），顶台。全部悬空玻璃。
        for (int x = 0; x <= 1; x++) {
            for (int z = 1; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.GLASS);
            }
        }
        helper.setBlock(new BlockPos(2, DECK + 1, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(3, DECK + 2, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(3, DECK + 3, 2), Blocks.GLASS);
        for (int x = 1; x <= 2; x++) {
            for (int z = 3; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, DECK + 4, z), Blocks.GLASS);
            }
        }
        patrol(helper, DECK + 1, 1, 0.7D,
                new BlockPos(2, DECK + 5, 4), new BlockPos(0, DECK + 1, 1),
                true, "the zigzag stairs");
    }

    /**
     * 浅地形上的玻璃行道：地面就在脚下一两格，行道带缺口、带拐角、带升高。
     *
     * <p>玩家实机截图的一比一（草地上铺的单排玻璃）。深渊场景里摔是主敌；
     * 浅地形上摔不死人，主敌变成**原地旋转卡死**——刹停、重铺、加速的循环
     * 或对角夹缝的爬跳弹回。断言趟数，失败附行车记录与读数带。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "windingpatrol", timeoutTicks = 470)
    public static void alongTheGlassRowsOverShallowGround(
            GameTestHelper helper
    ) {
        for (int x = -1; x <= 10; x++) {
            for (int z = -1; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 1), Blocks.GLASS);
        }
        // x=5 断一格：露出低一格的地面。
        helper.setBlock(new BlockPos(6, DECK + 1, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(7, DECK + 2, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(8, DECK + 2, 1), Blocks.GLASS);
        for (int z = 2; z <= 4; z++) {
            helper.setBlock(new BlockPos(8, DECK + 2, z), Blocks.GLASS);
        }
        patrol(helper, DECK + 2, 1, 0.5D,
                new BlockPos(8, DECK + 3, 4), new BlockPos(0, DECK + 2, 1),
                false, "the glass rows");
    }

    /**
     * 两个路标之间往返：到点掉头、计趟，全程记读数带与停滞表。
     *
     * <p>{@code watchFalls} 分开的是两种场景的主敌：悬空的路摔一次就是红，
     * 浅地形上摔不死人、只判卡住——把两者合成一条断言会让浅地形那条测起
     * 别的东西。
     */
    private static void patrol(
            GameTestHelper helper,
            int spawnFeetY,
            int spawnZ,
            double offZ,
            BlockPos far,
            BlockPos home,
            boolean watchFalls,
            String where
    ) {
        double offX = 0.4D
                + helper.getLevel().getRandom().nextDouble() * 1.2D;
        BlockPos base = helper.absolutePos(new BlockPos(0, spawnFeetY, spawnZ));
        EntityMaid maid = BridgePatrol.maidAt(helper, base.getX() + offX,
                base.getY(), base.getZ() + offZ);

        double baseFeet = base.getY();
        double[] lowest = new double[]{maid.getY()};
        boolean[] headingOut = new boolean[]{true};
        int[] legs = new int[]{0};
        StringBuilder tape = new StringBuilder();
        BridgePatrol.StallWatch stall = new BridgePatrol.StallWatch(maid);
        String[] fell = new String[]{null};

        for (int tick = 1; tick <= WATCHED; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                if (at % 5 == 0 && tape.length() < 900) {
                    BridgePatrol.tapeRow(helper, tape, at, maid);
                }
                if (watchFalls && maid.getY() < baseFeet - 1.5D) {
                    if (fell[0] == null) {
                        fell[0] = "fell at t=" + at + " "
                                + BridgePatrol.diaryOf(maid);
                    }
                    return;
                }
                stall.sample(at);
                BlockPos goal = headingOut[0] ? far : home;
                if (BridgePatrol.flatTo(helper, maid, goal) < 1.2D) {
                    headingOut[0] = !headingOut[0];
                    legs[0]++;
                }
                BridgePatrol.sendTo(helper, maid,
                        headingOut[0] ? far : home);
            });
        }

        helper.runAfterDelay(WATCHED, () -> {
            if (watchFalls) {
                helper.assertTrue(
                        lowest[0] > baseFeet - 1.5D,
                        "She was flung off " + where + " (legs " + legs[0]
                                + "): lowest y " + lowest[0] + "; " + fell[0]
                                + "; tape(rel)=" + tape
                );
            }
            helper.assertTrue(
                    legs[0] >= 2,
                    "She only finished " + legs[0] + " legs on " + where
                            + " — stuck; " + BridgePatrol.diaryOf(maid)
                            + "; tape(rel)=" + tape
            );
            helper.assertTrue(
                    stall.longest()
                            <= BridgePatrol.StallWatch.TOLERATED_TICKS,
                    "She spun in place on " + where + " for "
                            + stall.longest() + " ticks; "
                            + BridgePatrol.diaryOf(maid)
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }
}
