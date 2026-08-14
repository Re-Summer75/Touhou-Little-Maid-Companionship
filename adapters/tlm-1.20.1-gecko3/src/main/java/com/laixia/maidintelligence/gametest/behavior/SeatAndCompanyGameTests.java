package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerLingerPolicy;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.ApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.Errand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.leisure.LingerNearOwnerErrand;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Sitting down, and drifting over to her owner for its own sake.
 *
 * <p>Both errands were built on advertisements that already existed and that
 * nothing had ever asked for — a chair beside her was invisible to a maid left
 * to herself, and an owner advertising company was advertising to nobody.
 *
 * <p>The pair also sit on opposite sides of the claim rule, which is the thing
 * most worth pinning: a chair holds one maid, a person does not.
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class SeatAndCompanyGameTests {
    private static final Map<String, String> PARAMETERS =
            Map.of("speed", "0.5", "close_distance", "3");

    private SeatAndCompanyGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aFreeChairIsWalkedToward(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 6, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        scene.chair(6, 2, 1);

        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.REST_ON_SEAT)
                        == ActionResult.RUNNING,
                "A free chair across the room was not walked toward"
        );
        helper.assertTrue(
                maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "She was not sent anywhere"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aChairWithinReachIsSatOn(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        Entity chair = scene.chair(2, 2, 1);

        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.REST_ON_SEAT)
                        == ActionResult.SUCCEEDED,
                "A chair at her feet was not sat on"
        );
        helper.assertTrue(chair.equals(maid.getVehicle()),
                "She did not end up on the chair");
        helper.succeed();
    }

    /** One chair holds one person, so the second maid must be turned away. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void twoMaidsDoNotShareOneChair(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid first = scene.maid(1, 2, 1);
        EntityMaid second = scene.maid(3, 2, 1);
        scene.chair(2, 2, 1);
        TlmMaidIntentActions actions = scene.actions();
        long gameTime = scene.gameTime();

        ActionResult one = actions.execute(
                first,
                CompanionIntentIds.REST_ON_SEAT,
                PARAMETERS,
                gameTime,
                0
        );
        ActionResult two = actions.execute(
                second,
                CompanionIntentIds.REST_ON_SEAT,
                PARAMETERS,
                gameTime,
                0
        );
        int seated = (one == ActionResult.SUCCEEDED ? 1 : 0)
                + (two == ActionResult.SUCCEEDED ? 1 : 0);
        helper.assertTrue(seated == 1,
                "Exactly one maid should have taken the chair, " + seated
                        + " did (" + one + ", " + two + ")");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void nothingToSitOnFailsCleanly(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);

        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.REST_ON_SEAT)
                        == ActionResult.FAILED,
                "An empty room did not fail cleanly"
        );
        helper.assertFalse(
                maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "She was sent somewhere with nothing to sit on"
        );
        helper.succeed();
    }

    /**
     * Two idle maids standing together must not sit on each other.
     *
     * <p>The seat query answers "would this accept her as a passenger", which
     * a colleague does. Each maid then rode the other, and because a mob's
     * navigation follows the vehicle it is controlling, the two pointed at each
     * other and the server died of a stack overflow rather than misbehaving
     * visibly.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void maidsDoNotSitOnEachOther(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid first = scene.maid(1, 2, 1);
        EntityMaid second = scene.maid(2, 2, 1);

        helper.assertTrue(
                run(scene, first, CompanionIntentIds.REST_ON_SEAT)
                        == ActionResult.FAILED,
                "A maid treated her colleague as somewhere to sit"
        );
        helper.assertFalse(first.isPassenger(),
                "One maid ended up riding the other");
        helper.assertFalse(second.isPassenger(),
                "One maid ended up carrying the other");
        helper.succeed();
    }

    /**
     * The mirror of the chair rule, and the reason keeping company reserves
     * nothing: a person can be kept company by more than one maid.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void severalMaidsMayKeepOneOwnerCompany(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 6, 2)
                .ownerAt(6, 2, 1);
        EntityMaid first = scene.maid(1, 2, 1);
        EntityMaid second = scene.maid(1, 2, 2);
        TlmMaidIntentActions actions = scene.actions();
        long gameTime = scene.gameTime();

        ActionResult one = actions.execute(
                first,
                CompanionIntentIds.KEEP_COMPANY,
                PARAMETERS,
                gameTime,
                0
        );
        ActionResult two = actions.execute(
                second,
                CompanionIntentIds.KEEP_COMPANY,
                PARAMETERS,
                gameTime,
                0
        );
        helper.assertTrue(
                one == ActionResult.RUNNING && two == ActionResult.RUNNING,
                "Two maids could not keep one owner company (" + one + ", "
                        + two + ")"
        );
        helper.succeed();
    }

    /** She goes to her own owner, and has no business with anyone else's. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void companyRequiresAnOwner(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid stray = scene.strayMaid(1, 2, 1);

        helper.assertTrue(
                run(scene, stray, CompanionIntentIds.KEEP_COMPANY)
                        == ActionResult.FAILED,
                "An untamed maid went to keep somebody company"
        );
        helper.succeed();
    }

    /**
     * 主人停下来之后她得动起来，而且不能晃出他那一圈。
     *
     * <p>自由模式没有游走——宿主的 {@code RANDOM_STROLL} 被整个删掉了，因为它是
     * 第二个往 {@code WALK_TARGET} 上写的东西。加回来的这一条走的是意图那条路，
     * 而这里问的正是"它到底有没有接上"：落点圆心在主人身上、半径是策略里的那个数，
     * 两者都在纯 JVM 那侧钉过，唯独"动作注册了没有、Errand 返回的是不是一个真的
     * 能走的目标"只有真实世界答得了。
     *
     * <p>**最要紧的是那个落点不许每 tick 换。**骨架每 tick 都调 {@code find}，拿
     * identity 和她出发时那个比对，不一样就当作"改了主意"——抹掉移动目标重写。
     * 玩家看到的是她走一格、转身、再走一格。所以这里先连问二十次要同一个答案，
     * 再用到站（{@code commit}）把它翻页，确认下一趟会去别处。
     *
     * <p>问的是 Errand 抽出来的落点，不是这一 tick 的返回码：半径十六格，抽中她
     * 脚边那一小块时动作会当场报完成、根本不写移动目标，那读起来像"没接上"，其实
     * 是随机数。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void strollingSendsHerSomewhereNearHerOwner(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 6, 6)
                .ownerAt(3, 2, 3);
        EntityMaid maid = scene.maid(3, 2, 2);
        Errand stroll = LingerNearOwnerErrand.create();

        ApproachTarget first = stroll.find(maid, scene.gameTime());
        helper.assertTrue(first != null, "一次都没抽出落点");
        for (int tick = 1; tick < 20; tick++) {
            ApproachTarget again = stroll.find(maid, scene.gameTime() + tick);
            helper.assertTrue(
                    again == first,
                    "第 " + tick + " tick 就换了落点——骨架会把这读成她改了主意，"
                            + "抹掉移动目标重写，于是她走一格转一次身"
            );
        }
        double away = first.tracker().currentPosition()
                .distanceTo(scene.owner().position());
        helper.assertTrue(
                away <= OwnerLingerPolicy.RADIUS + 1.0D,
                "落点离主人 " + away + " 格；游走应当锚在主人身上，"
                        + "而不是从她自己脚下漫开"
        );

        // 到站之后先站一会儿，再挑下一个地方。两头都要钉：
        //
        // 不站——评估间隔只有两秒，她会一直在走，实机上"很少停下来"；
        // 不走——记忆没翻页，她到了一个地方就再也不动了。
        //
        // 站着期间答的是"没有值得去的地方"（null），不是原地假装走一趟：差事本来
        // 就用返回空表示这个意思，这一轮因此让给别的意图。
        long arrived = scene.gameTime();
        stroll.commit(maid, first, arrived);
        helper.assertTrue(
                stroll.find(maid, arrived) == null,
                "刚到就又出发了——没有停顿的散步是一直在走"
        );
        helper.assertTrue(
                stroll.find(
                        maid,
                        arrived + OwnerLingerPolicy.SHORTEST_REST_TICKS - 1
                ) == null,
                "最短的那段发呆都没站满"
        );
        ApproachTarget next = stroll.find(
                maid, arrived + OwnerLingerPolicy.LONGEST_REST_TICKS
        );
        helper.assertTrue(
                next != null && next != first,
                "站完之后她还认着同一个落点——下一趟哪儿也不会去"
        );

        // 记忆还有第三条出路——认了太久就换，那是为"根本走不到的点"准备的安全网。
        // 它在这个夹具里演不出来：房间只有一小块地板，十六格外的落点解析不到地面，
        // 会退回主人本人，前后两次因此仍是同一个位置，看不出重抽有没有发生。

        // 动作本身也要真的接上：上面走的是 Errand，这一句走的是动作 id 的分发。
        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.LINGER_NEAR_OWNER)
                        != ActionResult.FAILED,
                "动作 id 没有接到任何东西上"
        );
        helper.succeed();
    }

    /** 没有主人就没有"他附近"，这一条该干净地失败而不是把她派到别处。 */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void strollingRequiresAnOwner(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid stray = scene.strayMaid(1, 2, 1);

        helper.assertTrue(
                run(scene, stray, CompanionIntentIds.LINGER_NEAR_OWNER)
                        == ActionResult.FAILED,
                "一只没有主人的女仆在谁的身边散步？"
        );
        helper.succeed();
    }

    /**
     * 打完一仗之后，她还得能干别的。
     *
     * <p>玩家报的是"战斗完有概率什么任务都无法执行"。根因在两件事的交叉处：每一件
     * 差事的资格判据都要求 {@code ATTACK_TARGET} 记忆是空的，而**自由模式里没有任何
     * 东西会清它**——原版那个"目标无效就停手"的行为不在 {@code FreedomBrain} 的保留
     * 清单里。于是一仗只要是被编排器取消而不是自然打完结束的，那条记忆就永远留着，
     * 她此后不跟随、不吃饭、不回家、不落座，站到主人右键她为止。
     *
     * <p>"有概率"就是取决于这一仗怎么结束：自然打完走的是战斗自己的收尾（会清），
     * 被取消走的是编排器这条钩子（当时不清）。
     *
     * <p>收尾只扔**死掉的**那个目标。无条件扔会打断一场还没打完的仗——第一版那样
     * 写，僵尸局从"六只全杀"退到剩一两只、卫道士局从 2/4 存活退到 0/4。
     *
     * <p>所以断言的是**玩家看得见的那件事**——取消之后另一件差事跑得起来，而不是
     * "某个记忆为空"。后者是这一次的实现，前者是这条规则。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void afterAFightSheCanStillDoThings(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 6, 2)
                .ownerAt(6, 2, 1);
        EntityMaid maid = scene.maid(1, 2, 1);
        // 给把剑：赤手空拳时裁决走的是撤退分支，而写下攻击目标的是交战分支。
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX() + 2.0D, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, zombie);

        scene.actions().execute(
                maid,
                CompanionIntentIds.ENGAGE_THREAT,
                PARAMETERS,
                scene.gameTime(),
                0
        );
        helper.assertTrue(
                maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET),
                "夹具没让她真的打起来，这条什么都测不到"
        );

        // 仗打完了——因为它死了。这正是玩家报的那个处境（"战斗完后"），也是
        // 收尾唯一该扔掉目标的时候：活着的目标是这一仗的一部分，在取消时清掉它
        // 会打断本体的挥击路由，两个基准都因此变差过。
        zombie.discard();
        scene.actions().cancel(
                maid, CompanionIntentIds.ENGAGE_THREAT, PARAMETERS
        );
        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.KEEP_COMPANY)
                        != ActionResult.FAILED,
                "仗被取消之后她什么都做不了了——"
                        + "战斗留下的状态没有一样东西会替她清"
        );
        helper.succeed();
    }

    /**
     * 处境按**类别**收回许可，而不是一刀切。
     *
     * <p>这条走的是真实分发器，因此验的是许可矩阵**接上了没有**——纯 JVM 那侧逐格
     * 钉的是矩阵的内容，两者缺一不可。写这条的直接原因是：矩阵接上之后全套 186 条
     * 测试仍然全绿，因为夹具几乎都不填"可见实体记忆"，警戒度一律读成平静，那张表
     * 一次都没被走到。**没有闸的条款等于愿望**，而这句话刚写进规范。
     *
     * <p>两档各取一格：警戒时游走（自娱）该停、陪着（陪伴）该照做；危险时连陪着
     * 也停。一刀切的实现过不了第一对断言。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void dangerWithdrawsPermissionByClass(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 6, 6)
                .ownerAt(6, 2, 1);
        EntityMaid maid = scene.maid(1, 2, 1);
        Zombie zombie = new Zombie(helper.getLevel());
        // 放在房间地板之内：地板外的靶子会往下掉，距离随之变化，这一条就不再
        // 只关于许可矩阵了。
        zombie.setPos(maid.getX() + 4.0D, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, zombie);

        // 警戒：看得见，但它不动，所以谁也到不了。
        seeOnly(maid, zombie);
        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.LINGER_NEAR_OWNER)
                        == ActionResult.FAILED,
                "视野里有东西盯着，她还在散步——自娱应当在警戒时就停"
        );
        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.KEEP_COMPANY)
                        != ActionResult.FAILED,
                "只是远处有个僵尸，连陪着主人都不许了——警戒不该停掉差事"
        );

        // 危险：贴到一步之内。它此刻动没动都不重要，迈一步就到。
        zombie.setPos(maid.getX() + 1.0D, maid.getY(), maid.getZ());
        seeOnly(maid, zombie);
        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.KEEP_COMPANY)
                        == ActionResult.FAILED,
                "东西已经贴上来，她还走开去陪主人"
        );
        helper.succeed();
    }

    /** 让她只看得见这一只。 */
    private static void seeOnly(EntityMaid maid, LivingEntity hostile) {
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, List.of(hostile))
        );
    }

    private static ActionResult run(
            CompanionScene scene,
            EntityMaid maid,
            OrchestrationId action
    ) {
        return scene.actions().execute(
                maid,
                action,
                PARAMETERS,
                scene.gameTime(),
                0
        );
    }
}
