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
        // 崖下这一列必须是空的，否则这条测的就不是"九格深崖"。
        //
        // 实测她掉到 y=6 就落地了，还沿着 y=6 走了两格——可这条夹具只砌了
        // y=1 的地面和 y=10 的玻璃檐，y=5 那层地板不是它砌的。同一个批次里
        // 五十一条测试的结构挨在一起，而这些场景写方块时都远远超出自己声明
        // 的模板范围（这条就写到 x=8），谁后写谁赢。若真是隔壁写进来的，红
        // 的是这条、错的却是别处，而供词里一个字都不会提到它。
        //
        // 建好之后量一次、判决前再量一次：一开始就有，是结构挨得太近；跑到
        // 一半才出现，是隔壁在运行中写进来的。两种都要修，但修法不同。
        String[] below = new String[]{"", ""};
        below[0] = cliffColumn(helper);
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
            below[1] = cliffColumn(helper);
            // 过了也印。这一列是**场景成不成立**的前提，而它曾经被别的东西
            // 写过一次（实测她掉到 y=6 就落地、还沿 y=6 走了两格，可这条夹
            // 具只砌 y=1 和 y=10）。只在红的时候印，等于把"前提成不成立"押
            // 在"这一轮恰好出事"上——那正是今晚反复吃亏的那种赌法。
            System.out.println("=== pen route: cliff column ===\n  建好时="
                    + below[0] + "\n  判决时=" + below[1]);
            helper.assertTrue(
                    lowest[0] > zero.getY() + 9.0D,
                    "She went over the sheer cliff: lowest y "
                            + (lowest[0] - zero.getY()) + "; " + diaryOf(maid)
                            + "; 崖下建好时=" + below[0]
                            + "; 判决时=" + below[1]
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

    /** 崖下那一列（x 0..8，y 2..9，z 1）此刻有什么，非空的都报出来。 */
    private static String cliffColumn(GameTestHelper helper) {
        StringBuilder found = new StringBuilder();
        for (int y = 2; y <= 9; y++) {
            for (int x = 0; x <= 8; x++) {
                BlockPos at = new BlockPos(x, y, 1);
                if (!helper.getBlockState(at).isAir()) {
                    found.append(' ').append(x).append(',').append(y)
                            .append('=')
                            .append(helper.getBlockState(at).getBlock()
                                    .getName().getString());
                }
            }
        }
        return found.length() == 0 ? "空" : found.toString();
    }
}
