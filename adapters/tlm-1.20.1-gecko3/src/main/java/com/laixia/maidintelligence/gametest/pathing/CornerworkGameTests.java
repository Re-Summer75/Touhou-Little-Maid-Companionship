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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 拐角功夫：窄台上的转向不许被动量出卖。
 *
 * <p>玩家报的实机场景：竖向 L 形、两边各空一格的窄道，她上了拐角方块之后
 * 顺着旧朝向直接跳出侧沿。根子是台阶提前跳原样保留水平动量——上到**要拐弯
 * 的**一格宽孤台上，那份直线动量就是出卖她的那只手。修法在
 * {@code SureFootedNavigation.maybeJumpAStep}：拐角不提前跳（交回原版撞停，
 * 上去时动量近零）、斜着接近不提前跳（侧向分量会把她漂出窄台）。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class CornerworkGameTests {
    private static final int DECK = 5;
    private static final int WATCHED_TICKS = 200;

    private CornerworkGameTests() {
    }

    /**
     * 同层一格宽的 L：长直道攒足速度，直角拐弯，转身不许把她甩下去。
     *
     * <p>玩家报的实机现象：转身时直接朝转身的方向迈出崖外。机制是路径跟随
     * 提前瞄向拐弯后的节点，移动控制沿斜线切角，斜线横穿拐角外的深渊；
     * 崖边收步只减速不拦路，她照样一点点蠕出去。修法：朝崖的动量若不是
     * 验证过的起跳助跑，刹死并弃路，下一 tick 从站定的脚下重铺。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
    public static void aFlatOneWideCornerIsNotCutIntoTheVoid(
            GameTestHelper helper
    ) {
        for (int x = -1; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 一格宽 L：向东长直道 x0..5（z=1），拐角 (5,1)，向南臂 z2..6（x=5）。
        for (int x = 0; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, DECK, 1), Blocks.STONE);
        }
        for (int z = 2; z <= 6; z++) {
            helper.setBlock(new BlockPos(5, DECK, z), Blocks.STONE);
        }
        for (int y = 2; y <= 3; y++) {
            for (int x = -1; x <= 8; x++) {
                helper.setBlock(new BlockPos(x, y, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
            }
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(-1, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
            }
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 0, DECK + 1, 1));
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double startFeet = maid.getY();
        double[] lowest = new double[]{startFeet};
        StringBuilder tape = new StringBuilder();

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
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
                if (maid.getY() < startFeet - 1.5D) {
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(new BlockPos(5, DECK + 1, 6))
                        ), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > startFeet - 1.5D,
                    "The turn flung her off the flat corner: lowest y "
                            + lowest[0] + "; tape(rel)=" + tape
            );
            double pastTheTurn = helper.absolutePos(
                    new BlockPos(5, DECK + 1, 5)).getZ();
            helper.assertTrue(
                    maid.getZ() >= pastTheTurn,
                    "She never rounded the flat corner: z " + maid.getZ()
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 竖向 L：三宽引道向东，上一格到一格宽的拐角孤台，向南拐进一格宽的
     * 高臂，臂中间还断着一格。两侧全程悬空——侧沿出去就是四格深。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
    public static void aOneWideVerticalLTurnsWithoutASideLeap(
            GameTestHelper helper
    ) {
        for (int x = -1; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 引道：三宽，走面 DECK+1。
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        // 拐角孤台与向南的高臂：一格宽，走面 DECK+2，z=4 断一格。
        helper.setBlock(new BlockPos(3, DECK + 1, 1), Blocks.STONE);
        for (int z = 2; z <= 6; z++) {
            if (z == 4) {
                continue;
            }
            helper.setBlock(new BlockPos(3, DECK + 1, z), Blocks.STONE);
        }
        // 外圈矮墙拦住摔下去后的游荡，别搅红邻居。
        for (int y = 2; y <= 3; y++) {
            for (int x = -1; x <= 8; x++) {
                helper.setBlock(new BlockPos(x, y, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
            }
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(-1, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
            }
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 0, DECK + 1, 1));
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double startFeet = maid.getY();
        double[] lowest = new double[]{startFeet};
        StringBuilder tape = new StringBuilder();

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
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
                if (maid.getY() < startFeet - 1.5D) {
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(new BlockPos(3, DECK + 2, 6))
                        ), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > startFeet - 1.5D,
                    "Momentum flung her off the one-wide corner: lowest y "
                            + lowest[0] + "; tape(rel)=" + tape
            );
            double pastTheArmGap = helper.absolutePos(
                    new BlockPos(3, DECK + 2, 5)).getZ();
            helper.assertTrue(
                    maid.getZ() >= pastTheArmGap,
                    "She never finished the vertical L: z " + maid.getZ()
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 高落差竖向 L：从两格高的高臂跳下一格宽拐角，紧接九十度拐弯。
     *
     * <p>实测最容易摔的形态：下行滞空攒出来的横速带着她滑过拐角、掉进
     * 拐角外一格的空中。落地收腿的档位从前不多不少卡在崖边看护的介入线
     * 上，看护睁眼时人已经在沿外。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
    public static void downTheTallVerticalLSheHoldsTheCorner(
            GameTestHelper helper
    ) {
        tallVerticalL(helper, Blocks.STONE.defaultBlockState());
    }

    /** 同一形态，拐角是半砖——不完整的落点让滑出更容易，实测更严重。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
    public static void downOntoASlabCornerSheStillHolds(
            GameTestHelper helper
    ) {
        tallVerticalL(helper, Blocks.STONE_SLAB.defaultBlockState());
    }

    private static void tallVerticalL(
            GameTestHelper helper,
            net.minecraft.world.level.block.state.BlockState corner
    ) {
        for (int x = -1; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 高臂：一格宽向南，走面 DECK+3，臂端与拐角贴邻（落差二、跨一）。
        for (int z = 2; z <= 6; z++) {
            helper.setBlock(new BlockPos(3, DECK + 2, z), Blocks.STONE);
        }
        // 拐角与低臂：走面 DECK+1（拐角可换半砖），向西一格宽。两侧全悬空。
        helper.setBlock(new BlockPos(3, DECK, 1), corner);
        for (int x = 0; x <= 2; x++) {
            helper.setBlock(new BlockPos(x, DECK, 1), Blocks.STONE);
        }
        for (int y = 2; y <= 3; y++) {
            for (int x = -1; x <= 8; x++) {
                helper.setBlock(new BlockPos(x, y, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
            }
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(-1, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
            }
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 3, DECK + 3, 5));
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double cornerFeet = helper.absolutePos(
                new BlockPos(3, DECK + 1, 1)).getY();
        double[] lowest = new double[]{maid.getY()};
        StringBuilder tape = new StringBuilder();

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
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
                if (maid.getY() < cornerFeet - 1.2D) {
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(new BlockPos(0, DECK + 1, 1))
                        ), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > cornerFeet - 1.2D,
                    "The tall-L descent slid her off the corner: lowest y "
                            + lowest[0] + " vs corner " + cornerFeet
                            + "; tape(rel)=" + tape
            );
            double pastTheTurn = helper.absolutePos(
                    new BlockPos(1, DECK + 1, 1)).getX() + 1.0D;
            helper.assertTrue(
                    maid.getX() <= pastTheTurn,
                    "She never rounded the tall-L corner: x " + maid.getX()
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }
}
