package com.laixia.maidintelligence.gametest.pathbench;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .PathClock;
import com.laixia.maidintelligence.gametest.support.BenchmarkSwitch;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 几十只女仆同时寻路，服务器扛不扛得住。
 *
 * <p>玩家点过名："玩家可能会养几十只女仆，不能只考虑我们自己的想法，得
 * 做出余量。"规划那一头由 {@code PathPlanBenchmarkGameTests} 单独量，这
 * 里量的是**整条链在一起时的样子**：规划配额争抢、执行侧每 tick 的扫掠
 * 预演、拥挤转向的邻居查询，外加她们互相挤的那份代价。
 *
 * <p>对照组（stock）跑的是**纯裸原版链**：原版 A* ＋ 原版跟随 ＋
 * MoveControl。她们没有主人，附加脑不入场，升级守卫从没醒过（sw180 实
 * 测 stock 的 pathcost 全零才发现这一点）——正好是"没装本模组"的基线。
 * 记账由 {@code TimedVanillaNavigation} 补在同一对边界上。每局独占一个
 * batch——batch 之间是串行的，别的测试不会掺进这份读数。
 *
 * <p>场地只有棋盘步长那么大（十三格见方），二十四只挤在里面是**故意
 * 的**：真实的院子就这么大，避障与互相让行本来就是开销的一部分。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class PathCrowdBenchmarkGameTests {
    private static final int DECK = 3;

    /** 场地边长（棋盘步长内）。 */
    private static final int SPAN = 12;

    /** 跑多少 tick 取样。 */
    private static final int DRIVE = 200;

    /** 前多少 tick 不计：女仆入世、第一次铺路、JIT 都在这几十 tick 里。 */
    private static final int WARMUP = 40;

    private PathCrowdBenchmarkGameTests() {
    }

    /** 一只：基线，这一档的读数是后面几档的分母。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathcrowd1", required = false, timeoutTicks = 400)
    public static void whatOneMaidCosts(GameTestHelper helper) {
        crowd(helper, 1, true);
    }

    /** 八只：一个小院子的量。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathcrowd8", required = false, timeoutTicks = 400)
    public static void whatEightMaidsCost(GameTestHelper helper) {
        crowd(helper, 8, true);
    }

    /** 二十四只：玩家说的"几十只"，也是规划配额开始紧的那一档。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathcrowd24", required = false, timeoutTicks = 400)
    public static void whatTwoDozenMaidsCost(GameTestHelper helper) {
        crowd(helper, 24, true);
    }

    /** 一只走**本体链**：对照组的分母。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathvanilla1", required = false, timeoutTicks = 400)
    public static void whatOneStockMaidCosts(GameTestHelper helper) {
        crowd(helper, 1, false);
    }

    /** 八只走本体链。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathvanilla8", required = false, timeoutTicks = 400)
    public static void whatEightStockMaidsCost(GameTestHelper helper) {
        crowd(helper, 8, false);
    }

    /** 二十四只走本体链：与自有引擎那一档一比，就知道贵了几倍。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathvanilla24", required = false, timeoutTicks = 400)
    public static void whatTwoDozenStockMaidsCost(GameTestHelper helper) {
        crowd(helper, 24, false);
    }

    /**
     * 造 {@code count} 只，让她们朝对角来回走，量服务器 tick。
     *
     * <p>目标每 tick 重下——实机的跟随就是这样（移动 sink 每 tick 把同一
     * 个目标塞回来），节流与配额都在这条路径上，绕开它量出来的不算数。
     */
    private static void crowd(GameTestHelper helper, int count,
            boolean ours) {
        if (!BenchmarkSwitch.pathing()) {
            helper.succeed();
            return;
        }
        // 建场前清本场（批次之间世界不还原，台账 §5）：邻批留在场里的
        // 栅栏会变成幽灵障碍，几十只挤在里面绕它们走，规模读数全被掺。
        for (int x = 0; x <= SPAN; x++) {
            for (int z = 0; z <= SPAN; z++) {
                for (int y = DECK - 2; y <= DECK + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        for (int x = 0; x <= SPAN; x++) {
            for (int z = 0; z <= SPAN; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        List<EntityMaid> maids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            EntityMaid maid = new EntityMaid(helper.getLevel());
            double spot = 1.5D + (i % 6) * 2.0D;
            double lane = 1.5D + (i / 6) * 2.0D;
            maid.setPos(zero.getX() + spot, zero.getY() + DECK + 1.0D,
                    zero.getZ() + lane);
            maid.setTame(true);
            maid.setPickup(false);
            // 自由模式才走自有引擎；不设任务就是裸原版链。对照组换上计
            // 时导航——链不变，只是把 plan/walk 记进同一本账。
            if (ours) {
                maid.setTask(TaskManager.findTask(FreedomMaidTask.UID)
                        .orElseThrow());
            } else {
                maid.setNavigation(new TimedVanillaNavigation(
                        maid, helper.getLevel()));
            }
            maid.setHomeModeEnable(false);
            helper.getLevel().addFreshEntity(maid);
            maids.add(maid);
        }
        // 累计路程，不是净位移：她们每 60 tick 掉头一次，来回走的净位移
        // 会互相抵消，读出来像是"没动"。这个数只用来佐证读到的 tick 是
        // 真在寻路时的开销，不是站着发呆的便宜 tick。
        List<UUID> whom = new ArrayList<>(count);
        for (EntityMaid one : maids) {
            whom.add(one.getUUID());
        }
        double[] walked = new double[count];
        double[] lastX = new double[count];
        double[] lastZ = new double[count];
        for (int i = 0; i < count; i++) {
            lastX[i] = maids.get(i).getX();
            lastZ[i] = maids.get(i).getZ();
        }
        // **自己量每 tick 的真实耗时**，不问服务器的 getAverageTickTime
        // ——那是最近一百 tick 的滑动平均，而基准局之间没有隔离期，上一
        // 局的负载会拖进下一局的窗口里。实测因此散成"中位 11.9ms、均值
        // 74.7ms、最坏 279.8ms"，档与档之间根本不可比。
        //
        // 相邻两次回调的墙钟差就是这一 tick 的真实长度，只属于本局。
        List<Double> ticks = new ArrayList<>();
        long[] lastClock = new long[]{0L};
        // 从热身结束那一刻起，只记寻路自己花的时间。
        boolean[] counting = new boolean[]{false};
        for (int tick = 1; tick <= DRIVE; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                // 来回：前半程去对角，后半程回原点——单向走到墙角就没得
                // 走了，读数会掺进"她们站着"的便宜 tick。
                boolean back = (at / 60) % 2 == 1;
                double aimX = zero.getX() + (back ? 1.5D : SPAN - 0.5D);
                double aimZ = zero.getZ() + (back ? 1.5D : SPAN - 0.5D);
                for (EntityMaid one : maids) {
                    one.getNavigation().moveTo(aimX,
                            zero.getY() + DECK + 1.0D, aimZ, 1.0D);
                }
                for (int i = 0; i < count; i++) {
                    EntityMaid one = maids.get(i);
                    walked[i] += Math.hypot(one.getX() - lastX[i],
                            one.getZ() - lastZ[i]);
                    lastX[i] = one.getX();
                    lastZ[i] = one.getZ();
                }
                long clock = System.nanoTime();
                if (at > WARMUP && lastClock[0] > 0L) {
                    ticks.add((clock - lastClock[0]) / 1.0E6D);
                }
                lastClock[0] = clock;
                if (at == WARMUP && !counting[0]) {
                    counting[0] = true;
                    PathClock.reset(whom);
                }
            });
        }
        helper.runAfterDelay(DRIVE + 5, () -> {
            double moved = 0.0D;
            for (int i = 0; i < count; i++) {
                moved += walked[i];
            }
            report(count, ours, ticks, moved / count,
                    DRIVE - WARMUP, count, whom);
            for (EntityMaid one : maids) {
                one.discard();
            }
            // 收场自清（理由同 PathShape）：这片 13×13 的石板留在棋盘上
            // 就是下一个住户的幻影地板。
            for (int x = 0; x <= SPAN; x++) {
                for (int z = 0; z <= SPAN; z++) {
                    for (int y = DECK - 2; y <= DECK + 5; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
            }
            helper.succeed();
        });
    }

    /** 报读数：tick 时间的中位与最坏，外加"她们真的在动"的佐证。 */
    private static void report(int count, boolean ours, List<Double> ticks,
            double moved, int spanTicks, int heads, List<UUID> whom) {
        // 标记用 ASCII：中文经由日志与终端的几道编码转手会烂掉，读数本
        // 身就此不可读——实测吃过一次亏。
        String who = ours ? "ours " : "stock";
        if (ticks.isEmpty()) {
            System.out.printf("BENCH crowd %s n=%-3d 没采到样%n", who, count);
            return;
        }
        List<Double> hot = new ArrayList<>(ticks);
        java.util.Collections.sort(hot);
        double median = hot.get(hot.size() / 2);
        // 最坏取 95 分位而不是最大：偶尔一次 GC 或区块加载能顶出几百毫
        // 秒，那不是寻路的账。
        double worst = hot.get((int) (hot.size() * 0.95D));
        double total = 0.0D;
        for (double one : hot) {
            total += one;
        }
        System.out.printf(
                "BENCH crowd %s n=%-3d tick p50 %6.2fms  p95 %6.2fms"
                        + "  mean %6.2fms  walked %5.1f%n",
                who, count, median, worst, total / hot.size(), moved);
        // **寻路自己的账**：上面那行量的是整台服务器这一 tick 有多忙，
        // 混着框架开销与别的批次的收尾；这一行只有我们的代码。
        double plan = PathClock.planMillis(whom);
        double walk = PathClock.walkMillis(whom);
        System.out.printf(
                "BENCH pathcost %s n=%-3d plan %7.2fms/%-5d  walk %7.2fms/"
                        + "%-6d  per-maid-tick %5.3fms%n",
                who, count, plan, PathClock.planCalls(whom),
                walk, PathClock.walkCalls(whom),
                (plan + walk) / Math.max(1, spanTicks * heads));
    }
}
