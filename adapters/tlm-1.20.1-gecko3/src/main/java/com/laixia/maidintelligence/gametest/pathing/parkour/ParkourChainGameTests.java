package com.laixia.maidintelligence.gametest.pathing.parkour;

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
 * 跑酷的连招族：动作衔接处的物理边界（从 ParkourGameTests 按五百行布局
 * 纪律拆出，场地口径与本家一致）。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class ParkourChainGameTests {
    private static final int DECK = 5;
    private static final int WATCHED_TICKS = 180;
    private static final int LENGTH = 9;

    private ParkourChainGameTests() {
    }

    /** 进出双清，口径同本家（批次之间世界不还原，台账 §5）。 */
    private static void sweep(GameTestHelper helper) {
        for (int x = -1; x <= LENGTH + 1; x++) {
            for (int z = -1; z <= 3; z++) {
                for (int y = 1; y <= DECK + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    /**
     * 爬完台阶紧接着跳缺口，照样跳得过去。钉的是原版 noJumpDelay（起跳
     * 后十 tick JumpControl 不给竖直速度）——起跳直写速度后必须过。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 260)
    public static void aStepUpRightBeforeTheGapStillLeaps(
            GameTestHelper helper
    ) {
        sweep(helper);
        int low = DECK;
        int high = DECK + 1;
        for (int x = 0; x <= LENGTH + 1; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(new BlockPos(0, low, z), Blocks.STONE);
            helper.setBlock(new BlockPos(1, low, z), Blocks.STONE);
            helper.setBlock(new BlockPos(2, low, z), Blocks.STONE);
            helper.setBlock(new BlockPos(2, high, z), Blocks.STONE);
            for (int x = 5; x <= LENGTH; x++) {
                helper.setBlock(new BlockPos(x, high, z), Blocks.STONE);
            }
        }
        for (int x = -1; x <= LENGTH + 1; x++) {
            for (int y = low + 1; y <= high + 2; y++) {
                helper.setBlock(new BlockPos(x, y, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }
        for (int z = 0; z <= 2; z++) {
            for (int y = low + 1; y <= high + 2; y++) {
                helper.setBlock(new BlockPos(-1, y, z), Blocks.STONE);
            }
        }
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 0, low + 1, 1));
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
                if (maid.getY() < startFeet - 1.5D) {
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(
                                        new BlockPos(LENGTH, high + 1, 1))
                        ), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > startFeet - 1.5D,
                    "The step-up ate her jump and she fell: lowest y "
                            + lowest[0] + "（动作读数带见上方 dump）"
            );
            double farSide = helper.absolutePos(
                    new BlockPos(LENGTH - 1, high + 1, 1)).getX();
            helper.assertTrue(
                    farthest[0] >= farSide,
                    "She never crossed after the step-up: x " + farthest[0]
            );
            sweep(helper);
            maid.discard();
            helper.succeed();
        });
    }
}
