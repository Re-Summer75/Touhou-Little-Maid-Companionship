package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.gametest.support.BenchmarkSwitch;
import com.laixia.maidintelligence.gametest.support.CombatTrace;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.gametest.support.WeaponLedger;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.Arrays;

/**
 * 第二道标尺：一击就能要她半条命的敌人。
 *
 * <p>僵尸那一局的宽容之处在于每击只有三点——她怎么打都死不了，于是那一局量的
 * 其实只是效率。卫道士每击约十三点、移速 0.35，四个就是五十二点砸向一个二十点
 * 血的女仆。同一套走位在这里不再是"打得快一点还是慢一点"，而是活不活得下来。
 *
 * <p>所以这一局问的是**她会不会把身上的东西花在该花的时候**。五个金苹果是六十点
 * 有效生命，比她自己的血多两倍；不吃必死，乱吃会在还打得过的时候把家底花光然后
 * 死在后面。这是整套进食策略唯一一个真正被压到极限的场景。
 *
 * <p>用卫道士而不是掠夺者，是因为掠夺者只会用弩：它的目标列表里根本没有近战，
 * 给它一把斧头它会站着不动。而"只拿斧头的劫掠者"在原版里就是卫道士。这个选择
 * 也让场景问得清楚——它们必须走过来，于是后撤重新有意义，她的整套装备都用得上。
 *
 * <p>判据比僵尸那局松一格：允许挨打，但必须活着，而且三十五秒内清完。不设时限
 * 的话"躲在角落嚼苹果"也算过。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class CombatRaidBenchmarkGameTests {
    /** 四个卫道士、五格起手、三十五秒。 */
    private static final int PACK_SIZE = 4;

    private static final int BUDGET_TICKS = 700;
    private static final double PACK_DISTANCE = 5.0D;

    /** 十五支箭，十个金苹果。 */
    private static final int ARROWS = 15;
    private static final int APPLES = 10;

    /** 原版弓满蓄力的 tick 数——只用来判"她在满蓄力上空等了多久"。 */
    private static final int FULL_DRAW_TICKS = 20;

    /** 竞技场，与僵尸局同一副尺寸——共用世界里铺多大就真的铺多大。 */
    private static final int ARENA_X = 23;
    private static final int ARENA_Z = 15;

    private static final int START_X = 17;
    private static final int START_Z = 7;

    /** 与 CompanionScene 的抬升一致，好把顶砌在她那一层。 */
    private static final int LIFT = 12;

    private CombatRaidBenchmarkGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 1100, required = false, batch = "raid1")
    public static void fourVindicatorsTrial1(GameTestHelper helper) {
        trial(helper, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 1100, required = false, batch = "raid2")
    public static void fourVindicatorsTrial2(GameTestHelper helper) {
        trial(helper, 2);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 1100, required = false, batch = "raid3")
    public static void fourVindicatorsTrial3(GameTestHelper helper) {
        trial(helper, 3);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 1100, required = false, batch = "raid4")
    public static void fourVindicatorsTrial4(GameTestHelper helper) {
        trial(helper, 4);
    }


    /** 一局，独占一个批次——四个带斧的真怪不能和邻座共处。 */
    private static void trial(GameTestHelper helper, int index) {
        if (!BenchmarkSwitch.measuring()) {
            // 平时那一轮不量读数——基准占掉四分之三的墙钟时间。见 BenchmarkSwitch。
            helper.succeed();
            return;
        }
        CompanionScene scene = CompanionScene.room(helper, ARENA_X, ARENA_Z);
        unevenGround(helper);
        roofOver(helper);
        scene.ownerAt(START_X, 2, START_Z);
        EntityMaid maid = scene.maid(START_X, 2, START_Z);
        maid.setTask(new FreedomMaidTask());
        var pack = maid.getAvailableBackpackInv();
        pack.setStackInSlot(0, new ItemStack(Items.DIAMOND_SWORD));
        pack.setStackInSlot(1, new ItemStack(Items.BOW));
        pack.setStackInSlot(2, new ItemStack(Items.ARROW, ARROWS));
        pack.setStackInSlot(3, new ItemStack(Items.GOLDEN_APPLE, APPLES));

        Vindicator[] band = new Vindicator[PACK_SIZE];
        for (int slot = 0; slot < PACK_SIZE; slot++) {
            double angle = Math.toRadians(-45.0D + slot * 30.0D);
            Vindicator vindicator = EntityType.VINDICATOR.create(helper.getLevel());
            if (vindicator == null) {
                throw new IllegalStateException("夹具无法创建卫道士");
            }
            vindicator.setPos(
                    maid.getX() + Math.cos(angle) * PACK_DISTANCE,
                    maid.getY(),
                    maid.getZ() + Math.sin(angle) * PACK_DISTANCE
            );
            // 斧要显式给：不走 finalizeSpawn 的话它手上是空的，而空手的卫道士
            // 每击只有一点多，这一局就白设了。
            vindicator.setItemSlot(
                    EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE)
            );
            vindicator.setCanJoinRaid(false);
            vindicator.setTarget(maid);
            vindicator.setPersistenceRequired();
            vindicator.restrictTo(vindicator.blockPosition(), 12);
            helper.getLevel().addFreshEntity(vindicator);
            band[slot] = vindicator;
        }

        CombatTrace trace = new CombatTrace("raid trial " + index);
        WeaponLedger ledger = new WeaponLedger(band);
        long start = helper.getLevel().getGameTime();
        long[] killedAt = new long[PACK_SIZE];
        Arrays.fill(killedAt, -1L);
        float[] lowestHealth = {maid.getMaxHealth()};
        boolean[] died = {false};
        // 她死在第几 tick。均值里"打了 3 点伤害就死了"和"打了 80 点才倒下"长得
        // 一模一样，而前者说明她一开局就送、后者说明她差一点就赢。
        long[] fellAt = {-1L};
        // 吃下去的东西有没有真的生效。苹果耗尽而伤害吸收从未出现过，和苹果耗尽
        // 但确实扛下了几十点，是两件完全不同的事，而"剩几个苹果"对两者一视同仁。
        float[] peakAbsorption = {0.0F};
        // 弓到底是没被拉，还是拉了没射出去，还是射出去没打中。这三件事在"每局
        // 箭伤 26 点"里长得一模一样，而它们要改的地方完全不同。所以直接数：
        // 起手几次、真的少了几支箭、满蓄力空等了多少 tick。
        // 只在她活着的时候数——尸体掉包会让箭数一次性归零。
        int[] drawsBegun = {0};
        int[] arrowsSpent = {0};
        int[] ticksAtFullDraw = {0};
        boolean[] wasDrawing = {false};
        int[] arrowsLeft = {ARROWS};
        // 剑那边同一个问题：持剑 150 tick 只挥 2.5 下，而钻石剑每 0.63 秒一下。
        // 漏在哪一层要分开数——够不着、还在冷却、还是够得着也不冷却却没挥。
        int[] swordTicks = {0};
        int[] swordInRange = {0};
        int[] swordReadyInRange = {0};
        double[] swordGap = {0.0D};
        int[] swordGapTicks = {0};
        int[] swordNear = {0};

        helper.startSequence()
                .thenExecuteFor(BUDGET_TICKS, () -> {
                    long tick = helper.getLevel().getGameTime() - start;
                    if (maid.isAlive()) {
                        boolean drawing = maid.isUsingItem()
                                && maid.getUseItem()
                                        .getFoodProperties(maid) == null;
                        if (drawing && !wasDrawing[0]) {
                            drawsBegun[0]++;
                        }
                        if (drawing && maid.getTicksUsingItem()
                                >= FULL_DRAW_TICKS) {
                            ticksAtFullDraw[0]++;
                        }
                        wasDrawing[0] = drawing;
                        int quiver = count(maid, Items.ARROW);
                        if (quiver < arrowsLeft[0]) {
                            arrowsSpent[0] += arrowsLeft[0] - quiver;
                        }
                        arrowsLeft[0] = quiver;
                        if (maid.getMainHandItem().is(Items.DIAMOND_SWORD)) {
                            swordTicks[0]++;
                            Vindicator near = nearestOf(maid, band);
                            if (near.isAlive()) {
                                // 她持剑时到底站在多远。被要求站 1.8 却没站住，
                                // 和老老实实站在 4 格外，是两个完全不同的毛病，
                                // 而"够得着只有 12%"对两者一视同仁。
                                swordGap[0] += maid.distanceTo(near);
                                swordGapTicks[0]++;
                                if (maid.distanceTo(near) <= 2.5D) {
                                    swordNear[0]++;
                                }
                            }
                            boolean reachable = near.isAlive()
                                    && maid.distanceToSqr(near)
                                            <= maid.getMeleeAttackRangeSqr(near);
                            if (reachable) {
                                swordInRange[0]++;
                                if (!maid.getBrain().hasMemoryValue(
                                        MemoryModuleType.ATTACK_COOLING_DOWN)) {
                                    swordReadyInRange[0]++;
                                }
                            }
                        }
                    }
                    for (int slot = 0; slot < PACK_SIZE; slot++) {
                        if (killedAt[slot] < 0 && !band[slot].isAlive()) {
                            killedAt[slot] = tick;
                        }
                    }
                    if (fellAt[0] < 0 && !maid.isAlive()) {
                        fellAt[0] = tick;
                    }
                    died[0] |= !maid.isAlive();
                    lowestHealth[0] =
                            Math.min(lowestHealth[0], maid.getHealth());
                    peakAbsorption[0] = Math.max(
                            peakAbsorption[0], maid.getAbsorptionAmount()
                    );
                    ledger.record(maid);
                    trace.sample(tick, maid, nearestOf(maid, band));
                })
                .thenExecute(() -> {
                    if (index == 1) {
                        trace.dump();
                    }
                    report(
                            index, maid, band, killedAt, lowestHealth[0],
                            peakAbsorption[0], trace, ledger, fellAt[0]
                    );
                    System.out.printf(
                            "RAID trial=%d BOW draws=%d fired=%d full=%dt%n",
                            index, drawsBegun[0], arrowsSpent[0],
                            ticksAtFullDraw[0]
                    );
                    System.out.printf(
                            "RAID trial=%d SWORD held=%dt inRange=%dt "
                                    + "readyInRange=%dt gap=%.2f near=%dt%n",
                            index, swordTicks[0], swordInRange[0],
                            swordReadyInRange[0],
                            swordGapTicks[0] == 0 ? -1.0D
                                    : swordGap[0] / swordGapTicks[0],
                            swordNear[0]
                    );

                    helper.assertFalse(
                            died[0],
                            "她死了。" + trace.summary()
                    );
                    int standing = alive(band);
                    helper.assertTrue(
                            standing == 0,
                            "三十五秒过去还剩 " + standing + " 个没解决。"
                                    + trace.summary()
                    );
                })
                .thenSucceed();
    }

    /**
     * 把平地改成有高低的地形。
     *
     * <p>实机表现：**她大多数是败在高低地形上的**，而这条基准一直铺的是一块绝对平的
     * 石板。平地上"退一步"和"绕开"永远成立，高低地形上不成立——台阶要跳、坎要
     * 绕、坑里退不出去，而她的触及是球形，站在低处时对方等于凭空多出半格触及。
     * 一块永远不会出现这些的地板，量到的就永远是另一场仗。
     *
     * <p>四样东西，各自考一件事，位置写死好让每一局都一样：
     * <ul>
     *   <li><b>台阶</b>（一格高的长条）——她能不能走上去、上去之后还退不退得回来；
     *   <li><b>高台</b>（两格高）——一格能上、两格要跳，正好卡在寻路的分界上；
     *   <li><b>沟</b>（挖深一格）——掉进去容易，退出去要爬，是"退无可退"的真实版本；
     *   <li><b>孤柱</b>——挡视线也挡路，逼她真的绕。
     * </ul>
     *
     * <p>不用随机：同一副地形每局都一样，读数才有可比性。地形本身该不该更难，
     * 是另一件事，改的时候整条基准的历史读数一起作废——所以这里写死。
     */
    private static void unevenGround(GameTestHelper helper) {
        // 台阶：横贯她与敌人之间，逼每一次接近都跨一次高度。
        for (int x = 8; x <= 13; x++) {
            for (int z = 2; z <= ARENA_Z - 2; z++) {
                helper.setBlock(new BlockPos(x, LIFT + 1, z), Blocks.STONE);
            }
        }
        // 高台：一格上不去，得跳。
        for (int x = 3; x <= 6; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, LIFT + 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, LIFT + 2, z), Blocks.STONE);
            }
        }
        // 沟：她起手那一侧，退进去就要爬出来。
        for (int x = 18; x <= 21; x++) {
            for (int z = 9; z <= 12; z++) {
                helper.setBlock(new BlockPos(x, LIFT, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, LIFT - 1, z), Blocks.STONE);
            }
        }
        // 两根柱子：挡视线也挡路。
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(15, LIFT + y, 4), Blocks.STONE);
            helper.setBlock(new BlockPos(15, LIFT + y, 11), Blocks.STONE);
        }
    }

    /**
     * 一个顶，没有墙。
     *
     * <p>与僵尸局同样的理由：墙会挡住邻座夹具的视线，而顶不会——邻座的女仆和它
     * 们的目标站在同一层，头顶五格外的石头挡不住任何平视。
     *
     * <p>顶抬到八格：地形最高处已经吃掉两格，再留一次跳跃的余量。
     */
    private static void roofOver(GameTestHelper helper) {
        for (int x = 0; x <= ARENA_X; x++) {
            for (int z = 0; z <= ARENA_Z; z++) {
                helper.setBlock(new BlockPos(x, LIFT + 8, z), Blocks.STONE);
            }
        }
    }

    private static Vindicator nearestOf(EntityMaid maid, Vindicator[] band) {
        Vindicator nearest = band[band.length - 1];
        double best = Double.POSITIVE_INFINITY;
        for (Vindicator vindicator : band) {
            if (!vindicator.isAlive()) {
                continue;
            }
            double distance = maid.distanceTo(vindicator);
            if (distance < best) {
                best = distance;
                nearest = vindicator;
            }
        }
        return nearest;
    }

    private static int alive(Vindicator[] band) {
        int standing = 0;
        for (Vindicator vindicator : band) {
            if (vindicator.isAlive()) {
                standing++;
            }
        }
        return standing;
    }

    /** 这一局到底发生了什么，一屏读完。 */
    private static void report(
            int index,
            EntityMaid maid,
            Vindicator[] band,
            long[] killedAt,
            float lowestHealth,
            float peakAbsorption,
            CombatTrace trace,
            WeaponLedger ledger,
            long fellAt
    ) {
        int killed = 0;
        double dealt = 0.0D;
        StringBuilder fallen = new StringBuilder();
        for (int slot = 0; slot < band.length; slot++) {
            dealt += band[slot].getMaxHealth() - band[slot].getHealth();
            if (killedAt[slot] >= 0) {
                killed++;
                fallen.append(fallen.length() == 0 ? "" : ",")
                        .append(killedAt[slot]);
            }
        }
        // 苹果剩几个是这一行里最要紧的一列。清完场却把五个全喝了，和剩三个清完
        // 场，是两种完全不同的表现，而"她活下来了"对两者一视同仁。
        System.out.printf(
                "RAID trial=%d band=%d alive=%s kills=%d/%d hurt=%.1f low=%.1f "
                        + "dealt=%.1f arrows=%d apples=%d shield=%.1f closest=%.1f "
                        + "farthest=%.1f inReach=%.0f%% diedAt=%d fellAt=[%s]%n",
                index, band.length, maid.isAlive() ? "y" : "NO", killed, band.length,
                trace.damageTaken(), lowestHealth, dealt,
                count(maid, Items.ARROW), count(maid, Items.GOLDEN_APPLE),
                peakAbsorption,
                trace.closestApproach(), trace.farthestReached(),
                100.0D * trace.shareWithinReach(), fellAt, fallen
        );
        System.out.printf("RAID trial=%d %s%n", index, ledger.line());
    }

    private static int count(EntityMaid maid, net.minecraft.world.item.Item item) {
        int found = maid.getMainHandItem().is(item)
                ? maid.getMainHandItem().getCount()
                : 0;
        IItemHandler pack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < pack.getSlots(); slot++) {
            ItemStack stack = pack.getStackInSlot(slot);
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        return found;
    }
}
