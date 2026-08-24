package com.laixia.maidintelligence.gametest.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 门板与玻璃的衔接：顶半活板门平台连高一格的玻璃桥，站上去必须走得动、
 * 迈得上、跳得过。
 *
 * <p>从 {@code ParkourGauntletGameTests} 按族拆出（单文件五百行的布局纪律）。
 * 三条场景全部来自实机截图的一比一复刻。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class GlassRunGameTests {
    private static final int DECK = 5;
    private static final int WATCHED_TICKS = 200;

    private GlassRunGameTests() {
    }

    /**
     * 站在顶半活板门上，贴着的高一格玻璃桥要上得去。
     *
     * <p>实机地形：倒扣活板门平台连着高一格的玻璃桥面，她站在门板顶上就是
     * 不迈上去。门板顶是整数高度的合法立足面，往上一格是普通的抬脚——这条
     * 钉住"从沉块顶面出发的上行衔接"。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
    public static void offTheTrapdoorOntoTheGlassSheGoes(
            GameTestHelper helper
    ) {
        trapdoorToGlassScene(helper, false);
    }

    /** 同上，但门板与玻璃之间还隔着一格缺口：上一格的跳跃衔接。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
    public static void offTheTrapdoorAcrossAGapOntoTheGlass(
            GameTestHelper helper
    ) {
        trapdoorToGlassScene(helper, true);
    }

    /**
     * 瘦版实机地形：两格小门板台、一格宽无护栏的玻璃桥、高一格、悬空。
     *
     * <p>与三宽带护栏的排练场不同，这里每一侧都是深渊，起点台只有两格——
     * 窄台上的起点格、无护栏下的崖边收步、一格宽桥面上的节点接受，全在
     * 这一条里过堂。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
    public static void aSkinnyGlassRunOffATinyTrapdoorPerch(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockState lid = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, false)
                .setValue(TrapDoorBlock.HALF, Half.TOP);
        // 两格门板台（z=2 一条线），贴着高一格的一格宽玻璃桥，全部悬空。
        helper.setBlock(new BlockPos(0, DECK, 2), lid);
        helper.setBlock(new BlockPos(1, DECK, 2), lid);
        for (int x = 2; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 2), Blocks.GLASS);
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 0, DECK + 1, 2));
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double startFeet = maid.getY();
        double[] lowest = new double[]{startFeet};
        double[] farthest = new double[]{maid.getX()};
        StringBuilder tape = new StringBuilder();

        drive(helper, maid, startFeet, lowest, farthest,
                new BlockPos(9, DECK + 2, 2), tape);

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > startFeet - 1.5D,
                    "She fell off the skinny run: lowest y " + lowest[0]
                            + "; tape(rel)=" + tape
            );
            double farSide = helper.absolutePos(
                    new BlockPos(8, DECK + 2, 2)).getX();
            helper.assertTrue(
                    farthest[0] >= farSide,
                    "She froze on the tiny trapdoor perch: got to x "
                            + farthest[0] + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 桥中段一格长的凸台：登上去下一步就是下沿——过冲余量与带速登阶在这
     * 里都是把她送出沿的劲（实机玻璃桥凸段侧滑的一比一）。来回各过一遍：
     * 去程带速上凸台，回程从高处跳下接平桥。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 470)
    public static void overTheOneBlockBumpSheKeepsHerFeet(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 一格宽玻璃桥，中段 x5 高一格的孤凸台，全悬空。
        for (int x = 0; x <= 9; x++) {
            if (x == 5) {
                continue;
            }
            helper.setBlock(new BlockPos(x, DECK + 1, 2), Blocks.GLASS);
        }
        helper.setBlock(new BlockPos(5, DECK + 2, 2), Blocks.GLASS);

        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 1, DECK + 2, 2));
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double bridgeFeet = maid.getY();
        double[] lowest = new double[]{bridgeFeet};
        boolean[] headedEast = new boolean[]{true};
        int[] legs = new int[]{0};
        StringBuilder tape = new StringBuilder();
        BlockPos east = new BlockPos(8, DECK + 2, 2);
        BlockPos west = new BlockPos(1, DECK + 2, 2);

        for (int tick = 1; tick <= 400; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                if (at % 10 == 0 && tape.length() < 600) {
                    tape.append(String.format("%d:(%.1f,%.1f,%.1f) ",
                            at,
                            maid.getX()
                                    - helper.absolutePos(BlockPos.ZERO).getX(),
                            maid.getY()
                                    - helper.absolutePos(BlockPos.ZERO).getY(),
                            maid.getZ()
                                    - helper.absolutePos(BlockPos.ZERO).getZ()
                    ));
                }
                if (maid.getY() < bridgeFeet - 1.5D) {
                    return;
                }
                BlockPos goal = headedEast[0] ? east : west;
                double gx = helper.absolutePos(goal).getX() + 0.5D;
                double gz = helper.absolutePos(goal).getZ() + 0.5D;
                if (Math.hypot(maid.getX() - gx, maid.getZ() - gz) < 1.2D) {
                    headedEast[0] = !headedEast[0];
                    legs[0]++;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(helper.absolutePos(
                                headedEast[0] ? east : west)), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(430, () -> {
            helper.assertTrue(
                    lowest[0] > bridgeFeet - 1.5D,
                    "The one-block bump slid her off (legs " + legs[0]
                            + "): lowest y " + lowest[0]
                            + "; tape(rel)=" + tape
            );
            helper.assertTrue(
                    legs[0] >= 2,
                    "She only finished " + legs[0]
                            + " legs over the bump — stuck; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 顶半活板门平台（x 0..2，板顶即 DECK+1 的脚面）接玻璃桥（走面 DECK+2）；
     * {@code gapped} 为真时中间隔一格缺口。
     */
    private static void trapdoorToGlassScene(
            GameTestHelper helper,
            boolean gapped
    ) {
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockState lid = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, false)
                .setValue(TrapDoorBlock.HALF, Half.TOP);
        int glassFrom = gapped ? 4 : 3;
        for (int z = 0; z <= 2; z++) {
            for (int x = 0; x <= 2; x++) {
                helper.setBlock(new BlockPos(x, DECK, z), lid);
            }
            for (int x = glassFrom; x <= 9; x++) {
                helper.setBlock(new BlockPos(x, DECK + 1, z), Blocks.GLASS);
            }
        }
        for (int x = -1; x <= 10; x++) {
            for (int y = DECK + 1; y <= DECK + 4; y++) {
                helper.setBlock(new BlockPos(x, y, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }
        for (int z = 0; z <= 2; z++) {
            for (int y = DECK + 1; y <= DECK + 4; y++) {
                helper.setBlock(new BlockPos(-1, y, z), Blocks.STONE);
            }
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 1, DECK + 1, 1));
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double startFeet = maid.getY();
        double[] lowest = new double[]{startFeet};
        double[] farthest = new double[]{maid.getX()};
        StringBuilder tape = new StringBuilder();

        drive(helper, maid, startFeet, lowest, farthest,
                new BlockPos(9, DECK + 2, 1), tape);

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > startFeet - 1.5D,
                    "She fell between trapdoor and glass: lowest y "
                            + lowest[0] + "; tape(rel)=" + tape
            );
            double farSide = helper.absolutePos(
                    new BlockPos(8, DECK + 2, 1)).getX();
            helper.assertTrue(
                    farthest[0] >= farSide,
                    "She never climbed from the trapdoor onto the glass: x "
                            + farthest[0] + " (gapped=" + gapped + ")"
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /** 逐 tick 驱动她去目标；摔了就停手，免得她在世界底层搅红邻居。 */
    private static void drive(
            GameTestHelper helper,
            EntityMaid maid,
            double deckFeet,
            double[] lowest,
            double[] farthest,
            BlockPos target,
            StringBuilder tape
    ) {
        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                farthest[0] = Math.max(farthest[0], maid.getX());
                if (at % 5 == 0 && tape.length() < 700) {
                    tape.append(String.format("%d:(%.1f,%.1f,%.1f) ",
                            at,
                            maid.getX()
                                    - helper.absolutePos(BlockPos.ZERO).getX(),
                            maid.getY()
                                    - helper.absolutePos(BlockPos.ZERO).getY(),
                            maid.getZ()
                                    - helper.absolutePos(BlockPos.ZERO).getZ()
                    ));
                }
                if (maid.getY() < deckFeet - 1.5D) {
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(target)), 0.7F, 0)
                );
            });
        }
    }
}
