package com.laixia.maidintelligence.gametest.pathing.patrol;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 拐弯的行道上往返：路不是一条直线，掉头也不在一条线上。
 *
 * <p>从 {@code RoundTripGameTests} 按职责拆出（单文件五百行的布局纪律）。
 * 直桥那三条的两端是两个 x 门槛，这两条不是——之字梯每一步都带九十度转
 * 弯，玻璃行的远端拐进另一条 z 行。判据也跟着换了主敌：深渊上摔是主敌，
 * 这两条上**原地打转**才是（浅地形摔不死人，之字梯摔与卡各占一半）。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class WindingPatrolGameTests {
    private static final int DECK = BridgePatrol.DECK;
    private static final int WATCHED = BridgePatrol.WATCHED_TICKS;

    private WindingPatrolGameTests() {
    }

    /**
     * 悬空之字梯：单块交替升高、每一步都带转弯，爬上去再爬回来。
     *
     * <p>玩家截图场景的一比一。之字梯每一步都是"贴脸登阶 + 拐弯"：登阶跳
     * 滞空的十几 tick 里，路标已经指向拐角方向，空中转向若还听路标的，她
     * 就在半空被拽出侧沿。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "windingpatrol", timeoutTicks = 470)
    public static void upAndDownTheZigzagStairsSheStays(
            GameTestHelper helper
    ) {
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
        // 底台，之字梯（东、东、南），顶台。全部悬空玻璃。
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
        patrol(helper, DECK + 1, 1, 0.7D,
                new BlockPos(2, DECK + 5, 4), new BlockPos(0, DECK + 1, 1),
                true, "the zigzag stairs");
    }

    /**
     * 浅地形上的玻璃行道：地面就在脚下一两格，行道带缺口、带拐角、带升高。
     *
     * <p>玩家实机截图的一比一（草地上铺的单排玻璃）。深渊场景里摔是主敌；
     * 浅地形上摔不死人，主敌变成**原地旋转卡死**——刹停、重铺、加速的循环
     * 或对角夹缝的爬跳弹回。断言趟数，失败附行车记录与读数带。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "windingpatrol", timeoutTicks = 470)
    public static void alongTheGlassRowsOverShallowGround(
            GameTestHelper helper
    ) {
        for (int x = -1; x <= 10; x++) {
            for (int z = -1; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 1), Blocks.GLASS);
        }
        // x=5 断一格：露出低一格的地面。
        helper.setBlock(new BlockPos(6, DECK + 1, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(7, DECK + 2, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(8, DECK + 2, 1), Blocks.GLASS);
        for (int z = 2; z <= 4; z++) {
            helper.setBlock(new BlockPos(8, DECK + 2, z), Blocks.GLASS);
        }
        patrol(helper, DECK + 2, 1, 0.5D,
                new BlockPos(8, DECK + 3, 4), new BlockPos(0, DECK + 2, 1),
                false, "the glass rows");
    }

    /**
     * 平台沿口下一格：上台与下地之间往返，下行只是**走下去**。
     *
     * <p>实机点名的场景一比一："在方块边缘准备下方块时会抽搐一小会，或
     * 者来回在边缘不下去。"这一场拿停滞表当尺：往返本身逼她真下去、真
     * 爬回，沿口犹豫直接体现为原地打转或趟数不足。
     *
     * <p>**独立成批**：第一版挤进 windingpatrol，同批打包整体挪位、后续
     * 批次又在同格继承了这场的 DECK+1 石台——杆桥一族全红、玻璃行连坐
     * （sw188/189，台账 §5 的原案又添一例）。自己开一批，进出都不动别
     * 人的格局。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "ledgestep", timeoutTicks = 470)
    public static void offTheLedgeSheStepsDownToTheWallBase(
            GameTestHelper helper
    ) {
        // 收场自清（先注册，收官 tick 先清后判）：画下的台面抹回空气。
        // 批次之间世界不还原，这格后来的住户会继承幻影地板。
        helper.runAfterDelay(WATCHED, () -> {
            for (int x = 0; x <= 10; x++) {
                for (int z = 0; z <= 6; z++) {
                    for (int y = DECK - 2; y <= DECK + 5; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
            }
        });
        // 建场前清本场（批次之间世界不还原，台账 §5）。
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 6; z++) {
                for (int y = DECK - 2; y <= DECK + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        // 下地一整片，西侧抬一格成台：沿墙在 x=4/5 之间，落差一格。
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, DECK + 1, z), Blocks.STONE);
            }
        }
        // 远端放在离墙两格半外：贴着墙根的话，人站在沿口上方水平距离就
        // 进了翻趟圈（<1.2），没下过台阶这条测试也会绿——那就白钉了。
        patrol(helper, DECK + 2, 3, 0.5D,
                new BlockPos(7, DECK + 1, 3), new BlockPos(1, DECK + 2, 3),
                false, "the ledge step-down");
    }

    /**
     * 奔跑档下台阶：**沿口不许犹豫**。实机点名："平地奔跑下方块时总在
     * 边缘抽搐一会才决定下去"——安全判定慢半拍。慢档那条钉（上一场，
     * 0.7 档）是绿的；抽搐随速度放大（预演视野 = 步速 × 前瞻 tick），
     * 所以这条按奔跑档写单，专量**沿口滞留**：人在沿口一格带里、脚还在
     * 上层的 tick 数。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "ledgestep", timeoutTicks = 300)
    public static void atARunSheStepsOffTheLedgeWithoutDithering(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 6; z++) {
                for (int y = DECK - 2; y <= DECK + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, DECK + 1, z), Blocks.STONE);
            }
        }
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        EntityMaid maid = BridgePatrol.maidAt(helper, zero.getX() + 1.5D,
                zero.getY() + DECK + 2.0D, zero.getZ() + 3.5D);
        var trace = new com.laixia.maidintelligence.gametest.support
                .PathwalkTrace("run ledge step-down", zero);
        int[] rimDwell = new int[]{0};
        boolean[] down = new boolean[]{false};
        double rimX = zero.getX() + 5.0D;
        double upperFeet = zero.getY() + DECK + 2.0D;
        for (int at = 2; at <= 260; at++) {
            int tick = at;
            helper.runAfterDelay(at, () -> {
                trace.sample(tick, maid);
                // 奔跑档写单（1.15 ≈ 冲刺跟随的步速档）。
                maid.getBrain().setMemory(
                        net.minecraft.world.entity.ai.memory
                                .MemoryModuleType.WALK_TARGET,
                        new net.minecraft.world.entity.ai.memory.WalkTarget(
                                new net.minecraft.world.entity.ai.behavior
                                        .BlockPosTracker(new BlockPos(
                                                zero.getX() + 8,
                                                zero.getY() + DECK + 1,
                                                zero.getZ() + 3)),
                                1.15F, 0));
                if (maid.getY() < upperFeet - 0.5D) {
                    down[0] = true;
                }
                if (!down[0] && Math.abs(maid.getX() - rimX) < 1.0D
                        && maid.getY() > upperFeet - 0.1D) {
                    rimDwell[0]++;
                }
            });
        }
        helper.runAfterDelay(280, () -> {
            double left = Math.hypot(maid.getX() - (zero.getX() + 8.5D),
                    maid.getZ() - (zero.getZ() + 3.5D));
            for (int x = 0; x <= 10; x++) {
                for (int z = 0; z <= 6; z++) {
                    for (int y = DECK - 2; y <= DECK + 5; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
            }
            trace.dump();
            maid.discard();
            helper.assertTrue(down[0] && left < 1.5D,
                    "奔跑档没下到台下（差 " + String.format("%.1f", left)
                            + " 格）；沿口滞留 " + rimDwell[0]
                            + "t（读数带见上方 dump）");
            helper.assertTrue(rimDwell[0] <= 20,
                    "沿口犹豫了 " + rimDwell[0] + " tick 才下去（限 20；"
                            + "读数带见上方 dump）");
            helper.succeed();
        });
    }

    /**
     * 两个路标之间往返：到点掉头、计趟，全程记读数带与停滞表。
     *
     * <p>{@code watchFalls} 分开的是两种场景的主敌：悬空的路摔一次就是红，
     * 浅地形上摔不死人、只判卡住——把两者合成一条断言会让浅地形那条测起
     * 别的东西。
     */
    private static void patrol(
            GameTestHelper helper,
            int spawnFeetY,
            int spawnZ,
            double offZ,
            BlockPos far,
            BlockPos home,
            boolean watchFalls,
            String where
    ) {
        double offX = 0.4D
                + helper.getLevel().getRandom().nextDouble() * 1.2D;
        BlockPos base = helper.absolutePos(new BlockPos(0, spawnFeetY, spawnZ));
        EntityMaid maid = BridgePatrol.maidAt(helper, base.getX() + offX,
                base.getY(), base.getZ() + offZ);

        double baseFeet = base.getY();
        double[] lowest = new double[]{maid.getY()};
        boolean[] headingOut = new boolean[]{true};
        int[] legs = new int[]{0};
        StringBuilder tape = new StringBuilder();
        BridgePatrol.StallWatch stall = new BridgePatrol.StallWatch(maid);
        String[] fell = new String[]{null};

        for (int tick = 1; tick <= WATCHED; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                if (at % 5 == 0 && tape.length() < 900) {
                    BridgePatrol.tapeRow(helper, tape, at, maid);
                }
                if (watchFalls && maid.getY() < baseFeet - 1.5D) {
                    if (fell[0] == null) {
                        fell[0] = "fell at t=" + at + " "
                                + BridgePatrol.diaryOf(maid);
                    }
                    return;
                }
                stall.sample(at);
                BlockPos goal = headingOut[0] ? far : home;
                if (BridgePatrol.flatTo(helper, maid, goal) < 1.2D) {
                    headingOut[0] = !headingOut[0];
                    legs[0]++;
                }
                BridgePatrol.sendTo(helper, maid,
                        headingOut[0] ? far : home);
            });
        }

        helper.runAfterDelay(WATCHED, () -> {
            if (watchFalls) {
                helper.assertTrue(
                        lowest[0] > baseFeet - 1.5D,
                        "She was flung off " + where + " (legs " + legs[0]
                                + "): lowest y " + lowest[0] + "; " + fell[0]
                                + "; tape(rel)=" + tape
                );
            }
            helper.assertTrue(
                    legs[0] >= 2,
                    "She only finished " + legs[0] + " legs on " + where
                            + " — stuck; " + BridgePatrol.diaryOf(maid)
                            + "; tape(rel)=" + tape
            );
            helper.assertTrue(
                    stall.longest()
                            <= BridgePatrol.StallWatch.TOLERATED_TICKS,
                    "She spun in place on " + where + " for "
                            + stall.longest() + " ticks; "
                            + BridgePatrol.diaryOf(maid)
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }
}
