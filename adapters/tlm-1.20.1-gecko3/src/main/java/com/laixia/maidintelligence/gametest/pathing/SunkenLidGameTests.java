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
 * 沉盖与困板：关着的活板门以三种姿态摆进地形，每一种她都要走得出去。
 *
 * <p>从 {@code FootingHazardGameTests} 按族拆出（单文件五百行的布局纪律）。
 * 三条都来自实机报告：缺口里沉着关门板（跳跃线不许被掐死）、悬空下半门板
 * （站上去要爬得出来）、顶半门板平台（站上去要走得动）。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class SunkenLidGameTests {
    private static final int DECK = 5;
    private static final int WATCHED_TICKS = 160;

    private SunkenLidGameTests() {
    }

    /**
     * 缺口里沉着一块关门板，跳跃线不许被它掐死。
     *
     * <p>实测的反直觉现象：缺口底下放着**关着的**下半活板门时她不跳，打开
     * 门板反而跳了。根子是把"弧下有立足物"一律当成矮一头的路掐掉跳跃线——
     * 孤板连不成路，她两头不是。正确的尺是高度：立足物顶面低出走面半格以上
     * 是低洼，弧线从上面过是合法跑酷。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 200)
    public static void aClosedLidSunkInTheGapIsNoBarToTheLeap(
            GameTestHelper helper
    ) {
        EntityMaid maid = bridgeScene(helper);
        // 缺口两格，其中一格的底层沉着关着的下半门板——低洼，不是桥。
        BlockState lid = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, false)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM);
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(new BlockPos(3, DECK, z), lid);
        }
        double deckFeet = maid.getY();
        double[] lowest = new double[]{deckFeet};
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
                if (maid.getY() < deckFeet - 1.5D) {
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(new BlockPos(7, DECK + 1, 1))
                        ), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > deckFeet - 1.5D,
                    "She fell at the sunken lid: lowest y " + lowest[0]
                            + "; tape(rel)=" + tape
            );
            double crossed = helper.absolutePos(
                    new BlockPos(6, DECK + 1, 1)).getX();
            helper.assertTrue(
                    farthest[0] >= crossed,
                    "The sunken closed lid choked her leap: she got to x "
                            + farthest[0] + ", far side at " + crossed
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 站在悬空的下半门板上，要能自己爬上同层的路。
     *
     * <p>实测的僵住：同一层侧放、关闭的下半活板门（下方悬空），她站上去就
     * 动弹不得，也上不去旁边的方块。根子在原版 getFloorLevel 只看下一格：
     * 门板下是空气，起点地板被算到一格以下，迈向邻块的台阶差被算成两格高，
     * 起点连不出任何邻居。修法是沉在格子里的地板按格子自身报高度。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 200)
    public static void strandedOnASunkenLidSheClimbsBackOut(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 同层：路的方块和门板同在 DECK 层，门板下方什么都没有。
        BlockState lid = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, false)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM);
        helper.setBlock(new BlockPos(1, DECK, 1), lid);
        for (int x = 2; x <= 7; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        // 从一格高落到门板上：出生即被困在沉地板里，起点必须自己成立。
        maid.setPos(GameTestPositions.center(helper, 1, DECK + 1, 1));
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double[] farthest = new double[]{maid.getX()};

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                farthest[0] = Math.max(farthest[0], maid.getX());
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(new BlockPos(7, DECK + 1, 1))
                        ), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            double roadTop = helper.absolutePos(
                    new BlockPos(6, DECK + 1, 1)).getX();
            helper.assertTrue(
                    farthest[0] >= roadTop,
                    "She stayed stranded on the sunken lid: got to x "
                            + farthest[0] + ", road far side at " + roadTop
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 顶半关着的活板门平台是路：站上去必须走得动。
     *
     * <p>实测的僵住：她站在倒扣（顶半关）的活板门上一步不动。顶半关门是贴
     * 着格子天花板高度的一整块平板，站的人站在上一格；把它审成空气，她的脚
     * 就踩在"空气"的顶盖上，寻路起点不成立，人定在原地。正确分类是墙体
     * （BLOCKED），上一格照常可走。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 200)
    public static void aTopHalfTrapdoorPlatformIsStillARoad(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockState lid = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, false)
                .setValue(TrapDoorBlock.HALF, Half.TOP);
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), lid);
            }
        }
        for (int x = -1; x <= 7; x++) {
            for (int dy = 1; dy <= 2; dy++) {
                helper.setBlock(new BlockPos(x, DECK + dy, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, DECK + dy, 3), Blocks.STONE);
            }
        }
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(new BlockPos(-1, DECK + 1, z), Blocks.STONE);
            helper.setBlock(new BlockPos(-1, DECK + 2, z), Blocks.STONE);
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 0, DECK + 1, 1));
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double deckFeet = maid.getY();
        double[] lowest = new double[]{deckFeet};
        double[] farthest = new double[]{maid.getX()};

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                farthest[0] = Math.max(farthest[0], maid.getX());
                if (maid.getY() < deckFeet - 1.5D) {
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(new BlockPos(6, DECK + 1, 1))
                        ), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > deckFeet - 1.5D,
                    "She fell off the trapdoor platform: lowest y " + lowest[0]
            );
            double acrossIt = helper.absolutePos(
                    new BlockPos(5, DECK + 1, 1)).getX();
            helper.assertTrue(
                    farthest[0] >= acrossIt,
                    "She froze on the upside-down trapdoor platform: got to x "
                            + farthest[0] + ", expected past " + acrossIt
            );
            maid.discard();
            helper.succeed();
        });
    }

    /** 八格桥、中间两格缺口、三宽桥面带护栏——沉盖测试的公共舞台。 */
    private static EntityMaid bridgeScene(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 7; x++) {
            if (x == 3 || x == 4) {
                continue;
            }
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        for (int x = -1; x <= 8; x++) {
            for (int dy = 1; dy <= 2; dy++) {
                helper.setBlock(new BlockPos(x, DECK + dy, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, DECK + dy, 3), Blocks.STONE);
            }
        }
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(new BlockPos(-1, DECK + 1, z), Blocks.STONE);
            helper.setBlock(new BlockPos(-1, DECK + 2, z), Blocks.STONE);
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 0, DECK + 1, 1));
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        return maid;
    }
}
