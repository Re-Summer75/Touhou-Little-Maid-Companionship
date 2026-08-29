package com.laixia.maidintelligence.gametest.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.gametest.support.PathwalkTrace;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 门板压线：跑酷线的起跳格或落点格上有活板门，开着关着都不许切断这条线。
 *
 * <p>玩家高空平台实测（附图）：缺口两侧只要有一片活板门——关着的躺在起跳
 * 格里、开着的立在落点格边——她就拒跳，而台阶楼梯没事。病根在跳跃扫描按
 * **类型枚举**认路：台阶楼梯恰好是 WALKABLE，门板是 TRAPDOOR、开门板被宿
 * 主按"高于半格"判成 BLOCKED，"不是 WALKABLE 也不是 OPEN 一律当墙"就把
 * 方向判死了。修法是换成与立足验收同一把物理尺：盖住格心的矮板是路面、
 * 贴边竖片可穿行、盖住格心的高碰撞才是真墙。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class LidOnTheLineGameTests {
    private static final int DECK = 5;

    private LidOnTheLineGameTests() {
    }

    /** 关着的门板躺在起跳格里：踩着它照样起跳，跨过两格缺口。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void aClosedLidOnTheTakeoffDoesNotCutTheLine(
            GameTestHelper helper
    ) {
        buildPlatforms(helper);
        helper.setBlock(new BlockPos(3, DECK + 1, 1),
                Blocks.OAK_TRAPDOOR.defaultBlockState());
        runTheLine(helper, "closed lid on takeoff");
    }

    /** 开着的门板立在落点格边：从旁边落进去，线不许断。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void anOpenLidAtTheLandingDoesNotCutTheLine(
            GameTestHelper helper
    ) {
        buildPlatforms(helper);
        BlockState openLid = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.EAST)
                .setValue(TrapDoorBlock.OPEN, true);
        helper.setBlock(new BlockPos(6, DECK + 1, 1), openLid);
        runTheLine(helper, "open lid at landing");
    }

    /** 栅栏占了直线落点：旁边就有站位，斜一点跳过去——不许整条方向判死。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void aFencePostOnTheLandingSidestepsTheLine(
            GameTestHelper helper
    ) {
        buildPlatforms(helper);
        helper.setBlock(new BlockPos(6, DECK + 1, 1), Blocks.OAK_FENCE);
        runTheLine(helper, "fence post on landing");
    }

    /** 开着的门板立在平路中间：格心站得下人，走穿它，不许绕天下。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void throughAnOpenLidSheWalks(GameTestHelper helper) {
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.DIRT);
            }
        }
        BlockState openLid = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.NORTH)
                .setValue(TrapDoorBlock.OPEN, true);
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(new BlockPos(5, DECK + 1, z), openLid);
        }
        runTheLine(helper, "open lid mid-path");
    }

    /**
     * 一格宽窄道正中一根栅栏柱：柱只占中间四分之一，贴边那条缝塞得下身位
     * ——像玩家一样贴边绕过去，身子悬在道外也算数（体素化，玩家点名）。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void pastAFencePostOnTheNarrowWalkSheSqueezes(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, DECK, 1), Blocks.DIRT);
        }
        helper.setBlock(new BlockPos(5, DECK + 1, 1), Blocks.OAK_FENCE);
        runTheLine(helper, "fence post on narrow walk");
    }

    /**
     * 栅栏柱在崖沿格（玩家点名"边缘放一个栅栏试试"）：起跳格自己被柱占了，
     * 挤边和跳跃必须能组合——挤到柱边的窄条上，再从窄条上起跳过缺口。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void aFencePostOnTheBrinkStillLeapsTheGap(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, DECK, 1), Blocks.DIRT);
        }
        for (int x = 6; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, DECK, 1), Blocks.DIRT);
        }
        helper.setBlock(new BlockPos(3, DECK + 1, 1), Blocks.OAK_FENCE);
        runTheLine(helper, "fence post on the brink");
    }

    /**
     * 栅栏柱压在对岸崖沿（玩家实机场景）：跳跃弧线要穿过柱格，柱旁的侧缝
     * 三维里是空的——身位箱扫掠认下这条**空中车道**，先对齐缝再起跳，滞空
     * 锁沿车道的落点，落到柱后再走回中线。格判死会把这跳整条掐掉。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void throughTheLaneBesideThePostSheLeaps(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, DECK, 1), Blocks.DIRT);
        }
        for (int x = 5; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, DECK, 1), Blocks.DIRT);
        }
        helper.setBlock(new BlockPos(5, DECK + 1, 1), Blocks.OAK_FENCE);
        runTheLine(helper, "lane beside the post");
    }

    /** 两块悬空平台，缺口两格，跳跃射程之内。 */
    private static void buildPlatforms(GameTestHelper helper) {
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.DIRT);
            }
        }
        for (int x = 6; x <= 9; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.DIRT);
            }
        }
    }

    /** 从西台驱动到东台深处，跨线算到；全程读数带，失败附日记。 */
    private static void runTheLine(GameTestHelper helper, String name) {
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel());
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        maid.setPos(zero.getX() + 1.5D, zero.getY() + DECK + 1.0D,
                zero.getZ() + 1.5D);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        BlockPos goal = new BlockPos(8, DECK + 1, 1);
        double gx = helper.absolutePos(goal).getX() + 0.5D;
        double gz = helper.absolutePos(goal).getZ() + 0.5D;
        boolean[] arrived = new boolean[]{false};
        double[] lowest = new double[]{maid.getY()};
        PathwalkTrace trace = new PathwalkTrace(name, zero);

        for (int tick = 1; tick <= 280; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                if (arrived[0]) {
                    return;
                }
                trace.sample(at, maid);
                lowest[0] = Math.min(lowest[0], maid.getY());
                if (Math.hypot(maid.getX() - gx, maid.getZ() - gz) < 1.2D) {
                    arrived[0] = true;
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(goal)), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(300, () -> {
            trace.dump();
            helper.assertTrue(
                    lowest[0] > zero.getY() + DECK - 0.5D,
                    "The lid line dropped her: lowest y "
                            + (lowest[0] - zero.getY()) + "; " + diaryOf(maid)
            );
            helper.assertTrue(
                    arrived[0],
                    "The lid cut the parkour line (" + name + "); "
                            + diaryOf(maid)
            );
            maid.discard();
            helper.succeed();
        });
    }

    private static String diaryOf(EntityMaid maid) {
        return maid.getNavigation()
                instanceof com.laixia.maidintelligence.feature.behavior.tlm
                        .pathing.SureFootedNavigation sure
                ? sure.pathwalkDiary()
                : "diary=n/a";
    }
}
