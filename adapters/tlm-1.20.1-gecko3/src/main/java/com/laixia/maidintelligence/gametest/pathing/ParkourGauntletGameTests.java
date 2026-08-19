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
 * 跑酷的严酷关卡：不是单个缺口，是连着来的地形。
 *
 * <p>玩家点的三种场景，各自钉死：**每隔几格一个缺口**（节奏跳，落地即起）、
 * **中间比路高一格、两侧各隔一格**（跳跃图的三维边：上一格的落点跨一格连，
 * 下一格的落点跨一到三格连，都交给 A* 比价）、**L 形**（两段跳之间要拐弯，
 * 落点锁定不许把上一跳的方向带进下一跳）。
 *
 * <p>断言口径与 {@code ParkourGameTests} 一致：过没过去、摔没摔。摔是唯一的
 * 死罪。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class ParkourGauntletGameTests {
    private static final int DECK = 5;
    private static final int WATCHED_TICKS = 200;

    private ParkourGauntletGameTests() {
    }

    /** 每隔几格一个缺口：一格的、两格的，落地就得起下一跳，节奏不许断。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
    public static void gapsEveryFewBlocksAreARhythmSheCanRun(
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
        rails(helper, 9, DECK + 3);
        EntityMaid maid = walker(helper, 0, DECK + 1);
        double deckFeet = maid.getY();
        double[] lowest = new double[]{deckFeet};
        double[] farthest = new double[]{maid.getX()};
        StringBuilder tape = new StringBuilder();

        drive(helper, maid, deckFeet, lowest, farthest,
                new BlockPos(9, DECK + 1, 1), tape);

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > deckFeet - 1.5D,
                    "She fell between the rhythm gaps: lowest y " + lowest[0]
                            + "; tape(rel)=" + tape
            );
            double farSide = helper.absolutePos(
                    new BlockPos(8, DECK + 1, 1)).getX();
            helper.assertTrue(
                    farthest[0] >= farSide,
                    "She never finished the rhythm run: x " + farthest[0]
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 中间比路高一格、两侧各隔一格：跳上去，再跳下来。
     *
     * <p>这是跳跃图的三维用例：上一格的落点（隔一格的高台）与下一格的落点
     * （从高台回到路面）都不在同层，评估器连的是斜边，执行器按落差换滞空
     * 节奏——上去配快步（弧线只有头七八 tick 够高），下来配缓步（多飘两三
     * tick，也防飞过落点）。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
    public static void aRaisedStoneMidwayIsAStepInTheAir(
            GameTestHelper helper
    ) {
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
        rails(helper, 9, DECK + 4);
        EntityMaid maid = walker(helper, 0, DECK + 1);
        double deckFeet = maid.getY();
        double[] lowest = new double[]{deckFeet};
        double[] farthest = new double[]{maid.getX()};
        StringBuilder tape = new StringBuilder();

        drive(helper, maid, deckFeet, lowest, farthest,
                new BlockPos(9, DECK + 1, 1), tape);

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > deckFeet - 1.5D,
                    "She fell beside the raised stone: lowest y " + lowest[0]
                            + "; tape(rel)=" + tape
            );
            double farSide = helper.absolutePos(
                    new BlockPos(8, DECK + 1, 1)).getX();
            helper.assertTrue(
                    farthest[0] >= farSide,
                    "She never made it over the raised stone: x " + farthest[0]
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * L 形跑酷：一段向东带缺口，拐弯，一段向南带缺口。
     *
     * <p>钉两件事：拐角不许被"崖边 + 远节点"的异常态卡死（验不过就停下重铺，
     * 下一 tick 路就顺了）；第二跳的方向必须是新的——落点锁定落地即解锁，
     * 上一跳的方向不许渗进下一跳。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
    public static void anLShapedRunTurnsHerBetweenLeaps(
            GameTestHelper helper
    ) {
        for (int x = -1; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 东西臂：x 0..7、z 0..2，x=3 整排是缺口。
        for (int x = 0; x <= 7; x++) {
            if (x == 3) {
                continue;
            }
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        // 南北臂：x 5..7、z 3..7，z=5 整排是缺口。
        for (int z = 3; z <= 7; z++) {
            if (z == 5) {
                continue;
            }
            for (int x = 5; x <= 7; x++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        // 围墙贴着 L 的外沿与内弯，落点锁定之外再挡一层侧滑。
        for (int y = DECK + 1; y <= DECK + 2; y++) {
            for (int x = -1; x <= 8; x++) {
                helper.setBlock(new BlockPos(x, y, -1), Blocks.STONE);
            }
            for (int z = -1; z <= 8; z++) {
                helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
            }
            for (int z = -1; z <= 3; z++) {
                helper.setBlock(new BlockPos(-1, y, z), Blocks.STONE);
            }
            for (int x = -1; x <= 4; x++) {
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
            for (int z = 3; z <= 8; z++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
            for (int x = 4; x <= 8; x++) {
                helper.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
            }
        }
        EntityMaid maid = walker(helper, 0, DECK + 1);
        double deckFeet = maid.getY();
        double[] lowest = new double[]{deckFeet};
        double[] farthest = new double[]{maid.getX()};
        StringBuilder tape = new StringBuilder();

        drive(helper, maid, deckFeet, lowest, farthest,
                new BlockPos(6, DECK + 1, 7), tape);

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > deckFeet - 1.5D,
                    "She fell somewhere along the L: lowest y " + lowest[0]
                            + "; tape(rel)=" + tape
            );
            double pastTheTurnGap = helper.absolutePos(
                    new BlockPos(6, DECK + 1, 6)).getZ();
            helper.assertTrue(
                    maid.getZ() >= pastTheTurnGap,
                    "She never cleared the second leg: z " + maid.getZ()
                            + ", far side of the turn gap at " + pastTheTurnGap
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 高一格、隔两格：满推力的加速跳。
     *
     * <p>玩家点名的地形。升到一格高只有起跳后头七八 tick，跨两格上一格吃满
     * 推力（冲刺量级）正好够着——推力是绝对值直写的，"加速"在写速度那一下，
     * 不靠真助跑。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
    public static void aTwoGapHopUpIsWithinASprint(GameTestHelper helper) {
        twoGapHopScene(helper, 0);
    }

    /**
     * 出生就站在崖边，照样跳得上去。
     *
     * <p>玩家报的实机现象：一开始就站在边缘的她既不起跳、也不会回头蓄力。
     * 根子不是蓄力——推力直写不需要助跑——是这种跨度此前不在跳跃图里，
     * 执行器把它当异常反复停路，她就冻在崖边。零助跑起跳必须成立。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 280)
    public static void bornOnTheBrinkSheStillMakesTheHop(
            GameTestHelper helper
    ) {
        twoGapHopScene(helper, 2);
    }

    /** 路在 DECK、隔两格缺口、对面高一格：从 {@code spawnX} 出发跳上去。 */
    private static void twoGapHopScene(GameTestHelper helper, int spawnX) {
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int z = 0; z <= 2; z++) {
            for (int x = 0; x <= 2; x++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
            for (int x = 5; x <= 9; x++) {
                helper.setBlock(new BlockPos(x, DECK + 1, z), Blocks.STONE);
            }
        }
        rails(helper, 9, DECK + 4);
        EntityMaid maid = walker(helper, spawnX, DECK + 1);
        double startFeet = maid.getY();
        double[] lowest = new double[]{startFeet};
        double[] farthest = new double[]{maid.getX()};
        StringBuilder tape = new StringBuilder();

        drive(helper, maid, startFeet, lowest, farthest,
                new BlockPos(9, DECK + 2, 1), tape);

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > startFeet - 1.5D,
                    "She fell into the two-wide gap below the hop: lowest y "
                            + lowest[0] + "; tape(rel)=" + tape
            );
            double farSide = helper.absolutePos(
                    new BlockPos(8, DECK + 2, 1)).getX();
            helper.assertTrue(
                    farthest[0] >= farSide,
                    "She never made the two-gap hop up: x " + farthest[0]
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /** 三格宽直桥的护栏（z=-1/3 两道墙加西端封口），高到 {@code top}。 */
    private static void rails(GameTestHelper helper, int length, int top) {
        for (int x = -1; x <= length + 1; x++) {
            for (int y = DECK + 1; y <= top; y++) {
                helper.setBlock(new BlockPos(x, y, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }
        for (int z = 0; z <= 2; z++) {
            for (int y = DECK + 1; y <= top; y++) {
                helper.setBlock(new BlockPos(-1, y, z), Blocks.STONE);
            }
        }
    }

    /** 站在起点的自由模式女仆，拾物关闭。 */
    private static EntityMaid walker(GameTestHelper helper, int x, int feetY) {
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, x, feetY, 1));
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        return maid;
    }

    /** 逐 tick 驱动她去目标；摔了就停手，免得她在世界底层搅红邻居。 */
    private static void drive(
            GameTestHelper helper,
            EntityMaid maid,
            double deckFeet,
            double[] lowest,
            double[] farthest,
            BlockPos target
    ) {
        drive(helper, maid, deckFeet, lowest, farthest, target, null);
    }

    /** 同上，带读数带：每五 tick 记一笔相对坐标，红了当场可诊。 */
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
                if (tape != null && at % 5 == 0 && tape.length() < 700) {
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
