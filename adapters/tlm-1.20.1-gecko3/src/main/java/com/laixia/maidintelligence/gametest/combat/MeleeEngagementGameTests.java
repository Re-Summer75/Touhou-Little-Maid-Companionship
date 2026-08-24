package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.JumpStrike;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.MeleeSwing;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmCombatAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 端到端：她的刀能不能真的落在对方身上。
 *
 * <p>与选武器、走位分开，是因为这里问的是最后一步——挥出去有没有打中。这一步
 * 有两个各自看不出问题的前提：挥击的时机，和挥击时她在哪。任一错位，看到的都
 * 是同一个现象：她面对贴身的敌人做着近战的样子，却半天掉不了对方一滴血。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class MeleeEngagementGameTests {
    /** 原版近战节奏，用作"持剑必须快过它"的上界。 */
    private static final int VANILLA_MELEE_PERIOD = 20;

    private MeleeEngagementGameTests() {
    }

    /**
     * 站在攻击范围里就得打中，而且撤退不该让她打不出去。
     *
     * <p>玩家看到的是"近战只会后退，明明已经在攻击范围内却不攻击"。挥击时机
     * 曾写成 {@code elapsedTicks % 20 == 0}——那不是冷却，是一口时钟：它从动作
     * 开始计时而不是从上一次挥击计时，意图一切换相位就归零；更要命的是任何一
     * 个"不在范围内"的 tick 都会白白烧掉那个窗口，而后撤恰恰在窗口敞开的时候
     * 把她推出范围。两件事凑在一起，她可以在自己的攻击距离里站很久而一次都碰
     * 不到对方。
     *
     * <p>断言看靶子掉了多少血，不看她挥了几次手：挥空的动画和打中长得一样，
     * 而玩家抱怨的是打不中。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 160
    )
    public static void inReachSheActuallyConnects(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setTask(new FreedomMaidTask());
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.IRON_SWORD)
        );

        Zombie victim = new Zombie(helper.getLevel());
        // 贴在她身上：这就是"明明在攻击范围内"。
        victim.setPos(maid.getX() + 1.0D, maid.getY(), maid.getZ());
        victim.setNoAi(true);
        helper.getLevel().addFreshEntity(victim);

        helper.startSequence()
                .thenExecuteFor(120, () -> { })
                .thenExecute(() -> {
                    float taken =
                            victim.getMaxHealth() - victim.getHealth();
                    helper.assertTrue(
                            !victim.isAlive() || taken > 0.0F,
                            "It stood inside her reach for 120 ticks and took "
                                    + "no damage at all"
                    );
                })
                .thenSucceed();
    }

    /**
     * 判定打不过，她要走——而且是**边走边打**，不是二选一。
     *
     * <p>这条判据翻过两次，两次都是因为让一个决定取消了另一个：
     *
     * <ol>
     *   <li>最早是"够得着就先打一下，打不到才退"。她被判定输掉时身边几乎总有
     *       够得着的东西，于是撤退那一支永远轮不到，"打不过"落地成站着对砍到死。</li>
     *   <li>于是改成"退无可退才打"。这一版的问题在卫道士局量得很清楚：她流畅地
     *       后撤、照样被追上（对方移速 0.35 对她 0.6，追击加成抹平了差距），
     *       两百 tick 一刀未还。</li>
     * </ol>
     *
     * <p>现在两件事在同一 tick 都做：给地是无条件的，挥刀是**加上去**的，只在
     * 目标已经在触及之内、且冷却本来就好了的时候——那一下不花掉她任何本来要用的
     * 东西，换回来的击退就是距离，而距离正是撤退想要的东西。
     *
     * <p>所以断言的重点从"她有没有打"改成**"她有没有仍然在走"**：那才是前两版
     * 各自失守的地方。
     */
    @GameTest(batch = "meleeengagement", templateNamespace = "minecraft", template = "empty")
    public static void losingSheLeavesUnlessPinned(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 7, 7);
        // 站在地板中间，身后有地方退。
        EntityMaid maid = scene.maid(4, 2, 3);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        Zombie victim = seeHostile(helper, maid, 1.0D);

        TlmCombatAction combat = new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        );
        // 逼出 WITHDRAW：这一架她打不过。
        maid.setHealth(1.0F);
        combat.execute(maid);

        // 这一条是载重的：身后有地方退，她就必须真的在退。前两版分别在这里和
        // 它的反面失守，而"她打了没有"两边都能自圆其说。
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .isPresent(),
                "判定打不过、身后又有地方，她却没有往任何地方走——"
                        + "撤退被那一刀取消了"
        );
        helper.succeed();
    }

    /**
     * 拿上剑该更快出手，不该更慢。
     *
     * <p>{@code ATTACK_SPEED} 数的是每秒挥几次——空手 4.0，多数剑 1.6，斧 1.0
     * ——所以间隔是"每秒 tick 数除以它"。这里一度是拿它去除一个基准值，等于把
     * 含义反过来读：剑报的数比空手小，是因为每一下更重，而把"数小"当成"更慢"，
     * 结果她每换一件更好的武器，实际输出反而更低。持剑算出五十 tick 一刀，比
     * 原先写死的二十还慢一倍半，这就是"完全没利用上剑的冷却优势"。
     *
     * <p>断言拿原版近战节奏当上界：持剑的恢复必须快过它，因为剑的攻速本来就
     * 比这个基准快。
     */
    @GameTest(batch = "meleeengagement", templateNamespace = "minecraft", template = "empty")
    public static void aBetterWeaponSwingsFasterNotSlower(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        // 装备属性要等实体自己检测到换装才会生效。
        maid.tick();
        maid.tick();

        int armed = MeleeSwing.recoveryTicks(maid);
        helper.assertTrue(
                armed < VANILLA_MELEE_PERIOD,
                "Holding a sword she recovers in " + armed + " ticks, slower "
                        + "than the plain " + VANILLA_MELEE_PERIOD
                        + "-tick cadence it is supposed to beat"
        );
        helper.assertTrue(
                armed > 0,
                "Recovery came out as " + armed + " ticks"
        );
        helper.succeed();
    }

    /**
     * 打的时候站在攻击距离边缘，而不是贴上去。
     *
     * <p>贴脸没有额外收益——同样的一下，从边缘打和从鼻尖打伤害一样——却丢掉了
     * 命中之后唯一要紧的东西：还在射程里。被击退的目标会立刻退出贴身站位，
     * 于是她要花下一秒走路而不是挥刀。站在边缘，击退把目标推到的地方仍然在她
     * 的攻击范围之内。
     *
     * <p>断言不押具体格数，只要两件事：停下的位置真的打得到，并且在射程容得下
     * 的时候不要缩到一格。
     */
    @GameTest(batch = "meleeengagement", templateNamespace = "minecraft", template = "empty")
    public static void sheStopsAtReachNotAtTheSkin(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        Zombie victim = seeHostile(helper, maid, 1.0D);

        int standoff = MeleeSwing.standoff(maid, victim);
        double reachSqr = maid.getMeleeAttackRangeSqr(victim);
        helper.assertTrue(
                standoff * standoff <= reachSqr,
                "She stops " + standoff + " blocks out, past her own reach of "
                        + Math.sqrt(reachSqr) + ", so she never connects"
        );
        if (reachSqr >= 4.0D) {
            helper.assertTrue(
                    standoff >= 2,
                    "Her reach is " + Math.sqrt(reachSqr) + " blocks yet she "
                            + "still closes to " + standoff
                            + ", so one knockback puts the target outside it"
            );
        }
        helper.succeed();
    }

    /**
     * 一只真的在打她的僵尸,她得打回去。
     *
     * <p>其余近战测试都把靶子的 AI 关掉,或者手工喂记忆——玩家看到的"站着不动
     * 挨打"恰恰可能出在被这些夹具跳过的地方:目标会走位、她会受伤、血量会掉进
     * 保命线、惊慌会来抢她的移动目标。这条把这些全打开。
     *
     * <p>失败时把每一步的状态打出来:手上是什么、离多远、有没有被派往某处、
     * 双方还剩多少血。站着不动是一种时间上的形态,只报一个布尔值看不出它卡在
     * 哪一环。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 320
    )
    public static void sheFightsBackAgainstARealAttacker(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 7, 7);
        EntityMaid maid = scene.maid(3, 2, 3);
        maid.setTask(new FreedomMaidTask());
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.IRON_SWORD)
        );

        Zombie attacker = new Zombie(helper.getLevel());
        attacker.setPos(maid.getX() + 3.0D, maid.getY(), maid.getZ());
        helper.getLevel().addFreshEntity(attacker);
        // 拴在自家场地：没有收尾 discard 的活怪战后会游荡串场（缘由与实测
        // 见 JumpDojoGameTests 同款注释）。追击不受影响。
        attacker.restrictTo(attacker.blockPosition(), 12);
        attacker.setTarget(maid);

        StringBuilder trace = new StringBuilder();
        helper.startSequence()
                .thenExecuteFor(280, () -> {
                    if (trace.length() < 900) {
                        trace.append(sample(maid, attacker));
                    }
                })
                .thenExecute(() -> {
                    float dealt =
                            attacker.getMaxHealth() - attacker.getHealth();
                    helper.assertTrue(
                            !attacker.isAlive() || dealt > 0.0F,
                            "She took " + (maid.getMaxHealth()
                                    - maid.getHealth())
                                    + " damage and dealt none back in 280 "
                                    + "ticks. Per-tick state: " + trace
                    );
                })
                .thenSucceed();
    }

    /** 一格状态:手持首字母、距离、有无移动目标、双方血量。 */
    private static String sample(EntityMaid maid, Zombie attacker) {
        String held = maid.getMainHandItem().isEmpty()
                ? "-"
                : maid.getMainHandItem().getItem().toString().substring(0, 1);
        return held
                + String.format("%.1f", maid.distanceTo(attacker))
                + (maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                        ? "W" : ".")
                + (int) maid.getHealth()
                + "/"
                + (int) attacker.getHealth()
                + " ";
    }

    /**
     * 冷却期间要挪出对方的攻击距离，而不是站着挨打。
     *
     * <p>玩家看到的是"直接贴敌人脸上原地硬抗"。判据一度要求"存在一个它够不着
     * 而她够得着的位置",可原版的近战距离由体型算出——女仆和僵尸一样大,两边
     * 的攻击距离完全相同,那个位置对最常见的敌人根本不存在,于是她恒定贴脸。
     *
     * <p>真正的收益来自时间:她退出去,它走回来,那段路她不挨打,而这只要求她
     * 走得比它快。断言看的是"她被派去的地方比现在更远",不是某个具体距离。
     */
    @GameTest(batch = "meleeengagement", templateNamespace = "minecraft", template = "empty")
    public static void duringRecoverySheStepsOutOfReach(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 7, 7);
        EntityMaid maid = scene.maid(4, 2, 3);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        Zombie victim = seeHostile(helper, maid, 1.0D);

        TlmCombatAction combat = new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        );

        // 第一拍：够得着且冷却是好的，她应该挥出去。
        combat.execute(maid);
        helper.assertTrue(
                !MeleeSwing.recovered(maid),
                "She never swung, so there is no recovery to test"
        );

        // 第二拍：冷却中，这一拍不该继续待在它打得到的地方。
        combat.execute(maid);
        double held = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target.getTarget().currentPosition()
                        .distanceTo(victim.position()))
                .orElse(-1.0D);
        helper.assertTrue(
                held > maid.position().distanceTo(victim.position()),
                "Recovering, she was sent to a spot " + held
                        + " blocks from the target while standing "
                        + maid.position().distanceTo(victim.position())
                        + " away — that is standing there taking hits"
        );
        helper.succeed();
    }

    /**
     * 剑要横扫，而不是一次只戳一个。
     *
     * <p>她的攻击走的是怪物那条路——{@code doHurtTarget} 结算攻击力、锋利、
     * 击退和火焰附加，但横扫只存在于 {@code Player.attack} 里。于是女仆挥剑
     * 时，身边贴了几个都只挨一个，横扫之刃那格附魔完全是白镶的。
     *
     * <p>另一条断言同样要紧：横扫不能误伤。玩家愿意承担劈到自家牲口的代价，
     * 女仆这么干就是模组在攻击主人明确不许她碰的东西，所以每一个被波及的目标
     * 都要过一遍 {@code canAttack}。
     */
    @GameTest(batch = "meleeengagement", templateNamespace = "minecraft", template = "empty")
    public static void aSwordCatchesTheOnesBeside(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 7, 7);
        EntityMaid maid = scene.maid(3, 2, 3);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );

        Zombie victim = new Zombie(helper.getLevel());
        victim.setPos(maid.getX() + 1.0D, maid.getY(), maid.getZ());
        victim.setNoAi(true);
        helper.getLevel().addFreshEntity(victim);

        // 紧挨着主目标的第二个敌人，正是横扫该扫到的。
        Zombie beside = new Zombie(helper.getLevel());
        beside.setPos(maid.getX() + 1.6D, maid.getY(), maid.getZ() + 0.8D);
        beside.setNoAi(true);
        helper.getLevel().addFreshEntity(beside);

        // 同样站在弧线里，但不在她被允许攻击的名单上。
        Cow bystander = EntityType.COW.create(helper.getLevel());
        bystander.setPos(maid.getX() + 1.6D, maid.getY(), maid.getZ() - 0.8D);
        bystander.setNoAi(true);
        helper.getLevel().addFreshEntity(bystander);

        // 先让大家落地：刚放进世界的实体还没做过碰撞检测，而横扫和原版一样
        // 要求她双脚着地——夹具不能自己制造出"她在半空"这个前提。
        helper.startSequence()
                .thenExecuteFor(3, () -> { })
                .thenExecute(() -> {
                    maid.getBrain().setMemory(
                            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                            new NearestVisibleLivingEntities(
                                    maid, List.of(victim)
                            )
                    );
                    // 这一刀必须由下面这次决策打出来。敌人现在是真实体，她
                    // 自己的大脑在上面三 tick 里就可能先挥一次——那一刀落在
                    // 她还没落地的时候，横扫按原版规则跳过，于是主目标掉了血
                    // 而旁边那只没有，断言时看起来就像横扫坏了。
                    maid.getBrain().eraseMemory(
                            MemoryModuleType.ATTACK_COOLING_DOWN
                    );
                    new TlmCombatAction(
                            new TlmThreatScanner(),
                            new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
                    ).execute(maid);

                    helper.assertTrue(
                            victim.getHealth() < victim.getMaxHealth(),
                            "The maid did not land the swing this test is "
                                    + "built on"
                    );
                    helper.assertTrue(
                            beside.getHealth() < beside.getMaxHealth(),
                            "The zombie pressed against her target took "
                                    + "nothing, so the sword is still hitting "
                                    + "one at a time: victim="
                                    + victim.getHealth() + " beside="
                                    + beside.getHealth()
                    );
                    helper.assertTrue(
                            bystander.getHealth() == bystander.getMaxHealth(),
                            "The sweep hit something she is not allowed to "
                                    + "attack"
                    );
                })
                .thenSucceed();
    }

    /** 敌人只进记忆，不进世界——加入世界的僵尸会自己动，波及邻座。 */
    private static Zombie seeHostile(
            GameTestHelper helper,
            EntityMaid maid,
            double offset
    ) {
        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX() + offset, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, zombie);
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, List.of(zombie))
        );
        return zombie;
    }

}
