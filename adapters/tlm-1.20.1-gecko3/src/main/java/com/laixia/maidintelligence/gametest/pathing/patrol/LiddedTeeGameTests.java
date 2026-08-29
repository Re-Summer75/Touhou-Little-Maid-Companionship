package com.laixia.maidintelligence.gametest.pathing.patrol;

import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * 玩家实机截图的四桩：一格宽悬空梁上的门板与倒 T。
 *
 * <p>四条报告一一对应：横着的门板她认不认那是能站的地方；**竖着**的门板
 * （开着的活板门是一整格高的贴边竖片）她跨不跨得过去；倒 T（梁中间竖起一
 * 格，两侧各只剩一格落脚）会不会摔；以及在窄梁尽头**掉头**时会不会被跳的
 * 方向甩出去。
 *
 * <p>驾驶一律用奔跑跟随（{@link BridgePatrol#followPatrol}）——玩家点名，
 * 而且实机的摔全发生在这一种里：掉头不是测试喊的，是她自己在终点重新决定
 * 的，跳的方向也就此由她自己挑。每条各跑六遍：这些落点只有一格长，起跳点
 * 差半格结果就翻面，一次过说明不了什么。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class LiddedTeeGameTests {
    /** 抬到干落上限（六格）之外：脚下五格就有地面的话，主人在对岸时她
     *  会干脆跳下去走地面——那是合理行为，却把"她走不走得过去"这条测试
     *  换成了别的问题（实测：她从岛东端一跃而下，摔线当场定罪）。 */
    private static final int DECK = 9;
    private static final int DRIVE_TICKS = 900;

    private LiddedTeeGameTests() {
    }

    /**
     * 悬空的横门板就是桥面：关着的下半活板门底下什么都没有，照样站得住人
     * （原版里这就是隐形桥的做法）。她要认它、走过去、再走回来。
     */
    static void acrossTheHangingLidsSheWalksOn(GameTestHelper helper) {
        safetyFloor(helper);
        // 一格宽悬空梁，中段两格换成悬空的关门板（下半，底下没有任何支撑）。
        for (int x = 0; x <= 9; x++) {
            if (x == 4 || x == 5) {
                continue;
            }
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }
        BlockState lid = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, false)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                .setValue(TrapDoorBlock.FACING, Direction.NORTH);
        helper.setBlock(new BlockPos(4, DECK + 1, 2), lid);
        helper.setBlock(new BlockPos(5, DECK + 1, 2), lid);
        BridgePatrol.followPatrol(helper,
                at(helper, 1.5D, DECK + 1, 2.5D),
                at(helper, 8.5D, DECK + 1, 2.5D),
                at(helper, 0.5D, DECK + 1, 2.5D),
                4, feet(helper) - 1.6D, DRIVE_TICKS, "hanging lids");
    }
    /**
     * 竖着的门板：开着的活板门是**一整格高**的贴边竖片，横穿它那一面走不
     * 过去——但只有一格高，跳得过（玩家原话"可以小心跨越"）。她要么跳过
     * 去，要么绕，总之不能被它钉住。
     */
    static void overTheUprightLidSheGetsPast(GameTestHelper helper) {
        safetyFloor(helper);
        for (int x = 0; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }
        // 开着的门板立在走面格 x=5：贴着西面，整格高。
        helper.setBlock(new BlockPos(5, DECK + 1, 2),
                Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.OPEN, true)
                        .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                        .setValue(TrapDoorBlock.FACING, Direction.EAST));
        BridgePatrol.followPatrol(helper,
                at(helper, 1.5D, DECK + 1, 2.5D),
                at(helper, 8.5D, DECK + 1, 2.5D),
                at(helper, 0.5D, DECK + 1, 2.5D),
                4, feet(helper) - 1.6D, DRIVE_TICKS, "upright lid");
    }

    /**
     * 倒 T：一格宽的梁，中间一格竖起一格高。上去是一格长的孤台、下来又是
     * 一格长的落点，两次都没有后路——玩家实机就是在这儿掉下去的。
     */
    static void overTheInvertedTeeSheKeepsHerFeet(
            GameTestHelper helper
    ) {
        safetyFloor(helper);
        for (int x = 0; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }
        // 倒 T 的那一竖：梁中段顶上再垒一格。
        helper.setBlock(new BlockPos(5, DECK + 1, 2), Blocks.GLASS);

        BridgePatrol.followPatrol(helper,
                at(helper, 1.5D, DECK + 1, 2.5D),
                at(helper, 8.5D, DECK + 1, 2.5D),
                at(helper, 0.5D, DECK + 1, 2.5D),
                4, feet(helper) - 1.6D, DRIVE_TICKS, "inverted tee");
    }

    /**
     * 窄梁尽头掉头：两站都压在梁的**最后一格**上，她每到一站就得原地一百
     * 八十度转身再出发。玩家报的第四桩——转身那一下起跳方向不对就是下去了。
     */
    static void turningAtTheBeamsEndSheStaysOnIt(
            GameTestHelper helper
    ) {
        safetyFloor(helper);
        // 一格宽悬空梁，中段带一个缺口（掉头之后第一步就是跳）。
        for (int x = 0; x <= 7; x++) {
            if (x == 4) {
                continue;
            }
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }
        BridgePatrol.followPatrol(helper,
                at(helper, 1.5D, DECK + 1, 2.5D),
                at(helper, 7.5D, DECK + 1, 2.5D),
                at(helper, 0.5D, DECK + 1, 2.5D),
                6, feet(helper) - 1.6D, DRIVE_TICKS, "beam end turn");
    }

    /**
     * 倒 T 的**必要条件**：一侧空三格（玩家原话，稳定触发掉落）。
     *
     * <p>与上一条的区别只有那三格缺口，而它把两件难事叠在了一起：跨三格缺
     * 口是满推力的极限跳（跨度四），落点却只有一格长——落稳的下一步就是登
     * 上倒 T 那一竖，再下来又是一格长的落点。回程更狠：从倒 T 上下来，只有
     * 一格助跑就要起跳跨三格。
     */
    static void overTheInvertedTeeBeyondAThreeGapSheKeepsHerFeet(
            GameTestHelper helper
    ) {
        safetyFloor(helper);
        // 西侧引桥。
        for (int x = 0; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }
        // 空三格：x5、x6、x7。
        // 倒 T：横杠 x8..10，中间那一格顶上再垒一格。
        for (int x = 8; x <= 10; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }
        helper.setBlock(new BlockPos(9, DECK + 1, 2), Blocks.GLASS);

        BridgePatrol.followPatrol(helper,
                at(helper, 1.5D, DECK + 1, 2.5D),
                at(helper, 10.5D, DECK + 1, 2.5D),
                at(helper, 0.5D, DECK + 1, 2.5D),
                4, feet(helper) - 1.6D, DRIVE_TICKS, "tee beyond three-gap");
    }

    /**
     * 关着的门板当跳板：它在**上一格的下半**，不是下一格的上半。
     *
     * <p>玩家原话——两格高的柱子上，门板嵌在上面那一格的下半部分，于是它是
     * 一片伸在半空、顶面只高出一格出头（1.19）的檐。她跳得上去，可她认为不
     * 行。图上那条上跳的边只在**扫描撞到墙**时才连：脚下那一层是空的（檐下
     * 面就是虚空），扫描一路穿过去，压根不会去看上一格有没有落脚。
     */
    static void ontoTheLidLedgeAStepUpSheTakesIt(
            GameTestHelper helper
    ) {
        safetyFloor(helper);
        // 西侧引桥，走面 DECK+1。
        for (int x = 0; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }
        // 缺口 x5；x6 是那片檐：关着的下半门板，嵌在高一格那一格里，
        // 底下是虚空（檐顶 DECK+2.19，比她的脚高一格出头）。
        helper.setBlock(new BlockPos(6, DECK + 2, 2),
                Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.OPEN, false)
                        .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                        .setValue(TrapDoorBlock.FACING, Direction.WEST));
        // 檐后接着高一格的桥面，走面 DECK+2。
        for (int x = 7; x <= 10; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 2), Blocks.GLASS);
        }

        BridgePatrol.followPatrol(helper,
                at(helper, 1.5D, DECK + 1, 2.5D),
                at(helper, 10.5D, DECK + 2, 2.5D),
                at(helper, 0.5D, DECK + 1, 2.5D),
                4, feet(helper) - 1.6D, DRIVE_TICKS, "lid ledge step-up");
    }

    /**
     * 站在从墙上横伸出来的**下半门板檐**上，主人在侧后方——她不能转身
     * 跳下去。
     *
     * <p>玩家实测："下半活版门会直接转身侧边跳下去，而且是必然的"。这块
     * 檐只有一格见方、三面临空，脚面还是**小数高度**（0.1875）——窄道保持、
     * 唇沿判定、崖边看护都在这个高度上做判断，任何一处把小数当整数，她的
     * 一步就迈到沿外了。主人放在侧后方（北面隔着虚空），逼她考虑横向的路。
     */
    static void onTheLidLedgeWithHimAsideSheStaysOnIt(
            GameTestHelper helper
    ) {
        safetyFloor(helper);
        // 引桥（走面 DECK+1），缺口一格，然后是从墙上伸出来的门板檐。
        for (int x = 0; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }
        helper.setBlock(new BlockPos(6, DECK + 1, 2),
                Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.OPEN, false)
                        .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                        .setValue(TrapDoorBlock.FACING, Direction.WEST));
        // 檐依附的那面墙：两格高，挡住继续东行。
        helper.setBlock(new BlockPos(7, DECK + 1, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(7, DECK + 2, 2), Blocks.GLASS);

        // 主人在侧后方的半空：她够不着，所以**不能用"到站"判成败**——判据
        // 只有一条，别摔下去。
        BridgePatrol.stayOnYourFeet(helper,
                at(helper, 1.5D, DECK + 1, 2.5D),
                at(helper, 6.5D, DECK + 1, 6.5D),
                feet(helper) - 1.6D, 500, "lid ledge aside");
    }

    /** 摔下去落在自家院里，别搅进邻居的测试格。 */
    private static void safetyFloor(GameTestHelper helper) {
        for (int x = -1; x <= 10; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    /** 模板相对坐标换成绝对坐标（y 传的是脚面所在的格）。 */
    private static Vec3 at(GameTestHelper helper, double x, int feetY,
            double z) {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        return new Vec3(zero.getX() + x, zero.getY() + feetY, zero.getZ() + z);
    }

    /** 梁面的绝对高度，摔线按它算。 */
    private static double feet(GameTestHelper helper) {
        return helper.absolutePos(BlockPos.ZERO).getY() + DECK + 1;
    }

    /**
     * 玩家实机场景：**横放末地烛连成的单行桥**，带九十度转角——慢速行为
     * （散步、注视回应那档步速）在转角斜向跨过去掉进虚空，而奔跑跟随反而
     * 安全（玩家点名"转角处缺一根也是"）。杆面只有四分之一格宽、顶面
     * 0.625，是窄桥的极端形。
     */
    static void alongARodBridgeCornerSheWalksSlowlyAndSafely(
            GameTestHelper helper
    ) {
        rodCornerWalk(helper, true, "rod corner with elbow");
    }

    /** 同景减一根：转角那格空着，斜跨的诱惑更大。 */
    static void acrossAMissingElbowSheStillKeepsHerFeet(
            GameTestHelper helper
    ) {
        rodCornerWalk(helper, false, "rod corner missing elbow");
    }

    /**
     * 玩家实机的升级版：横烛桥**带断口**——杆段之间空一格，她得在四分之
     * 一格宽的杆面上起跳、跳过断口、落回杆面（玩家实测："不行，还是会摔
     * 下去"——实景桥的杆段间有断口，连续杆桥的钉抓不到它）。
     */
    static void acrossARodBridgeGapSheKeepsHerFeet(GameTestHelper helper) {
        safetyFloor(helper);
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 2),
                    Blocks.END_ROD.defaultBlockState()
                            .setValue(DirectionalBlock.FACING,
                                    Direction.EAST));
        }
        // 断口 x4。
        for (int x = 5; x <= 7; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 2),
                    Blocks.END_ROD.defaultBlockState()
                            .setValue(DirectionalBlock.FACING,
                                    Direction.EAST));
        }
        slowWalkAcross(helper,
                new Vec3(1.5D, DECK + 2.625D, 2.5D),
                new BlockPos(7, DECK + 2, 2), "rod gap bridge");
    }

    /** 转角+断口的组合（实机截图的形态）：x 段杆、断口、z 段杆。 */
    static void aroundARodBridgeBrokenElbowSheKeepsHerFeet(
            GameTestHelper helper
    ) {
        safetyFloor(helper);
        for (int x = 1; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 2),
                    Blocks.END_ROD.defaultBlockState()
                            .setValue(DirectionalBlock.FACING,
                                    Direction.EAST));
        }
        // 断口在转角处：(5, z2) 空着，z 段从 (5, z3) 起往南。
        for (int z = 3; z <= 6; z++) {
            helper.setBlock(new BlockPos(5, DECK + 1, z),
                    Blocks.END_ROD.defaultBlockState()
                            .setValue(DirectionalBlock.FACING,
                                    Direction.SOUTH));
        }
        slowWalkAcross(helper,
                new Vec3(1.5D, DECK + 2.625D, 2.5D),
                new BlockPos(5, DECK + 2, 6), "rod broken elbow");
    }

    /**
     * 一比一的行为驱动版：**主人站在桥上不动、她全靠"在你附近转转"自己
     * 走**（玩家实测点名：没断口也掉，专属这个行为；直写走目标的钉全
     * 绿——差异就在驱动。linger 的抽点、到站发呆、重抽循环与实机截图里
     * 的 climb 段都是直写驱动不会出现的形态）。判据只有一条：别掉。
     */
    static void lingeringOnARodBridgeSheStaysUp(GameTestHelper helper) {
        safetyFloor(helper);
        for (int x = 1; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 2),
                    Blocks.END_ROD.defaultBlockState()
                            .setValue(DirectionalBlock.FACING,
                                    Direction.EAST));
        }
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        BridgePatrol.stayOnYourFeet(helper,
                new Vec3(zero.getX() + 1.5D, zero.getY() + DECK + 2.625D,
                        zero.getZ() + 2.5D),
                new Vec3(zero.getX() + 5.5D, zero.getY() + DECK + 2.625D,
                        zero.getZ() + 2.5D),
                zero.getY() + DECK + 1.0D,
                600, "rod bridge linger");
    }

    /** 慢速档走线共用驱动：补写走目标、判摔、判到达。 */
    private static void slowWalkAcross(GameTestHelper helper, Vec3 spawnRel,
            BlockPos goal, String tape) {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        var maid = BridgePatrol.maidAt(helper,
                zero.getX() + spawnRel.x, zero.getY() + spawnRel.y,
                zero.getZ() + spawnRel.z);
        var trace = new com.laixia.maidintelligence.gametest.support
                .PathwalkTrace(tape, zero);
        for (int at = 2; at <= 640; at++) {
            int tick = at;
            helper.runAfterDelay(at, () -> trace.sample(tick, maid));
        }
        for (int at = 2; at <= 600; at += 40) {
            helper.runAfterDelay(at, () -> maid.getBrain().setMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType
                            .WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(
                            new net.minecraft.world.entity.ai.behavior
                                    .BlockPosTracker(
                                            helper.absolutePos(goal)),
                            0.45F, 0)));
        }
        double fallLine = zero.getY() + DECK + 1.0D;
        boolean[] fell = {false};
        // 摔那一刻的供词：坐标说得出"她掉了"，说不出"她在干什么"。
        String[] where = {"-"};
        for (int at = 2; at <= 640; at++) {
            helper.runAfterDelay(at, () -> {
                if (maid.getY() < fallLine && !fell[0]) {
                    fell[0] = true;
                    where[0] = BridgePatrol.diaryOf(maid);
                }
            });
        }
        helper.runAfterDelay(660, () -> {
            double left = BridgePatrol.flatTo(helper, maid, goal);
            trace.dump();
            maid.discard();
            helper.assertFalse(fell[0], tape + "：她从杆桥上掉下去了");
            helper.assertTrue(left < 1.3D,
                    tape + "：六百 tick 没走到对岸，还差 "
                            + String.format("%.1f", left) + " 格");
            helper.succeed();
        });
    }

    /** 横烛桥转角的慢速走线；转角格有没有杆由调用方给。 */
    private static void rodCornerWalk(GameTestHelper helper, boolean elbow,
            String tape) {
        safetyFloor(helper);
        // 西段：沿 x 的横杆 x1..x5；转角格 (6,2)；南段：沿 z 的横杆 z3..z7。
        for (int x = 1; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 2),
                    Blocks.END_ROD.defaultBlockState()
                            .setValue(DirectionalBlock.FACING,
                                    Direction.EAST));
        }
        if (elbow) {
            helper.setBlock(new BlockPos(6, DECK + 1, 2),
                    Blocks.END_ROD.defaultBlockState()
                            .setValue(DirectionalBlock.FACING,
                                    Direction.SOUTH));
        }
        for (int z = 3; z <= 7; z++) {
            helper.setBlock(new BlockPos(6, DECK + 1, z),
                    Blocks.END_ROD.defaultBlockState()
                            .setValue(DirectionalBlock.FACING,
                                    Direction.SOUTH));
        }

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        double deckTop = zero.getY() + DECK + 1.625D;
        var maid = BridgePatrol.maidAt(helper,
                zero.getX() + 1.5D, deckTop, zero.getZ() + 2.5D);
        BlockPos goal = new BlockPos(6, DECK + 2, 7);
        // 慢速档（散步/注视那档 0.45×）：每两秒补写一次走目标，防 sink
        // 中途擦掉。
        for (int at = 2; at <= 600; at += 40) {
            helper.runAfterDelay(at, () -> maid.getBrain().setMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType
                            .WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(
                            new net.minecraft.world.entity.ai.behavior
                                    .BlockPosTracker(
                                            helper.absolutePos(goal)),
                            0.45F, 0)));
        }
        double fallLine = zero.getY() + DECK + 1.0D;
        boolean[] fell = {false};
        // 摔那一刻的供词：坐标说得出"她掉了"，说不出"她在干什么"。
        String[] where = {"-"};
        for (int at = 2; at <= 640; at++) {
            helper.runAfterDelay(at, () -> {
                if (maid.getY() < fallLine && !fell[0]) {
                    fell[0] = true;
                    where[0] = BridgePatrol.diaryOf(maid);
                }
            });
        }
        helper.runAfterDelay(660, () -> {
            double left = BridgePatrol.flatTo(helper, maid, goal);
            maid.discard();
            helper.assertFalse(fell[0],
                    "慢速走横烛桥转角，她掉下去了：" + where[0]);
            helper.assertTrue(left < 1.3D,
                    "六百 tick 没走到对岸，还差 "
                            + String.format("%.1f", left) + " 格");
            helper.succeed();
        });
    }

    /**
     * 十连/多连钉的**并行展开**：框架的 attempts 语义是串行重跑（十次成
     * 功=十轮真实驱动排队），一批五十秒的场就要排上几百秒。这里把每条钉
     * 展开成 N 个独立实例同批并行——判据、驱动、判摔一字不动，语义仍是
     * "N 次独立成功"，墙钟除以 N。
     */
    @GameTestGenerator
    public static Collection<TestFunction> liddedTeeRuns() {
        List<TestFunction> runs = new ArrayList<>();
        PinSpread.spread(runs, "liddedtee", 6, 960, "hanginglids",
                LiddedTeeGameTests::acrossTheHangingLidsSheWalksOn);
        PinSpread.spread(runs, "liddedtee", 6, 960, "uprightlid",
                LiddedTeeGameTests::overTheUprightLidSheGetsPast);
        PinSpread.spread(runs, "liddedtee", 6, 960, "invertedtee",
                LiddedTeeGameTests::overTheInvertedTeeSheKeepsHerFeet);
        PinSpread.spread(runs, "liddedtee", 6, 960, "beamendturn",
                LiddedTeeGameTests::turningAtTheBeamsEndSheStaysOnIt);
        PinSpread.spread(runs, "liddedtee", 6, 960, "teebeyondgap",
                LiddedTeeGameTests
                        ::overTheInvertedTeeBeyondAThreeGapSheKeepsHerFeet);
        PinSpread.spread(runs, "liddedtee", 6, 960, "lidledgestepup",
                LiddedTeeGameTests::ontoTheLidLedgeAStepUpSheTakesIt);
        PinSpread.spread(runs, "liddedtee", 6, 660, "lidledgeaside",
                LiddedTeeGameTests::onTheLidLedgeWithHimAsideSheStaysOnIt);
        PinSpread.spread(runs, "liddedtee", 3, 700, "rodcornerelbow",
                LiddedTeeGameTests::alongARodBridgeCornerSheWalksSlowlyAndSafely);
        PinSpread.spread(runs, "liddedtee", 3, 700, "rodcornermissing",
                LiddedTeeGameTests::acrossAMissingElbowSheStillKeepsHerFeet);
        PinSpread.spread(runs, "liddedtee", 3, 700, "rodgapbridge",
                LiddedTeeGameTests::acrossARodBridgeGapSheKeepsHerFeet);
        PinSpread.spread(runs, "liddedtee", 3, 700, "rodbrokenelbow",
                LiddedTeeGameTests::aroundARodBridgeBrokenElbowSheKeepsHerFeet);
        return runs;
    }

}
