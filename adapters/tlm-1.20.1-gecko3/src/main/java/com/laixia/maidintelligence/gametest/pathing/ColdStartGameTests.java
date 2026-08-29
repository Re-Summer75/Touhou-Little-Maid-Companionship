package com.laixia.maidintelligence.gametest.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 冷启动：从别扭的站位出发，第一条路必须铺得出来。
 *
 * <p>往返压测抓到的冻结现场（之字梯，原地转 294 tick）：她在底台**角落**
 * (0.6, ·, 2.3) 站得端端正正、脚下真地板，目标就是刚爬过一遍的顶台，A* 却
 * 连续几百 tick 只铺出"到脚下为止"的残路。同一张图，从出生点能走、从这个
 * 角落不能走——冷启动的起点解析有它自己的失败面。这里把冻结坐标一比一钉
 * 死，失败时附上手动建路的原始节点串，让图的证词直接进失败信息。
 *
 * <p>另一族是玩家实机连报的三张截图：站上**桥面上的不完整方块**（玻璃顶上
 * 的关闭活板门、桶、堆肥桶）或**一开始就站在边缘**，她就定在原地。共同点
 * 都是起点格自身带非整碰撞或身位悬出——起点解析、地板报高、执行段进入判据
 * 里任何一环对这类脚感失守，外观都是同一种站桩。每种站位一比一钉死。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class ColdStartGameTests {
    private static final int DECK = 5;

    private ColdStartGameTests() {
    }

    /** 玻璃顶上的关闭活板门：站上薄板（脚感 +0.19），要能走下来过桥。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void fromAPlateOnTheDeckSheWalksOn(GameTestHelper helper) {
        buildBridge(helper);
        helper.setBlock(new BlockPos(1, DECK + 1, 1),
                Blocks.OAK_TRAPDOOR.defaultBlockState());
        crossFrom(helper, 1.5D, DECK + 1.3D, 1.5D);
    }

    /** 玻璃顶上的木桶：站上整块高台（脚感 +2），要能下来过桥。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void fromAtopTheBarrelSheClimbsDown(GameTestHelper helper) {
        buildBridge(helper);
        helper.setBlock(new BlockPos(1, DECK + 1, 1),
                Blocks.BARREL.defaultBlockState());
        crossFrom(helper, 1.5D, DECK + 2.0D, 1.5D);
    }

    /** 玻璃顶上的堆肥桶：陷进碗里（四壁高过台阶极限），要能跳出来过桥。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void outOfTheComposterBowlSheEscapes(GameTestHelper helper) {
        buildBridge(helper);
        helper.setBlock(new BlockPos(1, DECK + 1, 1),
                Blocks.COMPOSTER.defaultBlockState());
        crossFrom(helper, 1.5D, DECK + 1.2D, 1.5D);
    }

    /** 一开始就站在玻璃边缘、身前两格缺口：要能原地起跳或先回中再走。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void fromTheBrinkSheStillSetsOut(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            if (x == 1 || x == 2) {
                continue;
            }
            helper.setBlock(new BlockPos(x, DECK, 1), Blocks.GLASS);
        }
        crossFrom(helper, 0.88D, DECK + 1.0D, 1.5D);
    }

    /**
     * 实机同款驱动的门板起步：目标是**实体**（跟随主人的写法），且只在走目标
     * 缺席时补写一次——不是测试探针那种每 tick 死写。四个方块目标复现全绿
     * 之后，这是与实机剩下的最后一处驱动差异。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void chasingABodyOffThePlateSheGoes(GameTestHelper helper) {
        buildBridge(helper);
        helper.setBlock(new BlockPos(1, DECK + 1, 1),
                Blocks.OAK_TRAPDOOR.defaultBlockState());
        chaseFrom(helper, 1.5D, DECK + 1.3D, 1.5D);
    }

    /** 实机同款驱动的边缘起步：实体目标 + 缺席才写。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void chasingABodyOffTheBrinkSheGoes(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            if (x == 1 || x == 2) {
                continue;
            }
            helper.setBlock(new BlockPos(x, DECK, 1), Blocks.GLASS);
        }
        chaseFrom(helper, 0.88D, DECK + 1.0D, 1.5D);
    }

    /**
     * 实机冻结的一比一（黑匣子供词版）：高桥上站在活板门面，走目标**悬在桥旁
     * 空中、脚下十几格全空**——原版建路会把它垂直改派到深渊底，从此建路全空、
     * 站桩到天荒地老。修后它是灯塔：她该沿桥走到离它最近的那格。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void towardABeaconOverTheVoidSheStillWalks(
            GameTestHelper helper
    ) {
        int deck = 16;
        for (int x = 0; x <= 8; x++) {
            helper.setBlock(new BlockPos(x, deck, 1), Blocks.GLASS);
        }
        helper.setBlock(new BlockPos(1, deck + 1, 1),
                Blocks.OAK_TRAPDOOR.defaultBlockState());

        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel());
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        maid.setPos(zero.getX() + 1.5D, zero.getY() + deck + 1.3D,
                zero.getZ() + 1.5D);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        BlockPos beacon = helper.absolutePos(new BlockPos(4, deck + 1, 5));
        double nearX = helper.absolutePos(new BlockPos(4, deck + 1, 1))
                .getX() + 0.5D;
        boolean[] arrived = new boolean[]{false};
        String[] probe = new String[]{"unprobed"};
        StringBuilder tape = new StringBuilder();

        for (int tick = 1; tick <= 280; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                if (arrived[0]) {
                    return;
                }
                if (Math.abs(maid.getX() - nearX) < 1.1D
                        && maid.getY() > zero.getY() + deck) {
                    arrived[0] = true;
                    return;
                }
                if (at % 20 == 0 && tape.length() < 400) {
                    tape.append(String.format("%d:(%.1f,%.1f,%.1f) ",
                            at,
                            maid.getX() - zero.getX(),
                            maid.getY() - zero.getY(),
                            maid.getZ() - zero.getZ()
                    ));
                }
                if (at == 40) {
                    probe[0] = describe(maid.getNavigation()
                            .createPath(beacon, 0));
                }
                if (!maid.getBrain().hasMemoryValue(
                        MemoryModuleType.WALK_TARGET)) {
                    maid.getBrain().setMemory(
                            MemoryModuleType.WALK_TARGET,
                            new WalkTarget(new BlockPosTracker(beacon),
                                    0.7F, 0)
                    );
                }
            });
        }

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(
                    arrived[0],
                    "The beacon over the void never moved her; probe="
                            + probe[0] + "; " + diaryOf(maid)
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /** 桥面公用件：一排玻璃，x0 到 x8。 */
    private static void buildBridge(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            helper.setBlock(new BlockPos(x, DECK, 1), Blocks.GLASS);
        }
    }

    /** 实体目标版过桥：桥尾站一只女仆当目标，缺席才写走目标，贴近实机节律。 */
    private static void chaseFrom(
            GameTestHelper helper,
            double startX,
            double startY,
            double startZ
    ) {
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel());
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        maid.setPos(zero.getX() + startX, zero.getY() + startY,
                zero.getZ() + startZ);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid body = new EntityMaid(helper.getLevel());
        BlockPos farCell = helper.absolutePos(new BlockPos(7, DECK + 1, 1));
        body.setPos(farCell.getX() + 0.5D, farCell.getY(),
                farCell.getZ() + 0.5D);
        body.setTame(true);
        body.setPickup(false);
        body.setNoAi(true);
        helper.getLevel().addFreshEntity(body);

        double gx = farCell.getX() + 0.5D;
        boolean[] arrived = new boolean[]{false};
        String[] probe = new String[]{"unprobed"};
        StringBuilder tape = new StringBuilder();

        for (int tick = 1; tick <= 280; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                if (arrived[0]) {
                    return;
                }
                if (Math.abs(maid.getX() - gx) < 2.6D) {
                    arrived[0] = true;
                    return;
                }
                if (at % 20 == 0 && tape.length() < 400) {
                    tape.append(String.format("%d:(%.1f,%.1f,%.1f) ",
                            at,
                            maid.getX() - zero.getX(),
                            maid.getY() - zero.getY(),
                            maid.getZ() - zero.getZ()
                    ));
                }
                if (at == 60) {
                    probe[0] = describe(maid.getNavigation()
                            .createPath(body, 1));
                }
                if (!maid.getBrain().hasMemoryValue(
                        MemoryModuleType.WALK_TARGET)) {
                    maid.getBrain().setMemory(
                            MemoryModuleType.WALK_TARGET,
                            new WalkTarget(new EntityTracker(body, false),
                                    0.7F, 2)
                    );
                }
            });
        }

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(
                    arrived[0],
                    "Chasing a body she never set out; probe=" + probe[0]
                            + "; " + diaryOf(maid) + "; tape(rel)=" + tape
            );
            maid.discard();
            body.discard();
            helper.succeed();
        });
    }

    /** 从给定脚感出发驱动过桥，六格外算过。失败附日记、建路探针与读数带。 */
    private static void crossFrom(
            GameTestHelper helper,
            double startX,
            double startY,
            double startZ
    ) {
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel());
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        maid.setPos(zero.getX() + startX, zero.getY() + startY,
                zero.getZ() + startZ);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        BlockPos far = new BlockPos(7, DECK + 1, 1);
        double gx = helper.absolutePos(far).getX() + 0.5D;
        boolean[] arrived = new boolean[]{false};
        String[] probe = new String[]{"unprobed"};
        StringBuilder tape = new StringBuilder();

        for (int tick = 1; tick <= 280; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                if (arrived[0]) {
                    return;
                }
                if (Math.abs(maid.getX() - gx) < 1.6D) {
                    arrived[0] = true;
                    return;
                }
                if (at % 20 == 0 && tape.length() < 400) {
                    tape.append(String.format("%d:(%.1f,%.1f,%.1f) ",
                            at,
                            maid.getX() - zero.getX(),
                            maid.getY() - zero.getY(),
                            maid.getZ() - zero.getZ()
                    ));
                }
                if (at == 40) {
                    probe[0] = describe(maid.getNavigation()
                            .createPath(helper.absolutePos(far), 0));
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(
                                new BlockPosTracker(helper.absolutePos(far)),
                                0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(
                    arrived[0],
                    "She never set out from the awkward footing; probe="
                            + probe[0] + "; " + diaryOf(maid)
                            + "; tape(rel)=" + tape
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

    /** 之字梯底台角落冷启动：从冻结坐标出发，重新爬上顶台。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 320)
    public static void fromThePadCornerSheSetsOutAgain(
            GameTestHelper helper
    ) {
        buildZigzag(helper);
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel());
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        maid.setPos(zero.getX() + 0.6D, zero.getY() + DECK + 1.0D,
                zero.getZ() + 2.3D);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        BlockPos top = new BlockPos(2, DECK + 5, 4);
        double gx = helper.absolutePos(top).getX() + 0.5D;
        double gz = helper.absolutePos(top).getZ() + 0.5D;
        boolean[] arrived = new boolean[]{false};
        String[] probe = new String[]{"unprobed"};

        for (int tick = 1; tick <= 280; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                if (arrived[0]) {
                    return;
                }
                if (Math.hypot(maid.getX() - gx, maid.getZ() - gz) < 1.2D) {
                    arrived[0] = true;
                    return;
                }
                // 第四十 tick 让图自己作证：手动建一条路，把节点串留档。
                if (at == 40) {
                    probe[0] = describe(maid.getNavigation()
                            .createPath(helper.absolutePos(top), 0));
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(
                                new BlockPosTracker(helper.absolutePos(top)),
                                0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(
                    arrived[0],
                    "From the pad corner she never set out again; probe="
                            + probe[0] + "; at ("
                            + String.format("%.1f,%.1f,%.1f",
                                    maid.getX() - zero.getX(),
                                    maid.getY() - zero.getY(),
                                    maid.getZ() - zero.getZ()) + ")"
            );
            maid.discard();
            helper.succeed();
        });
    }

    /** 手动建路的证词：节点数、能否到达、逐节点相对坐标。 */
    private static String describe(Path path) {
        if (path == null) {
            return "null-path";
        }
        StringBuilder out = new StringBuilder();
        out.append(path.getNodeCount()).append(" nodes, canReach=")
                .append(path.canReach()).append(":");
        for (int i = 0; i < path.getNodeCount(); i++) {
            out.append(" (").append(path.getNode(i).x)
                    .append(",").append(path.getNode(i).y)
                    .append(",").append(path.getNode(i).z).append(")");
        }
        return out.toString();
    }

    /** 与往返压测里的之字梯场景一比一：底台、单块交替升高、顶台，全悬空。 */
    private static void buildZigzag(GameTestHelper helper) {
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
    }
}
