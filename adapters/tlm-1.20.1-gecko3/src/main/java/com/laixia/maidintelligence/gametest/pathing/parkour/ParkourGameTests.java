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
 * 跑酷：能跳多远跳多远，路线谁短谁赢——没有第二套决策系统，A* 按总距
 * 离选路，起跳推力按落点配速、只存在于滞空那十几 tick。
 *
 * <p>射程一到三格（人形物理极限）；四格推力也够不着——拒走线，摔不得。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class ParkourGameTests {
    private static final int DECK = 5;
    private static final int WATCHED_TICKS = 180;

    /** 桥总长（x 0..LENGTH），缺口挖在中段，两侧都留起跳/落脚的实地。 */
    private static final int LENGTH = 9;

    private ParkourGameTests() {
    }

    /** 一格缺口：起手式。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 260)
    public static void aOneWideGapIsWorthAJump(GameTestHelper helper) {
        crossingScene(helper, x -> x == 4, z -> true, true);
    }

    /** 两格缺口：推力配速跨过去——修活板门摔落时它还是拒走线，现在是跳板。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 260)
    public static void aTwoWideGapIsJumpedWithALeap(GameTestHelper helper) {
        crossingScene(helper, x -> x == 4 || x == 5, z -> true, true);
    }

    /** 三格缺口：物理极限之内，仍然过。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 260)
    public static void aThreeWideGapIsStillWithinHerLegs(
            GameTestHelper helper
    ) {
        crossingScene(helper, x -> x >= 3 && x <= 5, z -> true, true);
    }

    /** 四格缺口：极限之外，拒走且不摔。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 260)
    public static void aFourWideGapIsWhereSheDrawsTheLine(
            GameTestHelper helper
    ) {
        crossingScene(helper, x -> x >= 3 && x <= 6, z -> true, false);
    }

    /**
     * 直线飞越赢过绕路：缺口断了两条道、第三条完好，但直线（跳，总价约二点五）
     * 比侧向绕行（约三到四）短——**她应该跳，绕路是多余的路径**。
     *
     * <p>这条测试的前身断言相反（定价四时绕路赢）；定价按"距离最优"反转后，
     * 契约与断言一起反转，反转在明处。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 260)
    public static void aStraightJumpBeatsTheDetour(GameTestHelper helper) {
        EntityMaid maid = deck(helper, x -> x == 4, z -> z <= 1);
        double deckFeet = maid.getY();
        double[] lowest = new double[]{deckFeet};
        double[] farthest = new double[]{maid.getX()};
        boolean[] flewOverGap = new boolean[]{false};
        // 她**从哪条道过的缺口**：z~1.5 直跳道、z~2.5 绕路道——连红四
        // 十轮只说"她没跳"，是这条读数最终定的案（台账 §3）。
        double[] crossZ = new double[]{-1.0D};
        boolean[] crossGround = new boolean[]{true};

        drive(helper, maid, deckFeet, lowest, farthest, () -> {
            double relX = maid.getX()
                    - helper.absolutePos(BlockPos.ZERO).getX();
            double relZ = maid.getZ()
                    - helper.absolutePos(BlockPos.ZERO).getZ();
            if (relX > 4.05D && relX < 4.95D) {
                crossZ[0] = relZ;
                crossGround[0] = maid.onGround();
                if (relZ < 1.95D && !maid.onGround()) {
                    flewOverGap[0] = true;
                }
            }
        });

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > deckFeet - 1.5D,
                    "She fell: lowest y " + lowest[0]
            );
            double farSide = helper.absolutePos(
                    new BlockPos(LENGTH - 1, DECK + 1, 1)).getX();
            helper.assertTrue(
                    farthest[0] >= farSide,
                    "She never crossed: x " + farthest[0]
            );
            helper.assertTrue(
                    flewOverGap[0],
                    "The straight jump was shorter but she took the detour — "
                            + "she crossed the gap at z "
                            + String.format("%.2f", crossZ[0])
                            + (crossGround[0] ? " on the ground" : " airborne")
                            + " (z~1.5 = the jump lane, z~2.5 = the detour)"
            );
            sweep(helper);
            maid.discard();
            helper.succeed();
        });
    }


    /**
     * 提前跳上窄台，不许跳过头。
     *
     * <p>台阶提前跳（{@code maybeJumpAStep}，战斗 StepAhead 下放到走路的那份）
     * 的安全边界：一格宽的窄台、后面就是悬崖，目标就在台顶。提前跳只提前时机
     * 不加冲量，落地动量与原版撞跳同量级——她必须站上窄台停住，飞过台顶摔下
     * 悬崖就是跳过头了。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 260)
    public static void anEarlyStepJumpDoesNotOvershootALedge(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= LENGTH + 1; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int z = 0; z <= 2; z++) {
            for (int x = 0; x <= 3; x++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
            helper.setBlock(new BlockPos(4, DECK, z), Blocks.STONE);
            helper.setBlock(new BlockPos(4, DECK + 1, z), Blocks.STONE);
            // x5 起什么都没有：台顶再往前一步就是四格深的崖。
        }
        for (int x = -1; x <= LENGTH + 1; x++) {
            for (int y = DECK + 1; y <= DECK + 3; y++) {
                helper.setBlock(new BlockPos(x, y, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(new BlockPos(-1, DECK + 1, z), Blocks.STONE);
            helper.setBlock(new BlockPos(-1, DECK + 2, z), Blocks.STONE);
        }
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 0, DECK + 1, 1));
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);

        double startFeet = maid.getY();
        double[] lowest = new double[]{startFeet};

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                if (maid.getY() < startFeet - 1.5D) {
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(new BlockPos(4, DECK + 2, 1))
                        ), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > startFeet - 1.5D,
                    "She overshot the ledge and went off the cliff: lowest y "
                            + lowest[0]
            );
            BlockPos ledgeTop = helper.absolutePos(new BlockPos(4, DECK + 2, 1));
            helper.assertTrue(
                    maid.getY() >= ledgeTop.getY() - 0.1D
                            && Math.abs(maid.getX() - (ledgeTop.getX() + 0.5D))
                                    < 1.0D,
                    "She never settled on the ledge: at (" + maid.getX()
                            + ", " + maid.getY() + "), ledge top at "
                            + ledgeTop
            );
            sweep(helper);
            maid.discard();
            helper.succeed();
        });
    }

    /**
     * 半空里反转走目标，不能弯折这一跳。
     *
     * <p>玩家报的实机场景：跟随中主人半路飞到另一侧，空中转向跟着新目标走，
     * 把前冲速度拽没，她摔进缺口。修法是滞空期间落点锁死
     * （{@code SureFootedNavigation.steerTheLeap}）。这条测试在她滞空于缺口
     * 上方的那一刻把走目标反转回起点——锁没锁住，读数见分晓。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing", timeoutTicks = 260)
    public static void aMidAirRetargetCannotBendTheLeap(
            GameTestHelper helper
    ) {
        EntityMaid maid = deck(helper, x -> x >= 3 && x <= 5, z -> true);
        double deckFeet = maid.getY();
        double[] lowest = new double[]{deckFeet};
        boolean[] wasAloftOverGap = new boolean[]{false};

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                double relX = maid.getX()
                        - helper.absolutePos(BlockPos.ZERO).getX();
                if (relX > 3.05D && relX < 5.95D && !maid.onGround()) {
                    wasAloftOverGap[0] = true;
                }
                if (maid.getY() < deckFeet - 1.5D) {
                    return;
                }
                BlockPos target = wasAloftOverGap[0]
                        ? new BlockPos(0, DECK + 1, 1)
                        : new BlockPos(LENGTH, DECK + 1, 1);
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(target)), 0.7F, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    wasAloftOverGap[0],
                    "She never went aloft over the gap — scenario didn't arm"
            );
            helper.assertTrue(
                    lowest[0] > deckFeet - 1.5D,
                    "Mid-air retarget bent the leap and she fell: lowest y "
                            + lowest[0]
            );
            sweep(helper);
            maid.discard();
            helper.succeed();
        });
    }

    /** 通用跨越场景：断言不摔，且按 {@code shouldCross} 断言过没过去。 */
    private static void crossingScene(
            GameTestHelper helper,
            java.util.function.IntPredicate gap,
            java.util.function.IntPredicate gapLane,
            boolean shouldCross
    ) {
        EntityMaid maid = deck(helper, gap, gapLane);
        if (!shouldCross) {
            // 拒走场景的坑要是**真深渊**：浅坑（四格、有底板）在行为
            // 体系里完全合法——她闲下来后闲逛意图抽点抽到坑底，"计划
            // 内"下去散步（sw207 取证：goal=(8.5,-60,307.5) 等一串没人
            // 在测试里写过的地表单）。挖穿到基岩（深九格 > 干落容忍六
            // 格），降边不连、抽点无面，考题才考"拒走"。
            for (int x = -1; x <= LENGTH + 1; x++) {
                for (int z = -1; z <= 3; z++) {
                    for (int y = -3; y <= 1; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
            }
        }
        double deckFeet = maid.getY();
        double[] lowest = new double[]{deckFeet};
        double[] farthest = new double[]{maid.getX()};
        // 动作读数带（note/path/走目标逐 tick，坐标也在内）：四格缺口案
        // 翻面五轮、五种代码状态都对不上号，坐标看不出她"为什么"迈出沿
        // 口——下一次红让动作供词自己交代（dump 无条件打，绿轮也留档）。
        var trace = new com.laixia.maidintelligence.gametest.support
                .PathwalkTrace("parkour crossing",
                        helper.absolutePos(BlockPos.ZERO));
        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> trace.sample(at, maid));
        }
        drive(helper, maid, deckFeet, lowest, farthest, null);
        helper.runAfterDelay(WATCHED_TICKS - 1, trace::dump);

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > deckFeet - 1.5D,
                    "She fell: lowest y " + lowest[0] + " vs deck " + deckFeet
                            + "（动作读数带见上方 dump）"
            );
            double farSide = helper.absolutePos(
                    new BlockPos(LENGTH - 1, DECK + 1, 1)).getX();
            if (shouldCross) {
                helper.assertTrue(
                        farthest[0] >= farSide,
                        "A jumpable gap was never crossed: she got to x "
                                + farthest[0] + ", far side at " + farSide
                );
            } else {
                helper.assertTrue(
                        farthest[0] < farSide,
                        "She somehow crossed a gap that is past her limit: x "
                                + farthest[0] + "（动作读数带见上方 dump）"
                );
            }
            sweep(helper);
            maid.discard();
            helper.succeed();
        });
    }

    /** 逐 tick 驱动她过桥；摔了就停手，免得她在世界底层游荡搅红邻居。 */
    private static void drive(
            GameTestHelper helper,
            EntityMaid maid,
            double deckFeet,
            double[] lowest,
            double[] farthest,
            Runnable extraSample
    ) {
        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                farthest[0] = Math.max(farthest[0], maid.getX());
                if (extraSample != null) {
                    extraSample.run();
                }
                if (maid.getY() < deckFeet - 1.5D) {
                    return;
                }
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(
                                helper.absolutePos(
                                        new BlockPos(LENGTH, DECK + 1, 1))
                        ), 0.7F, 0)
                );
            });
        }
    }

    /** 进出双清（批次之间世界不还原，台账 §5）：缺口列里躺着邻批的幻影
     *  方块，四格缺口有时被垫成两格——她跳过去是"越限穿越"红，垫一半
     *  踩空是"她摔了"红，红绿全看上一任房客（sw195–203 翻面五轮的真
     *  相，动作读数带拍到她稳稳落在 x5 缺口中央）。 */
    private static void sweep(GameTestHelper helper) {
        for (int x = -1; x <= LENGTH + 1; x++) {
            for (int z = -1; z <= 3; z++) {
                for (int y = 1; y <= DECK + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    /** 三格宽带护栏的长桥。{@code gap} 说哪些 x 是缺口，{@code gapLane} 说断哪些 z 道。 */
    private static EntityMaid deck(
            GameTestHelper helper,
            java.util.function.IntPredicate gap,
            java.util.function.IntPredicate gapLane
    ) {
        sweep(helper);
        for (int x = 0; x <= LENGTH + 1; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= LENGTH; x++) {
            for (int z = 0; z <= 2; z++) {
                if (gap.test(x) && gapLane.test(z)) {
                    continue;
                }
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        for (int x = -1; x <= LENGTH + 1; x++) {
            for (int dy = 1; dy <= 2; dy++) {
                helper.setBlock(new BlockPos(x, DECK + dy, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, DECK + dy, 3), Blocks.STONE);
            }
        }
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(new BlockPos(-1, DECK + 1, z), Blocks.STONE);
            helper.setBlock(new BlockPos(-1, DECK + 2, z), Blocks.STONE);
        }
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
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
