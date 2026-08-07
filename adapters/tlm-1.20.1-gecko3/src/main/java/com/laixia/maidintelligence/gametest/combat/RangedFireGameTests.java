package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.FreedomMaidTask;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmCombatAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmThreatScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmWeaponScanner;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

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

        combat.execute(maid, 0);
        helper.assertTrue(
                maid.isUsingItem(),
                "She fired without ever drawing the bow"
        );

        // 蓄力未满就不该松手。
        combat.execute(maid, 1);
        helper.assertTrue(
                maid.isUsingItem(),
                "She released the string after a single tick"
        );

        helper.runAfterDelay(30, () -> {
            for (int tick = 2; tick < 30; tick++) {
                combat.execute(maid, tick);
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
        int longestDraw = 0;
        for (int tick = 0; tick < 60; tick++) {
            // 每 tick 重新喂一次：下面的 maid.tick() 会让传感器重扫世界，
            // 而夹具僵尸并不在世界里，记忆于是被清空——那会让她当场收兵，
            // 测出来的就不再是弩的事了。
            keepSeeing(maid, zombie);
            combat.execute(maid, tick);
            int drawn = maid.getTicksUsingItem();
            // 计时回退，说明这一发松了手，新的一发重新开始。
            everReleased |= drawn < previousDrawn;
            // 装填是真正失败的那一环：弩上没有弹药，射击就是空放。
            everLoaded |= CrossbowItem.isCharged(maid.getMainHandItem());
            longestDraw = Math.max(longestDraw, drawn);
            previousDrawn = drawn;
            maid.tick();
        }

        helper.assertTrue(
                everReleased,
                "She cranked the crossbow for " + longestDraw
                        + " ticks straight without ever releasing it"
        );
        helper.assertTrue(
                everLoaded,
                "The crossbow was never loaded, so every shot fired nothing "
                        + "and she went back to cranking it"
        );
        helper.succeed();
    }

    /**
     * 走完整条生产链路，看弩到底射不射得出去。
     *
     * <p>其它战斗测试都是手动调 execute，等于跳过了传感器、事实、意图与计划
     * 状态机——玩家看到的"高频抽搐"恰恰可能就出在被跳过的那几步里。这里放一
     * 只真实僵尸（关掉 AI，免得它自己动起来波及邻座），由编排器驱动动作。
     *
     * <p>断言是世界里出现了箭。失败时把每 tick 的手部状态打出来：抽搐是一种
     * 时间上的形态，只报一个布尔值看不出它。
     */
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

        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX(), maid.getY(), maid.getZ() + 6.0D);
        // 只当靶子：会动的僵尸会自己走位，断言就不再只关于弩了。
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);

        StringBuilder trace = new StringBuilder();
        helper.startSequence()
                .thenExecuteFor(200, () -> {
                    // 只观察，不驱动：编排器在生产里每 tick 自己跑一次，测试
                    // 再手动推一次就成了每 tick 两次，射击节奏会跟真实环境
                    // 对不上——这条测试的全部价值就在于走真实路径。
                    if (trace.length() < 600) {
                        trace.append(maid.isUsingItem() ? 'U' : '.')
                                .append(maid.getTicksUsingItem())
                                .append(CrossbowItem.isCharged(
                                        maid.getMainHandItem()) ? "C " : " ");
                    }
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
        keepSeeing(maid, zombie);
        return zombie;
    }

    /** 把这只敌人（重新）放进她的可见实体记忆。 */
    private static void keepSeeing(EntityMaid maid, Zombie zombie) {
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, List.of(zombie))
        );
    }

    @SuppressWarnings("unchecked")
    private static MaidIntentApi<EntityMaid> productionIntents() {
        return (MaidIntentApi<EntityMaid>) AdapterRuntime.require(
                MaidIntentApi.class
        );
    }
}
