package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.gametest.support.CombatTrace;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
    /**
     * 这一局放几个。
     *
     * <p>暂时按局分成两档：前六局三个、后六局两个。目的是把"她打不过"和"这一局
     * 本来就不可能"分开——四个时十二局全灭，而那个读数无法区分战术不行和强度
     * 超限。一次运行同时问两个强度，比连跑两轮便宜一半。
     */
    private static int packSize(int index) {
        return index <= 6 ? 3 : 2;
    }

    private static final int BUDGET_TICKS = 700;
    private static final double PACK_DISTANCE = 5.0D;

    /** 十支箭，五个金苹果。 */
    private static final int ARROWS = 10;
    private static final int APPLES = 5;

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

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 1100, required = false, batch = "raid5")
    public static void fourVindicatorsTrial5(GameTestHelper helper) {
        trial(helper, 5);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 1100, required = false, batch = "raid6")
    public static void fourVindicatorsTrial6(GameTestHelper helper) {
        trial(helper, 6);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 1100, required = false, batch = "raid7")
    public static void fourVindicatorsTrial7(GameTestHelper helper) {
        trial(helper, 7);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 1100, required = false, batch = "raid8")
    public static void fourVindicatorsTrial8(GameTestHelper helper) {
        trial(helper, 8);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 1100, required = false, batch = "raid9")
    public static void fourVindicatorsTrial9(GameTestHelper helper) {
        trial(helper, 9);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 1100, required = false, batch = "raid10")
    public static void fourVindicatorsTrial10(GameTestHelper helper) {
        trial(helper, 10);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 1100, required = false, batch = "raid11")
    public static void fourVindicatorsTrial11(GameTestHelper helper) {
        trial(helper, 11);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 1100, required = false, batch = "raid12")
    public static void fourVindicatorsTrial12(GameTestHelper helper) {
        trial(helper, 12);
    }

    /** 一局，独占一个批次——四个带斧的真怪不能和邻座共处。 */
    private static void trial(GameTestHelper helper, int index) {
        CompanionScene scene = CompanionScene.room(helper, ARENA_X, ARENA_Z);
        roofOver(helper);
        scene.ownerAt(START_X, 2, START_Z);
        EntityMaid maid = scene.maid(START_X, 2, START_Z);
        maid.setTask(new FreedomMaidTask());
        var pack = maid.getAvailableBackpackInv();
        pack.setStackInSlot(0, new ItemStack(Items.IRON_SWORD));
        pack.setStackInSlot(1, new ItemStack(Items.BOW));
        pack.setStackInSlot(2, new ItemStack(Items.ARROW, ARROWS));
        pack.setStackInSlot(3, new ItemStack(Items.GOLDEN_APPLE, APPLES));

        Vindicator[] band = new Vindicator[packSize(index)];
        for (int slot = 0; slot < packSize(index); slot++) {
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
        long start = helper.getLevel().getGameTime();
        long[] killedAt = new long[packSize(index)];
        Arrays.fill(killedAt, -1L);
        float[] lowestHealth = {maid.getMaxHealth()};
        boolean[] died = {false};
        // 吃下去的东西有没有真的生效。苹果耗尽而伤害吸收从未出现过，和苹果耗尽
        // 但确实扛下了几十点，是两件完全不同的事，而"剩几个苹果"对两者一视同仁。
        float[] peakAbsorption = {0.0F};

        helper.startSequence()
                .thenExecuteFor(BUDGET_TICKS, () -> {
                    long tick = helper.getLevel().getGameTime() - start;
                    for (int slot = 0; slot < packSize(index); slot++) {
                        if (killedAt[slot] < 0 && !band[slot].isAlive()) {
                            killedAt[slot] = tick;
                        }
                    }
                    died[0] |= !maid.isAlive();
                    lowestHealth[0] =
                            Math.min(lowestHealth[0], maid.getHealth());
                    peakAbsorption[0] = Math.max(
                            peakAbsorption[0], maid.getAbsorptionAmount()
                    );
                    trace.sample(tick, maid, nearestOf(maid, band));
                })
                .thenExecute(() -> {
                    if (index == 1) {
                        trace.dump();
                    }
                    report(
                            index, maid, band, killedAt, lowestHealth[0],
                            peakAbsorption[0], trace
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
     * 一个顶，没有墙。
     *
     * <p>与僵尸局同样的理由：墙会挡住邻座夹具的视线，而顶不会——邻座的女仆和它
     * 们的目标站在同一层，头顶五格外的石头挡不住任何平视。
     */
    private static void roofOver(GameTestHelper helper) {
        for (int x = 0; x <= ARENA_X; x++) {
            for (int z = 0; z <= ARENA_Z; z++) {
                helper.setBlock(new BlockPos(x, LIFT + 5, z), Blocks.STONE);
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
            CombatTrace trace
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
                        + "farthest=%.1f fellAt=[%s]%n",
                index, band.length, maid.isAlive() ? "y" : "NO", killed, band.length,
                trace.damageTaken(), lowestHealth, dealt,
                count(maid, Items.ARROW), count(maid, Items.GOLDEN_APPLE),
                peakAbsorption,
                trace.closestApproach(), trace.farthestReached(), fallen
        );
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
