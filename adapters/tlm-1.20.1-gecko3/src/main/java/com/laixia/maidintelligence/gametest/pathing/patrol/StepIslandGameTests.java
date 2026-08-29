package com.laixia.maidintelligence.gametest.pathing.patrol;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 玩家指定的摔跤复现场：带缺口的竖 L 岛，奔跑跟随，来回两趟，十连过关。
 *
 * <p>场景一比一按玩家口述：左空两格、右空三格夹着一座 L 形小岛——岛上一格
 * 比底部两格高一格，高的在左、低的在右；左侧高台的走面比岛的底部高两格
 * （"从两格高的方向跳过来"就是从这儿起跳）。全程一格宽、全悬空、玻璃。
 *
 * <p>驾驶也按实机：不喂走点，主人**跑**到一头、她用自己的跟随意图追
 * （{@link BridgePatrol#followPatrol}）。一次尝试走两个来回（四条腿），任何
 * 一步跌下桥立刻定罪；整条测试跑十次，十次全过才算过——玩家点名"不要一次
 * 过"。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class StepIslandGameTests {
    /** 抬到干落上限（六格）之外：脚下五格就有地面的话，主人在对岸时她
     *  会干脆跳下去走地面——那是合理行为，却把"她走不走得过去"这条测试
     *  换成了别的问题（实测：她从岛东端一跃而下，摔线当场定罪）。 */
    private static final int DECK = 9;

    /** 一个来回两条腿，玩家要两个来回。 */
    private static final int LEGS_WANTED = 4;

    private static final int DRIVE_TICKS = 900;

    private StepIslandGameTests() {
    }

    static void atARunSheFollowsOverTheSteppedIslandBothWaysTwice(
            GameTestHelper helper
    ) {
        // 兜底地面：摔下去落在自家院里，别搅进邻居的测试格。
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 高台（左）：x0..1，走面 DECK+3——比岛底高两格的出发侧。
        helper.setBlock(new BlockPos(0, DECK + 2, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(1, DECK + 2, 2), Blocks.GLASS);
        // 左空两格（x2..3）。竖 L 岛：高的一格在左（x4，走面 DECK+2），
        // 底部两格在右（x5..6，走面 DECK+1）。
        helper.setBlock(new BlockPos(4, DECK + 1, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(5, DECK, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(6, DECK, 2), Blocks.GLASS);
        // 右空三格（x7..9）。低台（右）：x10..12，走面 DECK+1。
        for (int x = 10; x <= 12; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        double laneZ = zero.getZ() + 2.5D;
        BridgePatrol.followPatrol(helper,
                new Vec3(zero.getX() + 1.5D,
                        zero.getY() + DECK + 3.0D, laneZ),
                new Vec3(zero.getX() + 12.5D,
                        zero.getY() + DECK + 1.0D, laneZ),
                new Vec3(zero.getX() + 0.5D,
                        zero.getY() + DECK + 3.0D, laneZ),
                LEGS_WANTED,
                // 合法的最低脚面是岛底与低台（DECK+1）；再低半格就是掉下去。
                zero.getY() + DECK + 0.6D,
                DRIVE_TICKS, "step-island follow");
    }

    /**
     * 同一座 L 岛，**高的那一格顶上多一块关着的下半活板门**——玩家实测点名
     * 的那一种，而且只在**连跳**时才现形。
     *
     * <p>玩家原话："站在活板门上面然后跳上上格方块而碰撞跳到侧边虚空，这个
     * 现象只出现在需要连续跳的情况。"
     *
     * <p>要害是那块板把落脚点从整数高度挪到了 {@code y + 0.1875}。她的一跳
     * 里有两件事同时发生：**升到板面**（比整格高出十六分之三）和**跨过缺口**
     * ——而下一跳紧接着就要从这块板上再跨两格、再上一格。中间那一跳落在非
     * 整高的薄板上，起跳的竖直余量和落点的判定都跟着偏，一撞就被弹向侧边；
     * 而这条通道只有一格宽，弹出去就是虚空。
     *
     * <p>为什么必须**连跳**才复现：单独一跳落稳之后她会停一拍、重新对线；
     * 连着的第二跳没有那一拍，带着上一跳的残余水平速度直接起跳，撞点才落在
     * 会把她推向侧面的那个角上。所以这条沿用同一套奔跑跟随、两个来回，绝不
     * 改成单跳的静态场景。
     */
    static void atARunSheClearsTheLiddedStepWithoutBeingFlungAside(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(0, DECK + 2, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(1, DECK + 2, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(4, DECK + 1, 2), Blocks.GLASS);
        // 关着的下半活板门盖在高的那一格上：走面从 DECK+2 抬到 DECK+2.1875。
        helper.setBlock(new BlockPos(4, DECK + 2, 2), Blocks.OAK_TRAPDOOR);
        helper.setBlock(new BlockPos(5, DECK, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(6, DECK, 2), Blocks.GLASS);
        for (int x = 10; x <= 12; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        double laneZ = zero.getZ() + 2.5D;
        BridgePatrol.followPatrol(helper,
                new Vec3(zero.getX() + 1.5D,
                        zero.getY() + DECK + 3.0D, laneZ),
                new Vec3(zero.getX() + 12.5D,
                        zero.getY() + DECK + 1.0D, laneZ),
                new Vec3(zero.getX() + 0.5D,
                        zero.getY() + DECK + 3.0D, laneZ),
                LEGS_WANTED,
                zero.getY() + DECK + 0.6D,
                DRIVE_TICKS, "lidded step island");
    }

    /**
     * 玩家逐格口述的复现场（羊毛=1、空=0、下半关门板=2；层1=y1、层2=y2）：
     *
     * <pre>
     * y2:  0 0 0 0 0 0 2 1 0 0 0 0 0 0 0 0
     * y1:  1 1 1 1 0 0 0 1 1 0 0 0 1 1 1 1
     *      x1      x5    x8  x10     x13    x16
     * </pre>
     *
     * <p>西台(x1..4) → 空三格、其中 **x7 的上层悬着一块下半关门板** → 岛
     * （x8 两层高、x9 一层）→ 空三格 → 东台(x13..16)。
     *
     * <p>连跳链：跳三格落上门板(x7) → **紧接着**从门板贴脸登上 x8 顶 → 下
     * x9 → 再跳三格。玩家报的那一幕在门板→x8 顶这一跳："站在活板门上面然
     * 后跳上上格方块而碰撞跳到侧边虚空，这个现象只出现在需要连续跳的情况"
     * ——落板带着腾空残余动量，登阶又是贴脸撞跳，撞上方块侧面就被弹向侧
     * 边，而门板只有一格长、两侧全是虚空。回程还有对偶的一半：从 x8 顶下
     * 到只有一格长的薄门板上再起跳。
     *
     * <p>此前两版夹具都复现失败，错都在把场景洗得太干净：一版把门板盖在
     * 头顶（不是贴腰的薄桥）、一版让她平地走上门板（零残余动量）。教训同
     * 栅栏坎那回：**夹具跟真实现场差一格，病灶就根本不出场**。
     */
    static void acrossAFloatingLidSheClimbsWithoutSwervingOff(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 17; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 1; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.WHITE_WOOL);
        }
        helper.setBlock(new BlockPos(8, DECK, 2), Blocks.WHITE_WOOL);
        helper.setBlock(new BlockPos(9, DECK, 2), Blocks.WHITE_WOOL);
        for (int x = 13; x <= 16; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.WHITE_WOOL);
        }
        helper.setBlock(new BlockPos(7, DECK + 1, 2), Blocks.OAK_TRAPDOOR);
        helper.setBlock(new BlockPos(8, DECK + 1, 2), Blocks.WHITE_WOOL);

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        // 主人的巡逻线**平移到走线外侧两格**（z+2）。实机截图里玩家一直站
        // 在航线侧面的山坡上看她跑：跟随目标斜在航线外，路径重铺的空档里
        // 控制器就斜着拽她一下——正是把她从 0.6 宽的门板上拽出侧沿的那只
        // 手。主人在航线正前方的夹具一次都不拽，咬合验证两轮全绿（修复关
        // 掉也绿），说明斜拉是复现的必要条件之一。到站半径三格够她在自己
        // 的航线上贴近计腿，主人本人悬在场外走位（假人不吃物理）。
        double ownerZ = zero.getZ() + 4.5D;
        BridgePatrol.followPatrol(helper,
                new Vec3(zero.getX() + 2.5D,
                        zero.getY() + DECK + 1.0D, zero.getZ() + 2.5D),
                new Vec3(zero.getX() + 14.5D,
                        zero.getY() + DECK + 1.0D, ownerZ),
                new Vec3(zero.getX() + 2.5D,
                        zero.getY() + DECK + 1.0D, ownerZ),
                LEGS_WANTED,
                zero.getY() + DECK + 0.5D,
                DRIVE_TICKS, "floating lid climb");
    }

    /**
     * 同一条跑酷线，**低格头顶立一根滴水石锥**（玩家逐格指定：L 的底层第
     * 二格 y1x2 上方、即 y2x2 放石锥）：
     *
     * <pre>
     * y2:  0 0 0 0 0 0 2 1 D 0 0 0 0 0 0 0   ← D=石锥，立在低格上
     * y1:  1 1 1 1 0 0 0 1 1 0 0 0 1 1 1 1
     *      x1      x5    x8  x10     x13
     * </pre>
     *
     * <p>玩家实测："女仆会从左边下来时直接掉下去"——从 x8 顶下到 x9 的那
     * 一步。石锥的碰撞是格心一根细柱：这一格"图上看着能站、身子进不去"
     * ——细柱两侧的缝塞不下 0.62 的身位，正是格级图与真实碰撞打架的又一
     * 张脸。判据只有一条：不许跌出行走面（这条线因石锥而不可达时，正确的
     * 行为是拒走或绕开，无论如何不是摔下去）。
     */
    static void aDripstoneSpikeOnTheLowStepDoesNotThrowHerOff(
            GameTestHelper helper
    ) {
        spikeRun(helper, Blocks.POINTED_DRIPSTONE, "dripstone low step");
    }

    /**
     * 同题换柱：**末地烛**（玩家点名"末地烛也是一样的情况"）。石锥走的是
     * DAMAGE_CAUTIOUS 这个可走词，末地烛走的是另一条路——细柱不满格于是
     * isPathfindable 为真、下方有地就晋升 WALKABLE——殊途同归到同一格误
     * 判。两条测试钉同一把尺（slimPillar），谁的路子漏了红谁。
     */
    static void anEndRodOnTheLowStepDoesNotThrowHerOff(
            GameTestHelper helper
    ) {
        spikeRun(helper, Blocks.END_ROD, "end rod low step");
    }

    /** 低格头顶立一根细柱的跑酷线；柱是什么由调用方给。 */
    private static void spikeRun(GameTestHelper helper,
            net.minecraft.world.level.block.Block spike, String tape) {
        for (int x = 0; x <= 17; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 1; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.WHITE_WOOL);
        }
        helper.setBlock(new BlockPos(8, DECK, 2), Blocks.WHITE_WOOL);
        helper.setBlock(new BlockPos(9, DECK, 2), Blocks.WHITE_WOOL);
        for (int x = 13; x <= 16; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.WHITE_WOOL);
        }
        helper.setBlock(new BlockPos(7, DECK + 1, 2), Blocks.OAK_TRAPDOOR);
        helper.setBlock(new BlockPos(8, DECK + 1, 2), Blocks.WHITE_WOOL);
        // 细柱立在低格头顶（石锥默认状态就是尖朝上的石笋；末地烛默认竖放）。
        helper.setBlock(new BlockPos(9, DECK + 1, 2), spike);

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        // 判据是**不摔**，不是走通：柱顶如今是合法立足（perchTop），可
        // x9 柱顶到东台仍隔着三格缺口、落点距离四超上限——这条线照旧不
        // 可达。正确行为是停在可达前沿（栖上柱顶驻足也算），无论如何不
        // 是摔下去。计腿判据会要求一件不可能的事，红得毫无意义。主人放
        // 在走线斜外侧、够不着的东台上，拉扯照旧。
        BridgePatrol.stayOnYourFeet(helper,
                new Vec3(zero.getX() + 2.5D,
                        zero.getY() + DECK + 1.0D, zero.getZ() + 2.5D),
                new Vec3(zero.getX() + 14.5D,
                        zero.getY() + DECK + 1.0D, zero.getZ() + 4.5D),
                zero.getY() + DECK + 0.5D,
                DRIVE_TICKS, tape);
    }

    /**
     * 反面钉成对的**正面钉**：细柱的顶是跑酷线上的**必经中继**（玩家实测
     * 点名："有不完整方块不代表不可以站或过，完全当做过不了是不行的"）。
     *
     * <p>两座孤台隔四格，唯一的路是中间那根柱的顶（石锥顶 0.69、末地烛顶
     * 1.0）——跳上柱顶、再跳下对岸，跑酷图的标准摆法。图上这条边由
     * perchTop 分类 + 真顶地板 + 扫掠仿真终审三件套连成；她走不通，说明
     * 三件套哪一环还在把"窄立足"当"不可站"。
     */
    static void aDripstoneTipIsAFootholdOnHerWay(
            GameTestHelper helper
    ) {
        perchRun(helper, Blocks.POINTED_DRIPSTONE, "dripstone stepping stone");
    }

    /** 同题换柱：末地烛顶（满一格高，跳上去正压着弧顶）。 */
    static void anEndRodTopIsAFootholdOnHerWay(GameTestHelper helper) {
        perchRun(helper, Blocks.END_ROD, "end rod stepping stone");
    }

    /**
     * 用户实机场景：**烛立在站位块顶上，烛"留出"的贴边空间是必经中转**
     * （玩家原话："直接跳末地烛上是跳不上去的，因为跳跃距离不够，高度也
     * 不够，但可以先落到有站位的 y1x2 方块上……女仆认为末地烛跳不上去，
     * 但不知道末地烛所留出的空间是能够跳上去并站稳的"）。
     *
     * <p>高一格的西台够不着，台前伸出一格站位块、块顶立着末地烛：她得跳
     * 进烛格贴着烛站（脚在格底，烛细留得下身位），再迈上西台。一格里两
     * 个真实站位（烛顶、烛旁块顶）——终审的落点验高按**格窗**收（落进
     * 目标格内任何真实面都算），这条边才连得起来。
     */
    static void aRodOnTheLedgeStillLeavesHerAFoothold(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 西台高一格（走面 DECK+2），台前一格站位块（顶=DECK+1）+烛。
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, DECK + 1, 2), Blocks.WHITE_WOOL);
        }
        helper.setBlock(new BlockPos(4, DECK, 2), Blocks.WHITE_WOOL);
        helper.setBlock(new BlockPos(4, DECK + 1, 2), Blocks.END_ROD);
        // 缺口 x5..x6，东台走面 DECK+1。
        for (int x = 7; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.WHITE_WOOL);
        }

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        BridgePatrol.followPatrol(helper,
                new Vec3(zero.getX() + 8.5D,
                        zero.getY() + DECK + 1.0D, zero.getZ() + 2.5D),
                new Vec3(zero.getX() + 2.5D,
                        zero.getY() + DECK + 2.0D, zero.getZ() + 2.5D),
                new Vec3(zero.getX() + 8.5D,
                        zero.getY() + DECK + 1.0D, zero.getZ() + 2.5D),
                2,
                zero.getY() + DECK + 0.5D,
                DRIVE_TICKS, "rod ledge foothold");
    }

    /** 柱顶当中继的跑酷线；柱是什么由调用方给。 */
    private static void perchRun(GameTestHelper helper,
            net.minecraft.world.level.block.Block spike, String tape) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.WHITE_WOOL);
        }
        // 柱的底座与柱：顶面就是两台之间唯一的落脚点。
        helper.setBlock(new BlockPos(5, DECK, 2), Blocks.WHITE_WOOL);
        helper.setBlock(new BlockPos(5, DECK + 1, 2), spike);
        for (int x = 7; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.WHITE_WOOL);
        }

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        BridgePatrol.followPatrol(helper,
                new Vec3(zero.getX() + 2.5D,
                        zero.getY() + DECK + 1.0D, zero.getZ() + 2.5D),
                new Vec3(zero.getX() + 8.5D,
                        zero.getY() + DECK + 1.0D, zero.getZ() + 2.5D),
                new Vec3(zero.getX() + 1.5D,
                        zero.getY() + DECK + 1.0D, zero.getZ() + 2.5D),
                2,
                zero.getY() + DECK + 0.5D,
                DRIVE_TICKS, tape);
    }

    /**
     * 孤柱顶上、主人不可达：**站稳待命，不打转**（玩家实测两报：跟随目标
     * 铺不出路时，上层每 tick 推移动控制、微推被柱面弹回，朝向跟着速度矢
     * 量翻，人在末地烛顶原地旋转）。
     *
     * <p>判据三条：不摔、不离柱、**朝向累计小于三圈**——旋转是逐 tick 的
     * 连续翻转，四百 tick 能滚出几十圈，三圈的余量放得下正常的张望。
     */
    static void onALonePillarWithHimUnreachableSheHoldsStill(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int dy = 0; dy <= 2; dy++) {
            helper.setBlock(new BlockPos(5, DECK + dy, 2), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(5, DECK + 3, 2), Blocks.END_ROD);

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        double topY = zero.getY() + DECK + 4.0D;
        Player owner = helper.makeMockPlayer();
        owner.setPos(zero.getX() + 13.5D, topY, zero.getZ() + 2.5D);
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(zero.getX() + 5.5D, topY, zero.getZ() + 2.5D);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());

        double[] spun = {0.0D, Double.NaN};
        for (int tick = 1; tick <= 400; tick++) {
            helper.runAfterDelay(tick, () -> {
                double yaw = maid.getYRot();
                if (!Double.isNaN(spun[1])) {
                    spun[0] += Math.abs(
                            Mth.degreesDifference((float) spun[1],
                                    (float) yaw));
                }
                spun[1] = yaw;
            });
        }
        helper.runAfterDelay(420, () -> {
            double offPillar = Math.hypot(
                    maid.getX() - (zero.getX() + 5.5D),
                    maid.getZ() - (zero.getZ() + 2.5D));
            boolean fell = maid.getY() < zero.getY() + DECK + 3.5D;
            maid.discard();
            helper.assertFalse(fell,
                    "她从孤柱上掉下去了 y=" + (maid.getY() - zero.getY()));
            helper.assertTrue(offPillar < 1.5D,
                    "她离开了孤柱 " + String.format("%.1f", offPillar)
                            + " 格——不可达时不该乱走");
            helper.assertTrue(spun[0] < 1080.0D,
                    "她在柱顶打转：四百 tick 朝向累计 "
                            + String.format("%.0f", spun[0]) + " 度");
            helper.succeed();
        });
    }

    /** 多连钉并行展开（语义不变，墙钟除以连数），见 {@code BridgePatrol.spread}。 */
    @GameTestGenerator
    public static java.util.Collection<net.minecraft.gametest.framework
            .TestFunction> stepIslandRuns() {
        java.util.List<net.minecraft.gametest.framework.TestFunction> runs =
                new java.util.ArrayList<>();
        PinSpread.spread(runs, "stepisland", 10, 960,
                "atarunshefollowsoverthesteppedislandbothwaystwice",
                StepIslandGameTests::atARunSheFollowsOverTheSteppedIslandBothWaysTwice);
        PinSpread.spread(runs, "stepisland", 10, 960,
                "atarunsheclearstheliddedstepwithoutbeingflungaside",
                StepIslandGameTests::atARunSheClearsTheLiddedStepWithoutBeingFlungAside);
        PinSpread.spread(runs, "stepisland", 10, 960,
                "acrossafloatinglidsheclimbswithoutswervingoff",
                StepIslandGameTests::acrossAFloatingLidSheClimbsWithoutSwervingOff);
        PinSpread.spread(runs, "stepisland", 10, 960,
                "adripstonespikeonthelowstepdoesnotthrowheroff",
                StepIslandGameTests::aDripstoneSpikeOnTheLowStepDoesNotThrowHerOff);
        PinSpread.spread(runs, "stepisland", 10, 960,
                "anendrodonthelowstepdoesnotthrowheroff",
                StepIslandGameTests::anEndRodOnTheLowStepDoesNotThrowHerOff);
        PinSpread.spread(runs, "stepisland", 10, 960,
                "adripstonetipisafootholdonherway",
                StepIslandGameTests::aDripstoneTipIsAFootholdOnHerWay);
        PinSpread.spread(runs, "stepisland", 10, 960,
                "anendrodtopisafootholdonherway",
                StepIslandGameTests::anEndRodTopIsAFootholdOnHerWay);
        PinSpread.spread(runs, "stepisland", 10, 960,
                "arodontheledgestillleavesherafoothold",
                StepIslandGameTests::aRodOnTheLedgeStillLeavesHerAFoothold);
        PinSpread.spread(runs, "stepisland", 3, 660,
                "onalonepillarwithhimunreachablesheholdsstill",
                StepIslandGameTests::onALonePillarWithHimUnreachableSheHoldsStill);
        return runs;
    }
}
