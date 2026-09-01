package com.laixia.maidintelligence.gametest.pathbench;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation
        .MaidPathNavigation;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel.Anchor;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .AnchorResolver;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel.EdgeVeto;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .StrideWeb;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .VoxelAstar;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .VoxelPath;
import com.laixia.maidintelligence.gametest.support.BenchmarkSwitch;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 铺一条路要花多少时间——**按地形分档量**。
 *
 * <p>自有引擎的每条边都要过扫掠仿真，代价与地形直接挂钩：空地上一条直线
 * 几乎不花钱，柱旁的车道要扫二十一档弹道。平时只知道"整轮跑得动"，可
 * "一次规划多少微秒"从来没有数字——玩家会养几十只，那是要乘出来的。
 *
 * <p>这里只量**规划**（图侧），不掺执行：直接调 {@code VoxelAstar.find}，
 * 不走 {@code moveTo} 的节流与配额，所以读到的是裸开销。规模那一头由
 * {@code PathCrowdBenchmarkGameTests} 用真女仆量。
 *
 * <p>每档地形都**再问一遍本体的尺**（{@code /host} 行）：旁置一个
 * {@code MaidPathNavigation}（TLM 自己的 A*），同一片地、同一对起终点。
 * 导航不装上实体——升级守卫盯的是她手里那把，这把只借体格与属性。
 *
 * <p>基准局的规矩同战斗那两条：{@code required = false}，读数是产出、红
 * 不是；不开 {@code COMPANIONSHIP_BENCHMARK} 就直接过。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class PathPlanBenchmarkGameTests {
    private static final int DECK = 3;

    /** 每档量多少次取统计——头几次要吃 JIT 和缓存冷启动的账。 */
    private static final int TRIALS = 24;

    /** 前几次不计入：类加载、JIT、锚点缓存都在这几次里预热。 */
    private static final int WARMUP = 6;

    private PathPlanBenchmarkGameTests() {
    }

    /** 空地直线八格：地板一片、无障碍，图的下限。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathbenchplan", required = false, timeoutTicks = 400)
    public static void whatAnOpenEightBlockPlanCosts(GameTestHelper helper) {
        if (!BenchmarkSwitch.pathing()) {
            helper.succeed();
            return;
        }
        sweep(helper, 12, 12);
        floor(helper, 0, 10, 0, 6);
        measure(helper, "open8", 1, 3, 9, 3);
        sweep(helper, 12, 12);
        helper.succeed();
    }

    /** 空地直线三十二格：同样无障碍，量的是**距离**这一维怎么涨。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathbenchfar", required = false, timeoutTicks = 400)
    public static void whatAnOpenLongPlanCosts(GameTestHelper helper) {
        if (!BenchmarkSwitch.pathing()) {
            helper.succeed();
            return;
        }
        sweep(helper, 36, 7);
        floor(helper, 0, 34, 0, 6);
        measure(helper, "open32", 1, 3, 33, 3);
        sweep(helper, 36, 7);
        helper.succeed();
    }

    /** 栅栏迷宫：边生成要绕，扫掠次数上去了。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathbenchmaze", required = false, timeoutTicks = 400)
    public static void whatAFencedPlanCosts(GameTestHelper helper) {
        if (!BenchmarkSwitch.pathing()) {
            helper.succeed();
            return;
        }
        sweep(helper, 18, 12);
        floor(helper, 0, 16, 0, 12);
        for (int x = 2; x <= 14; x += 4) {
            for (int z = 1; z <= 9; z++) {
                helper.setBlock(new BlockPos(x, DECK + 1, z),
                        Blocks.OAK_FENCE);
            }
        }
        measure(helper, "maze", 1, 11, 15, 11);
        sweep(helper, 18, 12);
        helper.succeed();
    }

    /** 缺口跑酷：每条跳边都要弹道扫掠，图侧最贵的那一族。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathbenchleap", required = false, timeoutTicks = 400)
    public static void whatALeapingPlanCosts(GameTestHelper helper) {
        if (!BenchmarkSwitch.pathing()) {
            helper.succeed();
            return;
        }
        sweep(helper, 22, 5);
        for (int x = 0; x <= 20; x++) {
            if (x % 4 == 2) {
                continue;
            }
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        measure(helper, "gaprun", 1, 2, 19, 2);
        sweep(helper, 22, 5);
        helper.succeed();
    }

    /** 柱压走道：车道扫描要展开二十一档弹道，单条边的天花板。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathbenchlane", required = false, timeoutTicks = 400)
    public static void whatALanePlanCosts(GameTestHelper helper) {
        if (!BenchmarkSwitch.pathing()) {
            helper.succeed();
            return;
        }
        sweep(helper, 18, 4);
        for (int x = 0; x <= 16; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.STONE);
        }
        for (int x = 4; x <= 12; x += 4) {
            helper.setBlock(new BlockPos(x, DECK + 1, 2), Blocks.OAK_FENCE);
        }
        measure(helper, "lane", 1, 2, 15, 2);
        sweep(helper, 18, 4);
        helper.succeed();
    }

    /** 铺一片地板。 */
    private static void floor(GameTestHelper helper, int x0, int x1,
            int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
    }

    /**
     * 建场前清本场（GameTest 批次之间不还原世界，台账 §5 的原案）。
     *
     * <p>这一族此前不清场，读数被邻批遗产污染过。长廊档
     * （open32/gaprun/lane）的地形本就溢出棋盘步长，清场跟着地形走，但
     * **杀伤半径要收到最小**：z 只清到走廊加一格、往上只清到 DECK+5
     * （跳弧顶不过 +3.7）——清进邻场的每一格都可能改写别人的考题，先例
     * 同 PenRoute 的"只溢单一高度的净地"。
     */
    private static void sweep(GameTestHelper helper, int xMax, int zMax) {
        for (int x = 0; x <= xMax; x++) {
            for (int z = 0; z <= zMax; z++) {
                for (int y = DECK - 2; y <= DECK + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    /**
     * 从 (fromX, fromZ) 铺到 (toX, toZ)，量 {@value #TRIALS} 次。
     *
     * <p>**每次都换一张新图**：{@code StrideWeb} 带锚点与跳边缓存，复用
     * 同一张图量出来的是缓存命中的价钱，不是铺一条路的价钱。实机每次
     * {@code moveTo} 也是新建的，这样才对得上。
     */
    private static void measure(GameTestHelper helper, String what,
            int fromX, int fromZ, int toX, int toZ) {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        BlockPos start = new BlockPos(zero.getX() + fromX,
                zero.getY() + DECK + 1, zero.getZ() + fromZ);
        Vec3 goal = new Vec3(zero.getX() + toX + 0.5D,
                zero.getY() + DECK + 1, zero.getZ() + toZ + 0.5D);
        long[] spent = new long[TRIALS];
        int reached = 0;
        int stops = 0;
        for (int trial = 0; trial < TRIALS; trial++) {
            var web = new StrideWeb(helper.getLevel(), 0.6D, 1.8D,
                    new EdgeVeto());
            Anchor from = AnchorResolver.resolve(helper.getLevel(), start,
                    0.6D, 1.8D);
            if (from == null) {
                helper.fail("基准场地不对：起点解不出锚 " + what);
                return;
            }
            long clock = System.nanoTime();
            VoxelPath path = VoxelAstar.find(web, from, goal, 0.45D);
            spent[trial] = System.nanoTime() - clock;
            if (path != null) {
                stops += path.length();
                if (path.reaches()) {
                    reached++;
                }
            }
        }
        report(what, spent, reached, stops);
        hostMeasure(helper, what, start, goal);
        // succeed 由各测试收：量完还要拿 sweep 收场自清（进场清保护自
        // 己，出场扫还别人干净格子——只清不扫的石板随批次洗牌轮流砸中
        // 杆桥与玻璃行，sw188–191 两族反相关翻红，台账 §5）。
    }

    /**
     * 本体那杆尺：同一片地、同一对起终点，问 TLM 自己的 A*
     * （{@code MaidNodeEvaluator}，预算按她的 FOLLOW_RANGE 走原版口径）。
     *
     * <p>导航旁置、不装上实体，升级守卫（{@code BehaviorExtraBrain} 里那
     * 道）盯的是她手里那把，这把不经过它。探针不入世，物理不跑，所以
     * {@code onGround} 得手工置位——{@code canUpdatePath} 只放行踩地的。
     */
    private static void hostMeasure(GameTestHelper helper, String what,
            BlockPos start, Vec3 goal) {
        EntityMaid probe = new EntityMaid(helper.getLevel());
        probe.setPos(start.getX() + 0.5D, start.getY(),
                start.getZ() + 0.5D);
        probe.setOnGround(true);
        var host = new MaidPathNavigation(probe, helper.getLevel());
        BlockPos aim = BlockPos.containing(goal.x, goal.y, goal.z);
        long[] spent = new long[TRIALS];
        int reached = 0;
        int stops = 0;
        for (int trial = 0; trial < TRIALS; trial++) {
            long clock = System.nanoTime();
            var path = host.createPath(aim, 0);
            spent[trial] = System.nanoTime() - clock;
            if (path != null) {
                stops += path.getNodeCount();
                if (path.canReach()) {
                    reached++;
                }
            }
        }
        report(what + "/host", spent, reached, stops);
    }

    /** 掐头去尾报统计：预热几次不算，剩下的取中位与最坏。
     *  标记与表头全用 ASCII——中文经日志与终端几道编码转手会烂掉
     *  （sw178 实测 {@code �յ� 8 ��}），理由同 crowd 那边。 */
    private static void report(String what, long[] spent, int reached,
            int stops) {
        long[] hot = java.util.Arrays.copyOfRange(spent, WARMUP, spent.length);
        java.util.Arrays.sort(hot);
        long median = hot[hot.length / 2];
        long worst = hot[hot.length - 1];
        long total = 0L;
        for (long one : hot) {
            total += one;
        }
        System.out.printf(
                "BENCH plan %-12s p50 %6.2fms  worst %6.2fms  mean %6.2fms"
                        + "  reach %d/%d  stops %.1f%n",
                what,
                median / 1.0E6D,
                worst / 1.0E6D,
                total / (double) hot.length / 1.0E6D,
                reached, spent.length,
                stops / (double) spent.length);
    }
}
