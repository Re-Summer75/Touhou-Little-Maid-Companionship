package com.laixia.maidintelligence.gametest.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
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
 * 往返跑：从左到右、掉头、再回来，起点随机。
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
 * <p>断言两条：一趟都不许摔；至少完成若干趟——跑不满说明她在哪儿卡住了。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class RoundTripGameTests {
    private static final int DECK = 5;
    private static final int WATCHED_TICKS = 400;

    private RoundTripGameTests() {
    }

    /** 节奏缺口桥上往返：一格、两格的缺口每趟都要双向各跳一遍。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "roundtrip", timeoutTicks = 470)
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
        sideRails(helper, 9, DECK + 6);
        roundTrip(helper, DECK + 1, DECK + 1);
    }

    /** 空中台阶上往返：上一格、下一格的斜边每趟双向各走一遍。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "roundtrip", timeoutTicks = 470)
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
        sideRails(helper, 9, DECK + 6);
        roundTrip(helper, DECK + 1, DECK + 1);
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
            batch = "roundtrip", timeoutTicks = 470)
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
        roundTrip(helper, DECK + 2, DECK + 2);
    }

    /**
     * 悬空之字梯：单块交替升高、每一步都带转弯，爬上去再爬回来。
     *
     * <p>玩家截图场景的一比一。之字梯每一步都是"贴脸登阶 + 拐弯"：登阶跳
     * 滞空的十几 tick 里，路标已经指向拐角方向，空中转向若还听路标的，她
     * 就在半空被拽出侧沿。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "roundtrip", timeoutTicks = 470)
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
        EntityMaid maid = new EntityMaid(helper.getLevel());
        double offX = 0.4D
                + helper.getLevel().getRandom().nextDouble() * 1.2D;
        BlockPos base = helper.absolutePos(new BlockPos(0, DECK + 1, 1));
        maid.setPos(base.getX() + offX, base.getY(), base.getZ() + 0.7D);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double baseFeet = base.getY();
        double[] lowest = new double[]{maid.getY()};
        boolean[] headingUp = new boolean[]{true};
        int[] legs = new int[]{0};
        StringBuilder tape = new StringBuilder();
        BlockPos top = new BlockPos(2, DECK + 5, 4);
        BlockPos bottom = new BlockPos(0, DECK + 1, 1);

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                if (at % 5 == 0 && tape.length() < 900) {
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
                if (maid.getY() < baseFeet - 1.5D) {
                    return;
                }
                BlockPos goal = headingUp[0] ? top : bottom;
                double gx = helper.absolutePos(goal).getX() + 0.5D;
                double gz = helper.absolutePos(goal).getZ() + 0.5D;
                double d = Math.hypot(maid.getX() - gx, maid.getZ() - gz);
                if (d < 1.2D) {
                    headingUp[0] = !headingUp[0];
                    legs[0]++;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(
                                        headingUp[0] ? top : bottom)),
                                0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > baseFeet - 1.5D,
                    "She was flung off the zigzag stairs (legs "
                            + legs[0] + "): lowest y " + lowest[0]
                            + "; tape(rel)=" + tape
            );
            helper.assertTrue(
                    legs[0] >= 2,
                    "She only finished " + legs[0]
                            + " stair legs — stuck; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /** 两侧护栏加高到视线高度，桥的两端敞着——掉头掉出去就是掉头的罪。 */
    private static void sideRails(GameTestHelper helper, int length, int top) {
        for (int x = -1; x <= length + 1; x++) {
            for (int y = DECK + 1; y <= top; y++) {
                helper.setBlock(new BlockPos(x, y, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }
    }

    /** 随机起点、往返驱动、计趟数；摔一次就是红。 */
    private static void roundTrip(
            GameTestHelper helper,
            int spawnFeetY,
            int walkFeetY
    ) {
        EntityMaid maid = new EntityMaid(helper.getLevel());
        double offX = 0.4D
                + helper.getLevel().getRandom().nextDouble() * 1.6D;
        double offZ = 1.2D
                + helper.getLevel().getRandom().nextDouble() * 0.6D;
        BlockPos base = helper.absolutePos(new BlockPos(0, spawnFeetY, 0));
        maid.setPos(base.getX() + offX, base.getY(), base.getZ() + offZ);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double deckFeet = base.getY();
        double[] lowest = new double[]{maid.getY()};
        boolean[] headingRight = new boolean[]{true};
        int[] legs = new int[]{0};
        StringBuilder tape = new StringBuilder();

        double leftRel = 1.2D;
        double rightRel = 8.3D;

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                double relX = maid.getX()
                        - helper.absolutePos(BlockPos.ZERO).getX();
                if (at % 5 == 0 && tape.length() < 900) {
                    tape.append(String.format("%d:(%.1f,%.1f,%.1f) ",
                            at,
                            relX,
                            maid.getY()
                                    - helper.absolutePos(BlockPos.ZERO).getY(),
                            maid.getZ()
                                    - helper.absolutePos(BlockPos.ZERO).getZ()
                    ));
                }
                if (maid.getY() < deckFeet - 1.5D) {
                    return;
                }
                if (headingRight[0] && relX >= rightRel) {
                    headingRight[0] = false;
                    legs[0]++;
                } else if (!headingRight[0] && relX <= leftRel) {
                    headingRight[0] = true;
                    legs[0]++;
                }
                BlockPos target = headingRight[0]
                        ? new BlockPos(9, walkFeetY, 1)
                        : new BlockPos(0, walkFeetY, 1);
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(target)), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > deckFeet - 1.5D,
                    "She fell during the round trips (legs done "
                            + legs[0] + ", spawn offX=" + String.format(
                                    "%.2f", offX)
                            + "): lowest y " + lowest[0]
                            + "; tape(rel)=" + tape
            );
            helper.assertTrue(
                    legs[0] >= 3,
                    "She only finished " + legs[0]
                            + " legs — stuck somewhere; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }
}
