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
     * 竖向 V：下到一格宽的谷底、直角拐弯、再上去。两边各空一格。
     *
     * <p>玩家点名的地形。摔点在下坡那一步：崖边刹车对"只降一格"一律放行，
     * 可带着冲劲迈下台阶会滞空两三 tick——刹车只在着地时运作，这几 tick
     * 足够把她整格漂过去，谷底一格宽、再往前是深渊。修法是下坡也看一步
     * 之外：落脚格是尽头就先收速再下，落在格心里；落地后朝崖的残余动量
     * 交给"先问再管"刹死，然后从容转弯上坡。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 280)
    public static void aOneWideVeeTurnsAtTheTroughWithoutFalling(
            GameTestHelper helper
    ) {
        for (int x = -1; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 下行臂：一格宽向东，走面 DECK+2；谷底 (3,1) 低一格；上行臂向南
        // 回到 DECK+2 的走面。全程一格宽，两侧悬空。
        for (int x = 0; x <= 2; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 1), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(3, DECK, 1), Blocks.STONE);
        for (int z = 2; z <= 5; z++) {
            helper.setBlock(new BlockPos(3, DECK + 1, z), Blocks.STONE);
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
        maid.setPos(GameTestPositions.center(helper, 0, DECK + 2, 1));
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
                // 谷底比起点低一格是正路；低两格半就是摔下去了。
                if (maid.getY() < startFeet - 2.5D) {
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(new BlockPos(3, DECK + 2, 5))
                        ), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > startFeet - 2.5D,
                    "The vee trough spat her into the void: lowest y "
                            + lowest[0] + "; tape(rel)=" + tape
            );
            double pastTheClimb = helper.absolutePos(
                    new BlockPos(3, DECK + 2, 4)).getZ();
            helper.assertTrue(
                    maid.getZ() >= pastTheClimb,
                    "She never climbed out of the vee: z " + maid.getZ()
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 下坡超走、回头拉回、转身接跳——整条链不许把她甩下去。
     *
     * <p>玩家逐帧推演出的连环摔：迈下台阶滞空那几 tick 是全速漂移，落点比
     * AI 预期远；于是回头拉回位置，转身没转完台阶跳又到了——旧的台阶跳原样
     * 保留动量向量，斜着的那份把她直接送出一格宽的侧沿。两处修法一起钉：
     * 下坡入口收到小跑（惯性不替她多走半格）、台阶跳只带速度大小方向对齐
     * 台阶线。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 280)
    public static void aDownhillOvershootTurnAndHopStaysOnTheLine(
            GameTestHelper helper
    ) {
        for (int x = -1; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 一格宽：向东的高道，下一格进两格长的低槽，槽尾直角向南，
        // 再上一格回到高道。全程两侧悬空。
        for (int x = 0; x <= 2; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 1), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(3, DECK, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(4, DECK, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(4, DECK, 2), Blocks.STONE);
        for (int z = 3; z <= 6; z++) {
            helper.setBlock(new BlockPos(4, DECK + 1, z), Blocks.STONE);
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
        maid.setPos(GameTestPositions.center(helper, 0, DECK + 2, 1));
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
                if (maid.getY() < startFeet - 2.5D) {
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(new BlockPos(4, DECK + 2, 6))
                        ), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > startFeet - 2.5D,
                    "The overshoot-turn-hop chain flung her off: lowest y "
                            + lowest[0] + "; tape(rel)=" + tape
            );
            double pastTheClimb = helper.absolutePos(
                    new BlockPos(4, DECK + 2, 5)).getZ();
            helper.assertTrue(
                    maid.getZ() >= pastTheClimb,
                    "She never completed the trough turn: z " + maid.getZ()
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 直穿浅谷的 V：一格宽直线，中间沉一格，穿过去或飞过去都算过。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 280)
    public static void aStraightVeeRunsThroughTheTrough(
            GameTestHelper helper
    ) {
        for (int x = -1; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 1; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 1), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(2, DECK, 1), Blocks.STONE);
        for (int x = 3; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 1), Blocks.STONE);
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
        maid.setPos(GameTestPositions.center(helper, 0, DECK + 2, 1));
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double startFeet = maid.getY();
        double[] lowest = new double[]{startFeet};
        double[] farthest = new double[]{maid.getX()};
        StringBuilder tape = new StringBuilder();

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
                if (maid.getY() < startFeet - 2.5D) {
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(new BlockPos(5, DECK + 2, 1))
                        ), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > startFeet - 2.5D,
                    "The straight vee dropped her: lowest y " + lowest[0]
                            + "; tape(rel)=" + tape
            );
            double farSide = helper.absolutePos(
                    new BlockPos(4, DECK + 2, 1)).getX();
            helper.assertTrue(
                    farthest[0] >= farSide,
                    "She never crossed the straight vee: x " + farthest[0]
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
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
            timeoutTicks = 280)
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
            timeoutTicks = 280)
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
}
