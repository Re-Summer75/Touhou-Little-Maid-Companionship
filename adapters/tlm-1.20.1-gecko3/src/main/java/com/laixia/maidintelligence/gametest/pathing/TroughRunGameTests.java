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
 * 谷地与下坡：沉一格的谷底、带拐弯的 V、下坡惯性接转身的连环。
 *
 * <p>从 {@code CornerworkGameTests} 按族拆出（单文件五百行的布局纪律）。
 * 三条场景的共同点是"往下走那一步"：下坡滞空是全速漂移，落点、转身、
 * 再起跳每一环都不许被惯性出卖。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class TroughRunGameTests {
    private static final int DECK = 5;
    private static final int WATCHED_TICKS = 200;

    private TroughRunGameTests() {
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
            batch = "pathing", timeoutTicks = 280)
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
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
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
            batch = "pathing", timeoutTicks = 280)
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
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
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
     * V 形谷 + 崖沿方块顶上的关门板：来回跑，两个方向都要从板上过。
     *
     * <p>玩家点名的场景。下行臂最后一块（紧贴谷口的崖沿）顶上盖一片关闭的
     * 下半活板门：去程带着下坡动量踩上小数高度的板面紧接着就要下谷，回程
     * 从谷里爬上来第一脚就是它。板面脚感（+0.19）曾让登阶时机、崖边判定、
     * 唇沿判定各自失守过一回——这里把三样钉在同一个折返点上。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 470)
    public static void backAndForthOverTheLiddedBrinkOfTheVee(
            GameTestHelper helper
    ) {
        for (int x = -1; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 2; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 1), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(3, DECK, 1), Blocks.STONE);
        for (int z = 2; z <= 5; z++) {
            helper.setBlock(new BlockPos(3, DECK + 1, z), Blocks.STONE);
        }
        // 玩家点的位置：下行臂崖沿那一块的顶上，关着的下半门板。
        helper.setBlock(new BlockPos(2, DECK + 2, 1),
                Blocks.OAK_TRAPDOOR.defaultBlockState());
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
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel());
        double offZ = 0.3D
                + helper.getLevel().getRandom().nextDouble() * 0.4D;
        BlockPos west = new BlockPos(0, DECK + 2, 1);
        BlockPos south = new BlockPos(3, DECK + 2, 5);
        maid.setPos(
                helper.absolutePos(west).getX() + 0.5D,
                helper.absolutePos(west).getY(),
                helper.absolutePos(west).getZ() + 0.3D + offZ
        );
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double startFeet = maid.getY();
        double[] lowest = new double[]{startFeet};
        boolean[] headedSouth = new boolean[]{true};
        int[] legs = new int[]{0};
        StringBuilder tape = new StringBuilder();

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
                if (maid.getY() < startFeet - 2.5D) {
                    return;
                }
                BlockPos goal = headedSouth[0] ? south : west;
                double gx = helper.absolutePos(goal).getX() + 0.5D;
                double gz = helper.absolutePos(goal).getZ() + 0.5D;
                if (Math.hypot(maid.getX() - gx, maid.getZ() - gz) < 1.2D) {
                    headedSouth[0] = !headedSouth[0];
                    legs[0]++;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(helper.absolutePos(
                                headedSouth[0] ? south : west)), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(430, () -> {
            helper.assertTrue(
                    lowest[0] > startFeet - 2.5D,
                    "The lidded brink flung her off (legs " + legs[0]
                            + "): lowest y " + lowest[0]
                            + "; tape(rel)=" + tape
            );
            helper.assertTrue(
                    legs[0] >= 2,
                    "She only finished " + legs[0]
                            + " legs over the lidded brink — stuck; "
                            + diaryOf(maid) + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /** 行车记录：分段执行器的供词，不是自建导航就报 n/a。 */
    private static String diaryOf(EntityMaid maid) {
        return maid.getNavigation()
                instanceof com.laixia.maidintelligence.feature.behavior.tlm
                        .pathing.SureFootedNavigation sure
                ? sure.pathwalkDiary()
                : "diary=n/a";
    }

    /**
     * 直穿浅谷的 V：一格宽直线，中间沉一格，穿过去或飞过去都算过。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
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
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
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

}
