package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.JumpLedger;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.JumpStrike;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.MeleeSwing;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ThreatProfile;
import com.laixia.maidintelligence.gametest.support.BenchmarkSwitch;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 跳劈靶场：一个僵尸、五格、一把木剑，只数跳劈成没成。
 *
 * <p>为什么要单独有这么个东西：跳劈前五轮改动全部失败，而当时唯一的验收手段是
 * 肉眼观察。**没有任何一个数说得清她跳了没有、跳成了没有。**大基准里的"每刀
 * 伤害"分母只有一两刀，已经量出来是噪音（见 tactics-log 的"规矩"）；四个卫道士
 * 的局里她常常还没进近战就死了，跳劈根本没机会发生。
 *
 * <p>所以这里把一切干扰都拿掉：
 * <ul>
 *   <li><b>一个僵尸</b>——不是四个。跳劈的第一道门就是"横扫弧里只有一个人"，
 *       多打一个都不会跳，那种场景根本考不了这条技术；
 *   <li><b>五格起手</b>——她要自己走进来，接近、站位、冷却这一整套循环都真实跑；
 *   <li><b>只有一把木剑</b>——没有弓、没有食物、没有第二把武器。换手、射箭、进食
 *       都不会插进来抢她的手；木剑伤害低，一个僵尸要砍五六刀才倒，于是一局能观察
 *       到好几个完整的冷却周期，分母够大；
 *   <li><b>不看输赢</b>——这一局她几乎必赢。产出只有一行读数。
 * </ul>
 *
 * <p>判据是**跳劈率**：她挥出去的刀里有多大比例带了暴击。理想值不是 100%——她
 * 走进来的第一刀、以及横扫能扫到人的时候，本来就不该跳。但一个一对一的慢武器
 * 循环里，绝大多数刀都应该是跳劈，因为冷却期她本来就站在预判圈里、那段时间也
 * 本来就没有输出。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class JumpDojoGameTests {
    /** 房间够她走五格、也够她被击退。 */
    private static final int ARENA = 12;

    /** 场景名，进读数行，好把三组分开看。 */
    private static final String SOLO = "solo";

    private static final String PACK = "pack";

    private static final String AXE = "axe";

    /** 起手间距。 */
    private static final double GAP = 5.0D;

    /** 一局多久。木剑砍僵尸要五六刀，留足几个完整冷却周期。 */
    private static final int BUDGET_TICKS = 600;

    /** {@link CompanionScene} 把地板铺在这一层，顶要盖在它上面。 */
    private static final int FLOOR = 12;

    /** 顶离地几格。留出一次跳跃（约 1.25 格）加她自己的身高。 */
    private static final int HEADROOM = 5;

    private JumpDojoGameTests() {
    }

    /**
     * 一份和生产同源的量测——闭合速度尤其要真的，跳劈的外推整个建立在它上面。
     */
    private static ScannedThreat measured(EntityMaid maid, Mob target) {
        return new ScannedThreat(target, new ThreatSample(
                maid.distanceTo(target),
                ThreatProfile.strikeDamage(target),
                ThreatProfile.reach(target, maid),
                ThreatProfile.attackPeriod(target),
                target.getHealth(),
                false,
                ThreatProfile.closingSpeed(maid, target),
                ThreatRelation.ATTACKING_MAID
        ));
    }

    /**
     * 盖个顶。
     *
     * <p>不是为了挡视线，是为了挡太阳：僵尸在露天会自燃，而一个烧着的僵尸会在
     * 七十 tick 里自己倒下——她一共才挥了一刀半，这一局的分母整个是假的。
     */
    private static void roofOver(GameTestHelper helper) {
        for (int x = 0; x <= ARENA; x++) {
            for (int z = 0; z <= ARENA; z++) {
                helper.setBlock(
                        new BlockPos(x, FLOOR + HEADROOM, z), Blocks.STONE
                );
            }
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojo1")
    public static void oneZombieTrial1(GameTestHelper helper) {
        trial(helper, 1, SOLO, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojo2")
    public static void oneZombieTrial2(GameTestHelper helper) {
        trial(helper, 2, SOLO, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojo3")
    public static void oneZombieTrial3(GameTestHelper helper) {
        trial(helper, 3, SOLO, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojo4")
    public static void oneZombieTrial4(GameTestHelper helper) {
        trial(helper, 4, SOLO, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojo5")
    public static void oneZombieTrial5(GameTestHelper helper) {
        trial(helper, 5, SOLO, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojo6")
    public static void oneZombieTrial6(GameTestHelper helper) {
        trial(helper, 6, SOLO, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojo7")
    public static void oneZombieTrial7(GameTestHelper helper) {
        trial(helper, 7, SOLO, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojo8")
    public static void oneZombieTrial8(GameTestHelper helper) {
        trial(helper, 8, SOLO, 1);
    }

    // 多目标：横扫比暴击值钱，所以这一组的**正确答案是跳劈率接近零**。它考的不是
    // "她能不能跳"，是 crowded 那道闸有没有真的关上——一组只会说"是"的读数，说明
    // 不了任何事。
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojoPack1")
    public static void threeZombiesTrial1(GameTestHelper helper) {
        trial(helper, 1, PACK, 3);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojoPack2")
    public static void threeZombiesTrial2(GameTestHelper helper) {
        trial(helper, 2, PACK, 3);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojoPack3")
    public static void threeZombiesTrial3(GameTestHelper helper) {
        trial(helper, 3, PACK, 3);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojoPack4")
    public static void threeZombiesTrial4(GameTestHelper helper) {
        trial(helper, 4, PACK, 3);
    }

    // 卫道士：一斧十三点，正是"血少了就别把这十一 tick 交出去"那道闸该说话的
    // 地方。它会真的还手——这一组要看的是跳劈率在挨打之后掉不掉下来。
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojoAxe1")
    public static void oneVindicatorTrial1(GameTestHelper helper) {
        trial(helper, 1, AXE, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojoAxe2")
    public static void oneVindicatorTrial2(GameTestHelper helper) {
        trial(helper, 2, AXE, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojoAxe3")
    public static void oneVindicatorTrial3(GameTestHelper helper) {
        trial(helper, 3, AXE, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojoAxe4")
    public static void oneVindicatorTrial4(GameTestHelper helper) {
        trial(helper, 4, AXE, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojoAxe5")
    public static void oneVindicatorTrial5(GameTestHelper helper) {
        trial(helper, 5, AXE, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojoAxe6")
    public static void oneVindicatorTrial6(GameTestHelper helper) {
        trial(helper, 6, AXE, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojoAxe7")
    public static void oneVindicatorTrial7(GameTestHelper helper) {
        trial(helper, 7, AXE, 1);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 900, required = false, batch = "dojoAxe8")
    public static void oneVindicatorTrial8(GameTestHelper helper) {
        trial(helper, 8, AXE, 1);
    }

    private static void trial(
            GameTestHelper helper,
            int index,
            String scenario,
            int foes
    ) {
        if (!BenchmarkSwitch.training()) {
            helper.succeed();
            return;
        }
        CompanionScene scene = CompanionScene.room(helper, ARENA, ARENA);
        // 加顶，否则僵尸被太阳烧死。第一次跑就栽在这儿：它在第 69 tick 倒下，
        // 而她只挥了一刀半——木剑根本砍不出那个速度，分母整个是假的。
        roofOver(helper);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setTask(new FreedomMaidTask());
        // 只有一把木剑。弓、食物、第二把武器都不给——它们都会来抢她的手，而这一
        // 局要看的正是那只手在冷却周期里做了什么。
        maid.getAvailableBackpackInv()
                .setStackInSlot(0, new ItemStack(Items.WOODEN_SWORD));
        JumpLedger.reset(maid);

        // 木桩：站着不动，每 tick 回满血。
        //
        // 两个夹具错误都栽在这儿，都值得记住。第一次用活僵尸，它第 69 tick 就倒
        // 了——**两刀**：木剑只有 4 点，但她的攻击力属性叠在上面再乘暴击，二十血
        // 撑不过两下，整局分母是 2。第二次把血量属性拉到两千，她**干脆不打了**：
        // 交战判据按敌人血量给这仗定价，两千血读作"打不赢"，于是她站在外面不动，
        // 三局各挥一刀。
        //
        // 回血是同时满足两头的写法：她看到的永远是一只满血的普通怪，定价照常，
        // 而演武可以一直进行下去。
        //
        // 卫道士那一组不钉住：它要还手，这一组考的正是挨打之后她还敢不敢跳。
        boolean pinned = !AXE.equals(scenario);
        java.util.List<Mob> band = new java.util.ArrayList<>();
        for (int slot = 0; slot < foes; slot++) {
            Mob foe = AXE.equals(scenario)
                    ? new Vindicator(EntityType.VINDICATOR, helper.getLevel())
                    : new Zombie(helper.getLevel());
            // 围成一小圈，好让 crowded 那道闸真的被考到；单个就正对着她。
            double angle = Math.toRadians(-30.0D + slot * 30.0D);
            foe.setPos(
                    maid.getX() + Math.cos(angle) * GAP,
                    maid.getY(),
                    maid.getZ() + Math.sin(angle) * GAP
            );
            if (foe instanceof Vindicator axeman) {
                axeman.setItemSlot(
                        EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE)
                );
                axeman.setCanJoinRaid(false);
            }
            foe.setPersistenceRequired();
            foe.setNoAi(pinned);
            // 拴在自家场地。测试没有收尾 discard（超时红的路径根本不给收尾
            // 机会），带 AI 的怪战后会**游荡串场**——区块被钉住不卸载，它就
            // 永远活着，走进 16 格内哪只女仆的感知就搅谁的局。弩局实测：她
            // 的靶子在 #6990 与串场进来的 #6646 之间来回切，箭全射给了别人
            // 家的僵尸，本场靶子 200 tick 零伤害。游荡的落点选择尊重这条活
            // 动半径，追击不受影响——它照样扑她，只是不再离家出走。
            foe.restrictTo(foe.blockPosition(), 12);
            foe.setTarget(maid);
            helper.getLevel().addFreshEntity(foe);
            band.add(foe);
        }
        Mob target = band.get(0);

        int[] swings = {0};
        int[] inRing = {0};
        int[] holdingSword = {0};
        int[] airborne = {0};
        // 三层漏斗，缺一层就分不清"没机会跳"和"有机会没跳"：
        //   inRing   她站在预判圈里
        //   inWindow 圈里 + 冷却正好剩一个滞空（判据的前两道门都过了）
        //   leaps    真的跳了
        // inWindow 是零 → 时机或站位不对；inWindow 不是零而 leaps 是零 → 只剩
        // 落刀几何这一道门，而 phase() 会把那一行数字原样交出来。
        int[] inWindow = {0};
        // 冷却到底有多长、圈内那些 tick 上"还差几刀"到底读到多少。窗口恒为零时，
        // 是这两个数说清"窗口够不够得着"——它可能整个落在冷却长度之外。
        int[] recovery = {-1};
        int[] leftLow = {Integer.MAX_VALUE};
        int[] leftHigh = {Integer.MIN_VALUE};
        String[] refused = {"（从未同时满足圈内 + 窗口内）"};
        double[] gapSum = {0.0D};
        int[] gapTicks = {0};
        boolean[] wasCoolingDown = {false};
        long[] felledAt = {-1L};
        long start = helper.getLevel().getGameTime();

        helper.startSequence()
                .thenExecuteFor(BUDGET_TICKS, () -> {
                    // 先回满：她挥完这一刀，下一 tick 看到的还是一只满血僵尸。
                    for (Mob foe : band) {
                        foe.setHealth(foe.getMaxHealth());
                    }
                    if (!maid.isAlive()) {
                        return;
                    }
                    // 挥刀次数靠冷却的上升沿数：ATTACK_COOLING_DOWN 从无到有，
                    // 正好是她刚刚挥出去一刀。比在执行层埋计数器少一处耦合。
                    boolean cooling = maid.getBrain()
                            .hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN);
                    if (cooling && !wasCoolingDown[0]) {
                        swings[0]++;
                    }
                    wasCoolingDown[0] = cooling;
                    if (maid.getMainHandItem().is(Items.WOODEN_SWORD)) {
                        holdingSword[0]++;
                        recovery[0] = MeleeSwing.recoveryTicks(maid);
                    }
                    if (!maid.onGround()) {
                        airborne[0]++;
                    }
                    if (target.isAlive()) {
                        double gap = Math.hypot(
                                target.getX() - maid.getX(),
                                target.getZ() - maid.getZ()
                        );
                        double reach = Math.sqrt(
                                maid.getMeleeAttackRangeSqr(target)
                        );
                        gapSum[0] += gap;
                        gapTicks[0]++;
                        // 她有多少时间站在预判圈里。跳劈率低的时候，这一列说明
                        // 是"没机会跳"还是"有机会没跳"——两者要改的地方完全不同。
                        if (gap > reach && gap <= reach + 1.0D) {
                            inRing[0]++;
                        }
                        // 窗口不再要求"在圈里"——判据的内沿已经删掉（她该起跳的
                        // 那半个冷却里正贴在触及之内）。这里要数的是判据真正的前
                        // 两道门：一跳够得着 + 冷却落在下落段。
                        int left = JumpStrike.ticksUntilSwing(maid);
                        if (cooling && left != Integer.MAX_VALUE) {
                            leftLow[0] = Math.min(leftLow[0], left);
                            leftHigh[0] = Math.max(leftHigh[0], left);
                        }
                        if (gap <= reach + 1.0D
                                && left >= JumpStrike.fallBeginsAt()
                                && left <= JumpStrike.landsAt()) {
                            inWindow[0]++;
                            refused[0] = JumpLedger.phase(
                                    maid, measured(maid, target)
                            );
                        }
                    } else if (felledAt[0] < 0) {
                        felledAt[0] = helper.getLevel().getGameTime() - start;
                    }
                })
                .thenExecute(() -> {
                    int crits = JumpLedger.crits(maid);
                    swings[0] = JumpLedger.swings(maid);
                    int leaps = JumpLedger.leaps(maid);
                    System.out.printf(
                            "DOJO %s trial=%d swings=%d crits=%d leaps=%d "
                                    + "critRate=%.0f%% "
                                    + "sword=%dt ring=%dt window=%dt air=%dt "
                                    + "gap=%.2f alive=%s%n",
                            scenario, index, swings[0], crits, leaps,
                            swings[0] == 0
                                    ? 0.0D : 100.0D * crits / swings[0],
                            holdingSword[0], inRing[0], inWindow[0],
                            airborne[0],
                            gapTicks[0] == 0
                                    ? -1.0D : gapSum[0] / gapTicks[0],
                            maid.isAlive() ? "y" : "NO"
                    );
                    System.out.printf(
                            "  WHY %s trial=%d 冷却=%dt 窗口=[%d,%d] "
                                    + "圈内读到的剩余=[%s,%s] %s%n",
                            scenario, index, recovery[0],
                            JumpStrike.fallBeginsAt(), JumpStrike.landsAt(),
                            leftLow[0] == Integer.MAX_VALUE
                                    ? "-" : String.valueOf(leftLow[0]),
                            leftHigh[0] == Integer.MIN_VALUE
                                    ? "-" : String.valueOf(leftHigh[0]),
                            refused[0]
                    );
                })
                .thenSucceed();
    }
}
