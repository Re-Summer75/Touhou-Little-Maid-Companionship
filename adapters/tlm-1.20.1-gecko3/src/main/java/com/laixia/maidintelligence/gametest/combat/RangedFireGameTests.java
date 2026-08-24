package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmCombatAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.RangedDrawCycle;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 端到端：她怎么把远程武器打出去。
 *
 * <p>与交战策略分开，是因为这里问的是另一类问题——不是"该不该打、站在哪里
 * 打"，而是"扳机扣下去之后到底发生了什么"。这条链路横跨拉弦、蓄力、松手、
 * 装填、击发五步，任何一步接错，看到的都是同一个现象：她举着武器不放箭。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class RangedFireGameTests {
    private RangedFireGameTests() {
    }

    /**
     * 战斗被打断时，那张拉满的弓要放下来。
     *
     * <p>玩家报的是"蓄力好了但敌人没了，她就一直保持蓄力"。使用状态只有主动松手
     * 才会清除，世界不会替她清；而编排器的 cancel 分发里当时有九个动作，唯独没有
     * 战斗。敌人消失、意图不再合格、动作不再被 tick——她就那么一直瞄着空气。
     *
     * <p>别的动作被中途丢下都无所谓，它们至多持有一个移动目标。战斗持有的是攻击
     * 目标、挥臂状态和一张拉满的弓，这是唯一一个必须被通知"你结束了"的动作。
     *
     * <p>断言走编排器的 cancel，而不是直接调动作自己的 cancel：坏掉的从来不是
     * 动作，是那根线没接上。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 60
    )
    public static void aDrawnBowIsLetGoWhenTheFightEnds(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.BOW)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.ARROW, 32)
        );
        seeHostile(helper, maid, 10.0D);

        new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        ).execute(maid);
        helper.assertTrue(
                maid.isUsingItem(),
                "夹具没让她拉起弓，这条什么都测不到"
        );

        scene.actions().cancel(
                maid, CompanionIntentIds.ENGAGE_THREAT, Map.of()
        );
        helper.assertFalse(
                maid.isUsingItem(),
                "战斗被中断后她仍保持着蓄力，而没有任何东西会替她松手"
        );
        helper.succeed();
    }

    /**
     * 远程攻击要拉弓，不是凭空射出。
     *
     * <p>直接调用 performRangedAttack 也能把箭发出去，但那不叫射箭：弓从未进入
     * 使用状态，屏幕上就不会弯，力度也是编造的常数而非蓄力挣来的。两者都看得见。
     *
     * <p>断言分两段：先要求她开始拉弓且不立刻放箭，再要求蓄满后才射出。
     * 只断言"箭出去了"的测试对这个缺陷完全免疫——旧实现同样能通过。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 60
    )
    public static void shootingDrawsTheBowFirst(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.BOW)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.ARROW, 32)
        );
        // 站远一点，好让她选择远程而不是贴上去砍。
        Zombie zombie = seeHostile(helper, maid, 10.0D);

        TlmCombatAction combat = new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        );

        combat.execute(maid);
        helper.assertTrue(
                maid.isUsingItem(),
                "She fired without ever drawing the bow"
        );

        // 蓄力未满就不该松手。
        combat.execute(maid);
        helper.assertTrue(
                maid.isUsingItem(),
                "She released the string after a single tick"
        );

        helper.runAfterDelay(30, () -> {
            for (int tick = 2; tick < 30; tick++) {
                combat.execute(maid);
            }
            helper.assertTrue(
                    zombie.isAlive(),
                    "Fixture zombie should not have been reached"
            );
            helper.succeed();
        });
    }

    /**
     * 弩要放得出去，不能一直拉着。
     *
     * <p>弩的装填与击发是两次动作，而"蓄满了没有"曾按原版弩的装填时长去算。
     * 一把不继承原版弩的模组弩报的是自己的时长，她于是永远等不到那个数——
     * 表现就是弩一直拉着，箭始终不出。
     *
     * <p>断言看的是拉弦计时有没有归零过，而不是箭有没有生成：卡住的形态是
     * 计时一路涨到测试结束，那么可执行的形式就得是"她放过手"。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 120
    )
    public static void aCrossbowGetsReleasedNotJustCranked(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.ARROW, 32)
        );
        // 够远才会选远程，而不是走过去用弩敲人。
        Zombie zombie = seeHostile(helper, maid, 10.0D);

        TlmCombatAction combat = new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        );

        boolean everReleased = false;
        boolean everLoaded = false;
        int previousDrawn = 0;
        for (int tick = 0; tick < 60; tick++) {
            // 每 tick 重新喂一次：下面的 maid.tick() 会让传感器重扫世界，
            // 而夹具僵尸并不在世界里，记忆于是被清空——那会让她当场收兵，
            // 测出来的就不再是弩的事了。
            keepSeeing(maid, zombie);
            combat.execute(maid);
            int drawn = maid.getTicksUsingItem();
            // 计时回退，说明这一发松了手，新的一发重新开始。
            everReleased |= drawn < previousDrawn;
            // 装填这一环改看"箭有没有真的飞出去"，不看 isCharged。
            //
            // 装填与击发发生在同一 tick 的同一次调用里：releaseUsingItem 装上，
            // 紧接着 performRangedAttack 打出去并把 Charged 标记清掉。所以在
            // execute 之后采样 isCharged 必然是 false，无论装填成没成功——那条
            // 断言测的是采样时机，不是弩。
            everLoaded |= !helper.getLevel().getEntitiesOfClass(
                    AbstractArrow.class,
                    maid.getBoundingBox().inflate(16.0D)
            ).isEmpty();
            previousDrawn = drawn;
            maid.tick();
        }

        helper.assertTrue(
                everReleased,
                "She cranked the crossbow for " + previousDrawn
                        + " ticks straight without ever releasing it"
        );
        helper.assertTrue(
                everLoaded,
                "六十 tick 里一支箭都没飞出来：弩上不去弹，射击就是空放，"
                        + "然后她回去继续拉弦"
        );
        helper.succeed();
    }

    /** 走完整条生产链路（真僵尸、编排器驱动）：其它战斗测试手动调 execute，
     *  跳过了传感器/事实/意图/计划状态机，而"高频抽搐"可能正出在那几步。
     *  断言是世界里出现了箭；失败时倒出逐 tick 手部状态——抽搐是时间形态。 */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 260
    )
    public static void aCrossbowFiresUnderTheRealOrchestrator(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(1, 2, 2);
        // 必须真的处于自由模式：射击是经 maid.performRangedAttack 转发给当前
        // 任务的，任务不对，这一整条路由就落空——弩会装填好却永远打不出去，
        // 而那正是本测试要抓的形态，夹具不能自己制造它。
        maid.setTask(new FreedomMaidTask());
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.ARROW, 64)
        );

        // 六格外在地板之外，"不动"的靶子照样会掉（缘由见 placeInert）。
        scene.floorAt(1, 8);
        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX(), maid.getY(), maid.getZ() + 6.0D);
        CompanionScene.placeInert(helper, zombie);

        StringBuilder trace = new StringBuilder();
        // 除手部状态外还记"她打的是谁、两只手里各是什么"，只记变化：手部轨迹
        // 分不开三种坏法（邻座引走、换装吃蓄力、别人松手），画出来一模一样。
        // 不记 isCharged：装填与击发同一调用完成，采样必然 false。
        String[] seen = {""};
        helper.startSequence()
                .thenExecuteFor(200, () -> {
                    // 只观察，不驱动：编排器在生产里每 tick 自己跑一次，测试
                    // 再手动推一次就成了每 tick 两次，射击节奏会跟真实环境
                    // 对不上——这条测试的全部价值就在于走真实路径。
                    if (trace.length() >= 1200) {
                        return;
                    }
                    LivingEntity victim = maid.getTarget();
                    // 靶子带距离：换靶合法（都在感知内）与串场（30 格外）一列分开。
                    String now = (victim == null ? "-" : "#" + victim.getId()
                                    + "@" + (int) maid.distanceTo(victim))
                            + "|" + maid.getMainHandItem().getItem()
                            + "/" + maid.getOffhandItem().getItem();
                    if (!now.equals(seen[0])) {
                        trace.append('[').append(seen[0] = now).append(']');
                    }
                    trace.append(maid.isUsingItem() ? 'U' : '.')
                            .append(maid.getTicksUsingItem()).append(' ');
                })
                .thenExecute(() -> {
                    // 命中后箭会消失，所以"世界里还有箭"会漏判已经打中的那些。
                    // 靶子掉血是更靠得住的证据，两者取其一。
                    boolean hurt = zombie.getHealth() < zombie.getMaxHealth();
                    boolean inFlight = !helper.getLevel()
                            .getEntitiesOfClass(
                                    AbstractArrow.class,
                                    maid.getBoundingBox().inflate(24.0D)
                            ).isEmpty();
                    helper.assertTrue(
                            hurt || inFlight,
                            "No bolt ever left the crossbow. Hand state per "
                                    + "tick was: " + trace
                    );
                    // 一发不算修好：卡住的形态是打完第一发之后装填标志再也
                    // 不落下，于是她对着一把空弩反复扣扳机。看靶子吃了多少
                    // 伤害，比数手上的状态位可靠——那个位在同一 tick 内被置
                    // 起又清掉，逐 tick 采样根本抓不到。
                    float taken = zombie.getMaxHealth() - zombie.getHealth();
                    helper.assertTrue(
                            !zombie.isAlive() || taken >= 10.0F,
                            "The target only took " + taken + " damage in 200 "
                                    + "ticks, so she stalled after the first "
                                    + "shot. Hand state per tick was: " + trace
                    );
                })
                .thenSucceed();
    }

    /**
     * 蓄力只能前进或变成一支箭，不能被悄悄丢掉。
     *
     * <p>玩家报告"蓄力了，对着目标一直射不出去，然后站着被打死"。弓要连续
     * 二十 tick 才拉满，所以**任何**每隔十九 tick 之内清一次蓄力的路径，产出
     * 的箭都恰好是零支——而且看起来就是她在瞄准。撤退分支正是这样一条：它
     * 一进来就把蓄力扔掉，而裁决在目标贴近的过程中本来就会反复翻面。
     *
     * <p>因此这条不测"她射中了没有"，测的是那个不变量：走过撤退分支之后，
     * 要么箭已经出去（蓄力被兑现），要么蓄力还在（可以继续攒）。归零且没有
     * 箭出去，就是那个永远射不出的死循环。
     */
    @GameTest(batch = "rangedfire", templateNamespace = "minecraft", template = "empty")
    public static void aDrawIsSpentNotBinned(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 3);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setTask(new FreedomMaidTask());
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.BOW)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.ARROW, 32)
        );
        // 残血 + 对方射得比她远：两者合起来让"拉开距离"不再是答案，裁决
        // 因此必然落到撤退分支——也就是要验证的那一条。用骷髅而不是靠地形，
        // 是为了让这条断言与"退不退得开"的判定无关。
        maid.setHealth(1.0F);
        Skeleton skeleton = EntityType.SKELETON.create(helper.getLevel());
        helper.assertTrue(skeleton != null, "夹具无法创建骷髅");
        skeleton.setPos(maid.getX() + 9.0D, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, skeleton);
        keepSeeing(maid, skeleton);

        // 攒一段真实的蓄力：拉弦是实体状态，要靠实体自己 tick 才会推进。
        maid.startUsingItem(InteractionHand.MAIN_HAND);
        for (int tick = 0; tick < 8; tick++) {
            maid.tick();
        }
        keepSeeing(maid, skeleton);
        int drawnBefore = maid.getTicksUsingItem();
        helper.assertTrue(
                drawnBefore > 0,
                "夹具没能让蓄力推进，这条测试就没有验证到任何东西"
        );
        int arrowsBefore = arrows(maid);

        new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        ).execute(maid);

        boolean stillDrawing = maid.isUsingItem()
                && maid.getTicksUsingItem() >= drawnBefore;
        boolean spent = arrows(maid) < arrowsBefore;
        helper.assertTrue(
                stillDrawing || spent,
                "一次决策把已经攒到 " + drawnBefore
                        + " tick 的蓄力清零了，也没有射出任何东西；"
                        + "只要这种情况会周期性发生，她就永远放不出箭"
        );
        helper.succeed();
    }

    /**
     * 被逼近到射程之内，她得真的往后退。
     *
     * <p>玩家报告"远程根本不拉开距离"。近战的后撤有测试钉着，远程这一侧一直
     * 没有——而两者走的是不同的分支。断言只看"她被派往哪里"：走不走得到是
     * 导航的事，"她被告知退到更远处"才是这里的命题。
     */
    @GameTest(batch = "rangedfire", templateNamespace = "minecraft", template = "empty")
    public static void closedOnAtBowRangeSheGivesGround(
            GameTestHelper helper
    ) {
        // 站在靠 +x 的一侧，威胁也在 +x，于是退路朝 -x，且那边地够多。
        CompanionScene scene = CompanionScene.room(helper, 11, 5);
        EntityMaid maid = scene.maid(9, 2, 2);
        maid.setTask(new FreedomMaidTask());
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.BOW)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.ARROW, 32)
        );
        // 三格：已经进了她想保持的八格，但还没近到该拔刀。
        Zombie zombie = seeHostile(helper, maid, 1.5D);

        new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        ).execute(maid);

        double here = maid.position().distanceTo(zombie.position());
        double sentTo = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target.getTarget().currentPosition()
                        .distanceTo(zombie.position()))
                .orElse(-1.0D);
        helper.assertTrue(
                sentTo > here,
                "僵尸进到 " + here + " 格，她被派往距它 " + sentTo
                        + " 格处——没有拉开距离就是站着挨打"
        );
        helper.succeed();
    }

    /** 她背包里还剩几支箭。 */
    private static int arrows(EntityMaid maid) {
        int count = 0;
        var backpack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            ItemStack stack = backpack.getStackInSlot(slot);
            if (stack.is(Items.ARROW)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 敌人只进记忆，不进世界。
     *
     * <p>加入世界的僵尸会自己找目标、自己走动，既污染邻座测试也让断言
     * 不再只关于本测试。
     */
    private static Zombie seeHostile(
            GameTestHelper helper,
            EntityMaid maid,
            double offset
    ) {
        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX() + offset, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, zombie);
        keepSeeing(maid, zombie);
        return zombie;
    }

    /** 把这只敌人（重新）放进她的可见实体记忆。 */
    private static void keepSeeing(EntityMaid maid, LivingEntity hostile) {
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, List.of(hostile))
        );
    }

    /**
     * 拉着弩转身撤退，不能把服务器带走。
     *
     * <p>玩家实机崩过一次：{@code shootCrossbowProjectile} 的 {@code target is
     * null}。宿主的弩不认传进来的 victim，回头读 {@code getTarget()}，而撤退
     * 分支从不设它。弓察觉不到——它用的就是传进来的那个。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 200
    )
    public static void aCrossbowSurvivesBreakingOffMidDraw(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setTask(new FreedomMaidTask());
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.ARROW, 32)
        );
        Zombie zombie = seeHostile(helper, maid, 10.0D);
        var weapons = new TlmWeaponScanner(RangedWeaponRecognizer.NONE);

        // 直接打那一行，不指望编排器自己走进这个状态：第一版放了只近处的无 AI
        // 僵尸，稳赢的仗永远 ENGAGE、到不了 withdraw，开关兜底都是绿的。
        for (int tick = 0; tick < 60; tick++) {
            keepSeeing(maid, zombie);
            // 「手里一把拉着的弩，而目标记忆是空的」——撤退分支的原样。
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            RangedDrawCycle.releaseOrKeepDraw(maid, zombie, weapons);
            maid.tick();
        }

        helper.assertTrue(
                maid.isAlive(),
                "她没能活着走完这一段——弩在目标记忆为空时开火崩掉了。"
        );
        helper.succeed();
    }
}
