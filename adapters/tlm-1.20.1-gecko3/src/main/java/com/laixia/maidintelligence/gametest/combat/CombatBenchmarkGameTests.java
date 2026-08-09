package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.gametest.support.CombatTrace;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.gametest.support.WeaponLedger;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.Arrays;

/**
 * 一道标尺，不是一条回归测试。
 *
 * <p>其余战斗测试各钉一个具体缺陷——有没有后撤、有没有换刀、有没有站着不动。
 * 它们全绿只说明那些缺陷不在了，完全不说明她打得好；这一整轮的绿色套件就是在
 * 她隔着墙数敌人、站着挨打的时候保持全绿的。
 *
 * <p>这里问的是合起来够不够聪明，答案以打印出来的轨迹为准，不以红绿为准。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class CombatBenchmarkGameTests {
    /** 基准局：六只僵尸、五格起手、十五秒、零伤害。 */
    private static final int PACK_SIZE = 6;
    private static final int BUDGET_TICKS = 300;
    private static final double PACK_DISTANCE = 5.0D;

    /** 六支箭：只够解决一只多一点，所以纯远程一定输，这是刻意的。 */
    private static final int ARROWS = 6;

    /**
     * 竞技场尺寸，沿用本套件里已经跑通的那一副。
     *
     * <p>第一版铺了 24×24，把邻座夹具的地板改掉了——那堵墙正好落在隔壁那只僵尸
     * 和那只女仆之间，于是隔壁那条"看不见就不该算"的测试以"空地上都扫不到"的
     * 形式变红。GameTest 共用一个世界，铺多大就真的铺多大。
     */
    private static final int ARENA_X = 23;
    private static final int ARENA_Z = 15;

    /** 她起手站的位置：贴着东墙，把整条长边留作退路。 */
    private static final int START_X = 17;
    private static final int START_Z = 7;

    /** 与 CompanionScene 的抬升一致，好把墙和顶砌在她那一层。 */
    private static final int LIFT = 12;

    private CombatBenchmarkGameTests() {
    }


    /**
     * 基准局：六只僵尸、五格起手、十五秒之内清完、一点伤害都不许挨。
     *
     * <p>这不是回归测试，是一道**标尺**。前面每条都只钉住一个具体缺陷（有没有后撤、
     * 有没有换刀、有没有站着不动），全绿只说明那些缺陷不在了，不说明她打得好。这条
     * 问的是合起来够不够聪明。
     *
     * <p>发下去的三样各有各的短处，凑不出一件万能的：
     *
     * <ul>
     *   <li><b>铁斧</b>——每击 9 点，但一秒只挥 0.9 下，且不横扫。一进一出只落一击
     *       的时候它最划算。</li>
     *   <li><b>铁剑</b>——每击 6 点、一秒 1.6 下，带横扫。站在人堆里连续挥的时候它
     *       最划算。</li>
     *   <li><b>弓</b>——只有六支箭，够解决一只多一点。射程内不挨打，但清不了场。</li>
     * </ul>
     *
     * <p>所以纯用哪一样都赢不了：全程弓不够箭，全程斧砍不完，全程剑挨得最多。要过就
     * 得在合适的距离上换合适的那件。武器账本那一行就是为了看她有没有这么做——只看
     * BENCH 那行的话，"三样都用了但配比不对"和"斧头一次没拿"读起来一模一样。
     *
     * <p>零伤害是刻意苛刻的。僵尸移速 0.23、她 0.6，速度差足以让"一次都不该挨"在
     * 理论上成立；挨了就说明某个环节把她送进了别人的触及范围。十五秒同样刻意：不设
     * 时限的话，"退到墙角站一辈子"也算过。
     *
     * <p>默认 {@code required = false}。一条注定红的必测项只会教人忽略整个套件，
     * 而它的价值在每次运行打印出来的那份轨迹，不在通过与否。等她真能过了再改必测。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 700, required = false, batch = "benchmark1")
    public static void sixZombiesTrial1(GameTestHelper helper) {
        trial(helper, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 700, required = false, batch = "benchmark2")
    public static void sixZombiesTrial2(GameTestHelper helper) {
        trial(helper, 2);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 700, required = false, batch = "benchmark3")
    public static void sixZombiesTrial3(GameTestHelper helper) {
        trial(helper, 3);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 700, required = false, batch = "benchmark4")
    public static void sixZombiesTrial4(GameTestHelper helper) {
        trial(helper, 4);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 700, required = false, batch = "benchmark5")
    public static void sixZombiesTrial5(GameTestHelper helper) {
        trial(helper, 5);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 700, required = false, batch = "benchmark6")
    public static void sixZombiesTrial6(GameTestHelper helper) {
        trial(helper, 6);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 700, required = false, batch = "benchmark7")
    public static void sixZombiesTrial7(GameTestHelper helper) {
        trial(helper, 7);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 700, required = false, batch = "benchmark8")
    public static void sixZombiesTrial8(GameTestHelper helper) {
        trial(helper, 8);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 700, required = false, batch = "benchmark9")
    public static void sixZombiesTrial9(GameTestHelper helper) {
        trial(helper, 9);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 700, required = false, batch = "benchmark10")
    public static void sixZombiesTrial10(GameTestHelper helper) {
        trial(helper, 10);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 700, required = false, batch = "benchmark11")
    public static void sixZombiesTrial11(GameTestHelper helper) {
        trial(helper, 11);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 700, required = false, batch = "benchmark12")
    public static void sixZombiesTrial12(GameTestHelper helper) {
        trial(helper, 12);
    }

    /**
     * 一局。
     *
     * <p>跑十二局而不是一局，每局独占一个批次。两件事逼出来的：
     *
     * <p>批次——GameTest 的批次串行、批内并发。六只带 AI 的真僵尸放进并发批次会
     * 走进邻座夹具打别人的女仆（隔壁以"她挨了 28 点"变红），邻座的卫道士也会溜
     * 进来（本局伤害在两次运行间从 0 跳到 21）。独占批次两个方向都断掉。
     *
     * <p>十二局——僵尸寻路带随机，单局读数在 {0 杀 3 伤} 和 {2 杀 25 伤} 之间跳。
     * 拿这种噪声调裁决参数等于对着随机数拧螺丝。要看的是整批的分布，不是某一局。
     * 五局仍然不够：同一份代码跑两遍给过 5.4 和 13.4 两个均值，两者都能"证明"
     * 相反的结论。十二局是能把这一档噪声压住的最小规模。
     */
    private static void trial(GameTestHelper helper, int index) {
        CompanionScene scene = CompanionScene.room(helper, ARENA_X, ARENA_Z);
        roofOver(helper);

        // 主人就站在她脚下。夹具玩家并没有真的进世界，所以僵尸不会去打他；
        // 这么放只为让强制传送的牵引绳完全不构成限制——这一局量的是战斗。
        scene.ownerAt(START_X, 2, START_Z);
        EntityMaid maid = scene.maid(START_X, 2, START_Z);
        maid.setTask(new FreedomMaidTask());
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.IRON_SWORD)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                1, new ItemStack(Items.IRON_AXE)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                2, new ItemStack(Items.BOW)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                3, new ItemStack(Items.ARROW, ARROWS)
        );

        Zombie[] pack = new Zombie[PACK_SIZE];
        for (int slot = 0; slot < PACK_SIZE; slot++) {
            // 五格外的一段扇面，不是一个点上叠六只：叠在一起会互相挤开，
            // 起手距离就不再是五格。
            double angle = Math.toRadians(-60.0D + slot * 24.0D);
            Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
            if (zombie == null) {
                throw new IllegalStateException("夹具无法创建僵尸");
            }
            zombie.setPos(
                    maid.getX() + Math.cos(angle) * PACK_DISTANCE,
                    maid.getY(),
                    maid.getZ() + Math.sin(angle) * PACK_DISTANCE
            );
            zombie.setTarget(maid);
            zombie.setPersistenceRequired();
            // 十二格，跟本套件其它真怪一个数。第一版给了整条竞技场长度，六只
            // 带 AI 的僵尸于是能走进邻座夹具——隔壁两条当场变红（一条挨了 28
            // 点伤害，一条的弩靶子一点血没掉），而它们自己毫无问题。共用世界
            // 里放真怪，拴绳必须比场地小。
            zombie.restrictTo(zombie.blockPosition(), 12);
            helper.getLevel().addFreshEntity(zombie);
            pack[slot] = zombie;
        }

        CombatTrace trace = new CombatTrace("benchmark trial " + index);
        WeaponLedger ledger = new WeaponLedger(pack);
        long start = helper.getLevel().getGameTime();
        long[] killedAt = new long[PACK_SIZE];
        Arrays.fill(killedAt, -1L);
        float[] lowestHealth = {maid.getMaxHealth()};

        helper.startSequence()
                .thenExecuteFor(BUDGET_TICKS, () -> {
                    long tick = helper.getLevel().getGameTime() - start;
                    for (int slot = 0; slot < PACK_SIZE; slot++) {
                        if (killedAt[slot] < 0 && !pack[slot].isAlive()) {
                            killedAt[slot] = tick;
                        }
                    }
                    lowestHealth[0] =
                            Math.min(lowestHealth[0], maid.getHealth());
                    ledger.record(maid);
                    trace.sample(tick, maid, nearestOf(maid, pack));
                })
                .thenExecute(() -> {
                    // 只有第一局打完整轨迹：五份逐 tick 轨迹会把日志淹掉，
                    // 而看因果链一份就够，其余四局只要那一行结果。
                    if (index == 1) {
                        trace.dump();
                    }
                    report(index, maid, pack, killedAt, lowestHealth[0], trace);
                    System.out.println("  " + ledger.line());

                    int standing = alive(pack);
                    helper.assertTrue(
                            trace.engagedTicks() > 0,
                            "整局都没有进入交战意图，这个场景没有测到任何东西。"
                                    + trace.summary()
                    );
                    helper.assertTrue(
                            trace.damageTaken() <= 0.0F,
                            "她挨了 " + trace.damageTaken() + " 点伤害，最低血量 "
                                    + lowestHealth[0] + "。" + trace.summary()
                    );
                    helper.assertTrue(
                            standing == 0,
                            "十五秒过去还剩 " + standing + " 只没解决。"
                                    + ledger.line() + " " + trace.summary()
                    );
                })
                .thenSucceed();
    }

    /**
     * 一个顶，没有墙。
     *
     * <p>顶是为了僵尸不着火：露天的话它们白天会自燃，"十五秒内解决六只"就变成
     * 太阳解决的。
     *
     * <p>墙则是不能砌。第一版四面围了五格高，隔壁那条"看不见就不该算"的测试当场
     * 变红——GameTest 共用一个世界，几副夹具的地板本来就叠在一起，平时无所谓是
     * 因为地板不挡视线，而一堵墙挡。顶不会有这个问题：邻座的女仆站在同一层，头顶
     * 五格外的石头挡不住她们平视的任何东西。
     *
     * <p>把僵尸留在场内因此只能靠 {@code restrictTo}，跟本套件其它真怪一样。
     */
    private static void roofOver(GameTestHelper helper) {
        for (int x = 0; x <= ARENA_X; x++) {
            for (int z = 0; z <= ARENA_Z; z++) {
                helper.setBlock(new BlockPos(x, LIFT + 5, z), Blocks.STONE);
            }
        }
    }

    /** 还站着的那只里离她最近的；全死了就返回最后一只。 */
    private static Zombie nearestOf(EntityMaid maid, Zombie[] pack) {
        Zombie nearest = pack[pack.length - 1];
        double best = Double.POSITIVE_INFINITY;
        for (Zombie zombie : pack) {
            if (!zombie.isAlive()) {
                continue;
            }
            double distance = maid.distanceTo(zombie);
            if (distance < best) {
                best = distance;
                nearest = zombie;
            }
        }
        return nearest;
    }

    private static int alive(Zombie[] pack) {
        int standing = 0;
        for (Zombie zombie : pack) {
            if (zombie.isAlive()) {
                standing++;
            }
        }
        return standing;
    }

    /** 这一局到底发生了什么，一屏读完。 */
    private static void report(
            int index,
            EntityMaid maid,
            Zombie[] pack,
            long[] killedAt,
            float lowestHealth,
            CombatTrace trace
    ) {
        int killed = 0;
        double dealt = 0.0D;
        StringBuilder fallen = new StringBuilder();
        for (int slot = 0; slot < pack.length; slot++) {
            dealt += pack[slot].getMaxHealth() - pack[slot].getHealth();
            if (killedAt[slot] >= 0) {
                killed++;
                fallen.append(fallen.length() == 0 ? "" : ",")
                        .append(killedAt[slot]);
            }
        }
        // 一行一局，好把五局并排读。这一行就是判据本身：清场几只、挨了多少。
        System.out.printf(
                "BENCH trial=%d kills=%d/%d hurt=%.1f low=%.1f dealt=%.1f "
                        + "arrows=%d closest=%.1f farthest=%.1f inReach=%d%% "
                        + "fellAt=[%s]%n",
                index, killed, pack.length, trace.damageTaken(), lowestHealth,
                dealt, arrowsLeft(maid), trace.closestApproach(),
                trace.farthestReached(),
                Math.round(trace.shareWithinReach() * 100), fallen
        );
    }

    private static int arrowsLeft(EntityMaid maid) {
        int arrows = 0;
        IItemHandler backpack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            ItemStack stack = backpack.getStackInSlot(slot);
            if (stack.is(Items.ARROW)) {
                arrows += stack.getCount();
            }
        }
        return arrows;
    }
}
