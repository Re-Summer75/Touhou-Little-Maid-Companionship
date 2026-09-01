package com.laixia.maidintelligence.gametest.pathbench;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 掉落物如山时她捡东西，服务器扛不扛得住——实机点名的场景一比一。
 *
 * <p>三臂分账对应线程抽样的三份：**纯掉落物**（没有女仆，量原版物品移动
 * 的 O(N²) 底噪）、**平地拾取**（我们的链：每件换单→探路→规划）、**栅栏
 * 拾取**（放大器：走线被堵、跳边全进弧线扫掠，车道最多展开二十一档弹
 * 道）。臂与臂相减就是各自的真实份额——凭手感吵不出来的账，这里一轮出
 * 数。
 *
 * <p>规矩同基准家族：{@code required=false} 读数是产出；不开
 * {@code COMPANIONSHIP_PATHBENCH} 直接过；进出双清、掉落物与女仆收场全
 * 部销毁。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class PickupStormBenchmarkGameTests {
    private static final int DECK = 3;

    private static final int SPAN = 12;

    /** 倒多少件。二百四十件按 6×40 网格铺开，落地即互相合并成堆——合
     *  并本身就是被测负载的一部分，不做防合并处理。 */
    private static final int ITEMS = 240;

    private static final int DRIVE = 400;

    private static final int WARMUP = 60;

    private PickupStormBenchmarkGameTests() {
    }

    /** 纯掉落物：没有女仆，这一臂就是原版的底噪。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pickupstorm1", required = false, timeoutTicks = 600)
    public static void whatTheDropsAloneCost(GameTestHelper helper) {
        storm(helper, false, false, false, false, "alone ");
    }

    /** 平地拾取：我们的链原价。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pickupstorm2", required = false, timeoutTicks = 600)
    public static void whatPickingOnOpenFloorCosts(GameTestHelper helper) {
        storm(helper, true, false, false, false, "open  ");
    }

    /** 栅栏拾取：放大器加满。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pickupstorm3", required = false, timeoutTicks = 600)
    public static void whatPickingAmongFencesCosts(GameTestHelper helper) {
        storm(helper, true, true, false, false, "fenced");
    }

    /** 全不可达：封死角口的圈、她在圈外、物品全在圈内——实机指认的
     *  最坏形态（卡在栅栏外，目标永远到不了）：每一单都是最贵的
     *  no-route 全额审。量的就是这个状态下 tick 扫不扫得平。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pickupstorm5", required = false, timeoutTicks = 600)
    public static void whatUnreachableDropsCost(GameTestHelper helper) {
        storm(helper, true, true, false, false, "sealed");
    }

    /** 原版对照臂：同一个封死的圈、同一套差事，导航换纯原版链
     *  （TimedVanillaNavigation 只补记账）——给"不可达目标连发"定
     *  个原版基准：tick 与 plan 两列直接对照。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pickupstorm6", required = false, timeoutTicks = 600)
    public static void whatVanillaPaysAtTheSealedPen(GameTestHelper helper) {
        storm(helper, true, true, false, true, "stockp");
    }

    /** P 点拾取：同一个角口高差圈，掉落物换成不会合并的
     *  能量点——实体数从头到尾不缩水，才是刷怪场杀完怪满地 P 点
     *  的真压力。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pickupstorm4", required = false, timeoutTicks = 600)
    public static void whatPickingPowerPointsCosts(GameTestHelper helper) {
        storm(helper, true, true, true, false, "ppoint");
    }

    private static void storm(GameTestHelper helper, boolean withMaid,
            boolean fences, boolean points, boolean stockNav, String arm) {
        if (!BenchmarkSwitch.pathing()) {
            helper.succeed();
            return;
        }
        for (int x = 0; x <= SPAN; x++) {
            for (int z = 0; z <= SPAN; z++) {
                for (int y = DECK - 2; y <= DECK + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        boolean sealed = arm.startsWith("sealed")
                || arm.startsWith("stockp");
        if (fences) {
            // 栅栏围照 FencePen 家族的原景配齐：**角缺口＋高低差**（圈
            // 体整体抬一格，外看一格坎加栅栏翻不进；唯一入口是西南角的
            // 无栏台阶，进圈上一格、出圈下一格）。均匀撒的物品自动分到
            // 圈内外两侧——圈里那批可达但得绕角口，进出搬运加柱旁重规
            // 划（21 档车道弹道）就是实机指认的最坏负载。
            for (int x = 3; x <= 9; x++) {
                for (int z = 3; z <= 9; z++) {
                    helper.setBlock(new BlockPos(x, DECK + 1, z),
                            Blocks.STONE);
                    boolean rim = x == 3 || x == 9 || z == 3 || z == 9;
                    if (rim && (sealed || !(x == 3 && z == 3))) {
                        helper.setBlock(new BlockPos(x, DECK + 2, z),
                                Blocks.OAK_FENCE);
                    }
                }
            }
        }
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        List<Entity> drops = new ArrayList<>(ITEMS);
        for (int i = 0; i < ITEMS; i++) {
            // 封圈臂：全部撒进圈内（4..8），圈外的她一件都够不着。
            double x = zero.getX() + (sealed
                    ? 4.2D + (i % 20) * 0.23D : 1.0D + (i % 20) * 0.55D);
            double z = zero.getZ() + (sealed
                    ? 4.2D + (i / 20) * 0.38D : 1.0D + (i / 20) * 0.9D);
            double y = zero.getY() + DECK + 2.2D;
            Entity drop;
            if (points) {
                drop = new EntityPowerPoint(helper.getLevel(), x, y, z, 1);
            } else {
                ItemEntity item = new ItemEntity(helper.getLevel(),
                        x, y, z, new ItemStack(Items.COBBLESTONE));
                item.setPickUpDelay(0);
                drop = item;
            }
            helper.getLevel().addFreshEntity(drop);
            drops.add(drop);
        }
        EntityMaid maid;
        if (withMaid) {
            maid = new EntityMaid(helper.getLevel());
            // 出生在圈外（圈心是封闭圈的内部）。
            maid.setPos(zero.getX() + 1.5D, zero.getY() + DECK + 1.0D,
                    zero.getZ() + 1.5D);
            maid.setTame(true);
            maid.setPickup(true);
            maid.setTask(TaskManager.findTask(FreedomMaidTask.UID)
                    .orElseThrow());
            if (stockNav) {
                // 对照臂：差事层相同，导航换纯原版（升级守卫是精确类
                // 匹配，不会把它换回自有引擎）。
                maid.setNavigation(new TimedVanillaNavigation(
                        maid, helper.getLevel()));
            }
            maid.setHomeModeEnable(false);
            helper.getLevel().addFreshEntity(maid);
        } else {
            maid = null;
        }
        // 同 crowd：自己量相邻回调的墙钟差，不问服务器的滑动平均。
        List<Double> ticks = new ArrayList<>();
        long[] lastClock = new long[]{0L};
        for (int tick = 1; tick <= DRIVE; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                long clock = System.nanoTime();
                if (at > WARMUP && lastClock[0] > 0L) {
                    ticks.add((clock - lastClock[0]) / 1.0E6D);
                }
                lastClock[0] = clock;
                if (at == WARMUP && maid != null) {
                    PathClock.reset(List.of(maid.getUUID()));
                }
            });
        }
        helper.runAfterDelay(DRIVE + 5, () -> {
            // 掉落物落地互相合并会吞实体（幸存者的 count 变大），数
            // 实体会把"合并"错记成"收走"——无女仆的 alone 臂曾因此
            // "剩 58/240"。按件数汇总才是真剩量。
            int left = 0;
            for (Entity drop : drops) {
                if (drop.isAlive()) {
                    left += drop instanceof ItemEntity item
                            ? item.getItem().getCount() : 1;
                    drop.discard();
                }
            }
            List<Double> hot = new ArrayList<>(ticks);
            java.util.Collections.sort(hot);
            double p50 = hot.isEmpty() ? -1.0D : hot.get(hot.size() / 2);
            double p95 = hot.isEmpty() ? -1.0D
                    : hot.get((int) (hot.size() * 0.95D));
            String cost = "-";
            if (maid != null) {
                var whom = List.of(maid.getUUID());
                cost = String.format("plan %7.2fms/%-4d walk %6.2fms",
                        PathClock.planMillis(whom), PathClock.planCalls(whom),
                        PathClock.walkMillis(whom));
                maid.discard();
            }
            System.out.printf(
                    "BENCH pickup %s tick p50 %6.2fms  p95 %6.2fms"
                            + "  left %3d/%d  pathcost %s%n",
                    arm, p50, p95, left, ITEMS, cost);
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
}
