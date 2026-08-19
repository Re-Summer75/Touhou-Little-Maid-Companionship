package com.laixia.maidintelligence.gametest.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.gametest.support.PathwalkTrace;
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
 * 下崖跟进：高台尽头与目标之间只隔一段落差时，跳下去是合法的路。
 *
 * <p>实机黑匣子抓到的"可达前沿站桩"：她站在高台尽头（活板门台、玻璃边缘），
 * 目标就在台下几格外，图里没有"跳下去"这条边，A* 的最优解是原地，外观是
 * "不知道要跳，即使是可以的"（玩家三连报）。下崖边补上后，这里钉三条界：
 * 干落六格以内要跟；落水不怕高也要跟；深崖无水必须拒——跟随不是跳崖。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class DropOffGameTests {
    private DropOffGameTests() {
    }

    /** 高台尽头干落五格：她要自己迈下去，然后走到地面目标。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void offTheDeckEndSheDropsToFollow(GameTestHelper helper) {
        for (int x = 3; x <= 8; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 6, 1), Blocks.GLASS);
        }
        EntityMaid maid = spawn(helper, 1.5D, 7.0D, 1.5D);
        BlockPos goal = new BlockPos(7, 2, 1);
        driveAndAssertArrival(helper, maid, goal,
                "She never dropped off the deck end",
                new PathwalkTrace("deck-end drop",
                        helper.absolutePos(BlockPos.ZERO)));
    }

    /** 高台尽头脚下是水潭：落水不怕高，照样跟。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void intoThePondSheDropsToFollow(GameTestHelper helper) {
        // 水一直铺到甲板正下方：第一个落点必须是水面。此前西侧留了块干沿
        // 石，唯一的下崖边落在干石上、无到站背书被正确拒掉，水面反而够不
        // 着——场景背叛了它要测的事。
        for (int x = 2; x <= 8; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.WATER);
            }
        }
        for (int x = 1; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, 2, -1), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 3), Blocks.STONE);
        }
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(new BlockPos(1, 2, z), Blocks.STONE);
            helper.setBlock(new BlockPos(9, 2, z), Blocks.STONE);
        }
        // 潭对岸一块能爬出去的石台：目标要真可达，单程票才发得出去。
        helper.setBlock(new BlockPos(7, 2, 1), Blocks.STONE);
        for (int x = 0; x <= 1; x++) {
            helper.setBlock(new BlockPos(x, 6, 1), Blocks.GLASS);
        }
        EntityMaid maid = spawn(helper, 0.5D, 7.0D, 1.5D);
        BlockPos beacon = new BlockPos(7, 3, 1);
        boolean[] swam = new boolean[]{false};
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        PathwalkTrace trace = new PathwalkTrace("pond drop", zero);
        for (int tick = 1; tick <= 260; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                trace.sample(at, maid);
                if (at == 40) {
                    System.out.println("[pond probe] pad: "
                            + PathwalkTrace.describe(maid.getNavigation()
                                    .createPath(helper.absolutePos(
                                            new BlockPos(7, 3, 1)), 0), zero));
                    System.out.println("[pond probe] water: "
                            + PathwalkTrace.describe(maid.getNavigation()
                                    .createPath(helper.absolutePos(
                                            new BlockPos(5, 2, 1)), 0), zero));
                    System.out.println("[pond probe] rim: "
                            + PathwalkTrace.describe(maid.getNavigation()
                                    .createPath(helper.absolutePos(
                                            new BlockPos(2, 3, 1)), 0), zero));
                }
                if (maid.getX() - zero.getX() > 2.5D
                        && maid.getY() - zero.getY() < 4.5D) {
                    swam[0] = true;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(beacon)), 0.7F, 0)
                );
            });
        }
        helper.runAfterDelay(280, () -> {
            trace.dump();
            helper.assertTrue(
                    swam[0],
                    "The pond below never received her; " + diaryOf(maid)
            );
            maid.discard();
            helper.succeed();
        });
    }

    /** 九格深崖没有水：必须拒跳——跟随不是跳崖。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void atTheSheerCliffSheHoldsTheLine(GameTestHelper helper) {
        for (int x = 3; x <= 8; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 10, 1), Blocks.GLASS);
        }
        EntityMaid maid = spawn(helper, 1.5D, 11.0D, 1.5D);
        BlockPos lure = new BlockPos(7, 2, 1);
        double[] lowest = new double[]{maid.getY()};
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        PathwalkTrace trace = new PathwalkTrace("sheer cliff", zero);
        for (int tick = 1; tick <= 260; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                trace.sample(at, maid);
                lowest[0] = Math.min(lowest[0], maid.getY());
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(lure)), 0.7F, 0)
                );
            });
        }
        helper.runAfterDelay(280, () -> {
            trace.dump();
            helper.assertTrue(
                    lowest[0] > zero.getY() + 9.0D,
                    "She went over the sheer cliff: lowest y "
                            + (lowest[0] - zero.getY()) + "; " + diaryOf(maid)
            );
            maid.discard();
            helper.succeed();
        });
    }

    private static EntityMaid spawn(
            GameTestHelper helper,
            double x,
            double y,
            double z
    ) {
        EntityMaid maid = new EntityMaid(helper.getLevel());
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        maid.setPos(zero.getX() + x, zero.getY() + y, zero.getZ() + z);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        return maid;
    }

    /** 每 tick 驱动到方块目标，横向贴近算到；全程读数带，失败附日记。 */
    private static void driveAndAssertArrival(
            GameTestHelper helper,
            EntityMaid maid,
            BlockPos goal,
            String grievance,
            PathwalkTrace trace
    ) {
        double gx = helper.absolutePos(goal).getX() + 0.5D;
        double gz = helper.absolutePos(goal).getZ() + 0.5D;
        boolean[] arrived = new boolean[]{false};
        for (int tick = 1; tick <= 260; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                if (arrived[0]) {
                    return;
                }
                trace.sample(at, maid);
                if (Math.hypot(maid.getX() - gx, maid.getZ() - gz) < 1.4D) {
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
        helper.runAfterDelay(280, () -> {
            trace.dump();
            helper.assertTrue(arrived[0], grievance + "; " + diaryOf(maid));
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
