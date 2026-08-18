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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 立足点安全：她不踩没有地板的格子。
 *
 * <p>场景照实机报告一比一搭：八格长条桥，打掉中间两格，缺口两侧的空格里放**开着
 * 的**活板门。原版寻路把活板门格（{@code BlockPathTypes.TRAPDOOR}）当成可以落脚
 * 的地面而**从不检查下面有没有地板**——刷怪塔骗怪跳崖用的就是这一手。她走的是同
 * 一套节点评估，于是径直走进缺口摔下去。
 *
 * <p>断言不看她走没走到，只看**她有没有跌到桥面以下**。修好之后合法的过法有
 * 两种：认出缺口后拒走，或者当成跑酷缺口起跳跨过去（两格在射程内）——两种都
 * 脚不离安全面；唯一的失败是把活板门当地板走进去摔下去。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class FootingHazardGameTests {
    /** 桥面高度（结构内相对 y）。摔下去落差四格，读数上一目了然。 */
    private static final int DECK = 5;

    /** 看多少 tick。走完八格绰绰有余，摔下去更是当场可见。 */
    private static final int WATCHED_TICKS = 160;

    private FootingHazardGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 200)
    public static void anOpenTrapdoorGapIsNotAFloor(GameTestHelper helper) {
        EntityMaid maid = bridgeScene(helper, true);
        double deckFeet = maid.getY();
        double[] lowest = new double[]{deckFeet};
        StringBuilder tape = new StringBuilder();

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                if (at % 5 == 0 && tape.length() < 700) {
                    tape.append(String.format("%d:(%.1f,%.1f,%.1f) ",
                            at,
                            maid.getX() - helper.absolutePos(BlockPos.ZERO).getX(),
                            maid.getY() - helper.absolutePos(BlockPos.ZERO).getY(),
                            maid.getZ() - helper.absolutePos(BlockPos.ZERO).getZ()
                    ));
                }
                // 每 tick 重新催她过桥，模拟一个执意要过去的差事。
                // 摔了就停手。结局已经写进读数了，继续驱动只会让她在世界底层
                // 游荡进邻居的结构，把不相干的测试搅红。
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
                    "She walked into the open-trapdoor gap and fell: lowest y "
                            + lowest[0] + " vs deck " + deckFeet
                            + "; rawTypes gap=" + rawType(helper, 3, DECK, 1)
                            + "/" + rawType(helper, 4, DECK, 1)
                            + "; nav=" + maid.getNavigation().getClass()
                                    .getSimpleName()
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /** 那一格在原版节点评估眼里是什么。 */
    private static String rawType(GameTestHelper helper, int x, int y, int z) {
        return RawProbe.at(helper, helper.absolutePos(new BlockPos(x, y, z)));
    }

    /** {@code getBlockPathTypeRaw} 是 protected，借子类身份读一眼。 */
    private static final class RawProbe
            extends net.minecraft.world.level.pathfinder.WalkNodeEvaluator {
        static String at(GameTestHelper helper, BlockPos pos) {
            return getBlockPathTypeRaw(helper.getLevel(), pos).name();
        }
    }

    /**
     * 正向对照：**关着的**下半活板门是合法的桥面，她必须照常走过去。
     *
     * <p>这条测试防的是修过头。立足点规则一旦写成"活板门一律不可信"，玩家用
     * 活板门搭的桥、盖的地板就全废了——关着的下半活板门自己就是地板（碰撞顶面
     * 三/十六格），规则里"自身站得住"那半句就是为它留的。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 200)
    public static void aClosedTrapdoorBridgeIsStillARoad(
            GameTestHelper helper
    ) {
        EntityMaid maid = bridgeScene(helper, false);
        BlockState shut = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, false)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM);
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(new BlockPos(3, DECK, z), shut);
            helper.setBlock(new BlockPos(4, DECK, z), shut);
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
                            maid.getX() - helper.absolutePos(BlockPos.ZERO).getX(),
                            maid.getY() - helper.absolutePos(BlockPos.ZERO).getY(),
                            maid.getZ() - helper.absolutePos(BlockPos.ZERO).getZ()
                    ));
                }
                // 摔了就停手。结局已经写进读数了，继续驱动只会让她在世界底层
                // 游荡进邻居的结构，把不相干的测试搅红。
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
                    "She fell off a perfectly good closed-trapdoor bridge: "
                            + "lowest y " + lowest[0]
                            + "; tape(rel)=" + tape
            );
            double crossed = helper.absolutePos(
                    new BlockPos(6, DECK + 1, 1)).getX();
            helper.assertTrue(
                    farthest[0] >= crossed,
                    "A closed-trapdoor bridge was refused as a road: she got "
                            + "to x " + farthest[0] + " and the far side is "
                            + crossed
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 缺口里沉着一块关门板，跳跃线不许被它掐死。
     *
     * <p>玩家实测的反直觉现象：缺口底下放着**关着的**下半活板门时她不跳，
     * 打开门板反而跳了。根子是把"弧下有立足物"一律当成矮一头的路掐掉跳跃
     * 线——孤板连不成路，她两头不是。正确的尺是高度：立足物顶面低出走面
     * 半格以上是低洼，弧线从上面过是合法跑酷。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 200)
    public static void aClosedLidSunkInTheGapIsNoBarToTheLeap(
            GameTestHelper helper
    ) {
        EntityMaid maid = bridgeScene(helper, false);
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
     * <p>玩家实测的僵住：同一层侧放、关闭的下半活板门（下方悬空），她站上去
     * 就动弹不得，也上不去旁边的方块。根子在原版 getFloorLevel 只看下一格：
     * 门板下是空气，起点地板被算到一格以下，迈向邻块的台阶差被算成两格高，
     * 起点连不出任何邻居。修法是沉在格子里的地板按格子自身报高度。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 200)
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

        double[] lowest = new double[]{DECK + 1.0D};
        double[] farthest = new double[]{maid.getX()};

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
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
     * <p>玩家实测报的僵住：她站在倒扣（顶半关）的活板门上一步不动。顶半关门
     * 是贴着格子天花板高度的一整块平板，站的人站在上一格；把它审成空气，她
     * 的脚就踩在"空气"的顶盖上，寻路起点不成立，人定在原地。正确分类是墙体
     * （BLOCKED），上一格照常可走。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 200)
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
                            + "; gapType=" + rawType(helper, 3, DECK, 1)
            );
            maid.discard();
            helper.succeed();
        });
    }

    /** 对照：没有活板门的裸缺口——不是地板，是缺口；跳过去可以，走进去不行。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 200)
    public static void aBareGapIsNotAFloorEither(GameTestHelper helper) {
        EntityMaid maid = bridgeScene(helper, false);
        double deckFeet = maid.getY();
        double[] lowest = new double[]{deckFeet};
        StringBuilder tape = new StringBuilder();

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                if (at % 5 == 0 && tape.length() < 700) {
                    tape.append(String.format("%d:(%.1f,%.1f,%.1f) ",
                            at,
                            maid.getX() - helper.absolutePos(BlockPos.ZERO).getX(),
                            maid.getY() - helper.absolutePos(BlockPos.ZERO).getY(),
                            maid.getZ() - helper.absolutePos(BlockPos.ZERO).getZ()
                    ));
                }
                // 摔了就停手。结局已经写进读数了，继续驱动只会让她在世界底层
                // 游荡进邻居的结构，把不相干的测试搅红。
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
                    "She walked into the bare gap and fell: lowest y "
                            + lowest[0] + " vs deck " + deckFeet
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 八格桥，中间两格是缺口；{@code trapdoors} 为真时在缺口两格里放开着的
     * 活板门（顶部铰链、翻板贴着缺口两壁——刷怪塔的经典摆法）。
     */
    private static EntityMaid bridgeScene(
            GameTestHelper helper,
            boolean trapdoors
    ) {
        // 桥下留空，桥面下方 DECK 格是硬地——摔下去读数明确又不会摔死。
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 桥面三格宽。第一版一格宽，她在"下到活板门、再跳回石面"的过程中会从
        // 侧面滑下去——摔是摔了，摔的却不是要测的那个原因，连关着门的对照都被
        // 染红。这条测试的危险源必须只有缺口本身。
        for (int x = 0; x <= 7; x++) {
            if (x == 3 || x == 4) {
                continue;
            }
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        // 护栏。没有它，"从活板门面跳回石面"的侧向漂移就能把她带出桥沿——
        // 轨迹实测 tick 30 漂到 z=-0.7 摔下去，摔的不是要测的原因。这组测试的
        // 唯一危险源必须是缺口本身。
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
        if (trapdoors) {
            BlockState west = Blocks.OAK_TRAPDOOR.defaultBlockState()
                    .setValue(TrapDoorBlock.OPEN, true)
                    .setValue(TrapDoorBlock.HALF, Half.TOP)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING,
                            net.minecraft.core.Direction.EAST);
            BlockState east = Blocks.OAK_TRAPDOOR.defaultBlockState()
                    .setValue(TrapDoorBlock.OPEN, true)
                    .setValue(TrapDoorBlock.HALF, Half.TOP)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING,
                            net.minecraft.core.Direction.WEST);
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(3, DECK, z), west);
                helper.setBlock(new BlockPos(4, DECK, z), east);
            }
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 0, DECK + 1, 1));
        maid.setTame(true);
        // 拾物关掉、摔落即停：这只女仆要是摔下去还被继续驱动，会在世界底层
        // 游荡进邻居的结构，把不相干的测试搅红——闪避那条就这么闪过三次。
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        return maid;
    }
}
