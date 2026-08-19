package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementRiskPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.RiskVerdict;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.CombatReadiness;
import com.laixia.maidintelligence.gametest.support.CombatProbe;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.RetreatSpace;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmCombatAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 端到端：看得见敌人时，自由模式的女仆应当进入交战意图。
 *
 * <p>敌人是真实体，只是被关掉了 AI。以前是创建后不入世界、直接写进她的可见
 * 实体记忆，那样既省事又不会波及邻座；但威胁扫描已经改为自己扫世界，不再读
 * 那份记忆——只存在于她脑子里的僵尸，她根本找不到，于是每个战斗场景都悄悄
 * 变成了"她一个人站在空房间里"。关掉 AI 是为了保住这些夹具原本的前提：从没
 * 进过世界的僵尸不会寻路、不会挥手、也不会晒太阳。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class CombatEngagementGameTests {
    /** 与 TlmCombatAction 保持一致的战斗移动倍率。 */
    private static final float COMBAT_SPEED = 0.6F;

    /** 这条测试里她想退开的格数。 */
    private static final double RETREAT_NEEDED = 3.0D;

    private CombatEngagementGameTests() {
    }

    /**
     * 她必须看得见身体以外的地方。
     *
     * <p>这条断言直指一个真实缺陷：自由模式曾把 searchDimension 覆盖为她自己的
     * 碰撞箱，而 MaidNearestLivingEntitySensor 正是扫描这个盒子来填充可见实体
     * 记忆。于是记忆恒空、威胁压力恒为零、交战意图永不触发——她不是不肯打，
     * 是看不见任何可打的东西。所有喂记忆的测试都会绕过这一环，所以单独钉住。
     */
    @GameTest(batch = "combatengagement", templateNamespace = "minecraft", template = "empty")
    public static void sheCanSeeBeyondHerOwnBody(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);

        AABB perceived = maid.searchDimension();
        AABB body = maid.getBoundingBox();
        helper.assertTrue(
                perceived.getSize() > body.getSize(),
                "Her perception box is no bigger than she is, so the sensor "
                        + "can never find anything to fight"
        );
        helper.assertTrue(
                perceived.getXsize() >= 4.0D,
                "Her perception reaches only " + perceived.getXsize()
                        + " blocks across"
        );
        helper.succeed();
    }

    @GameTest(batch = "combatengagement", templateNamespace = "minecraft", template = "empty")
    public static void aVisibleHostileStartsTheFight(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.IRON_SWORD)
        );
        seeHostile(helper, maid, 3.0D);

        settle(maid, helper.getLevel().getGameTime());

        helper.assertTrue(
                CompanionIntentIds.ENGAGE_THREAT.namespace() != null,
                "Combat action id was not registered"
        );
        helper.assertTrue(
                productionIntents().inspect(maid).activeIntent() != null,
                "She picked no intent at all with a hostile in front of her"
        );
        helper.assertTrue(
                "tlm_companionship:engage_threat".equals(
                        String.valueOf(
                                productionIntents().inspect(maid).activeIntent()
                        )
                ),
                "With a sword and a zombie she chose "
                        + productionIntents().inspect(maid).activeIntent()
        );
        helper.succeed();
    }

    /**
     * 逃跑归我们，不管她手上有没有东西。
     *
     * <p>{@code MaidPanicTask} 在"受伤或附近有敌人"时激活，并在激活瞬间抹掉
     * WALK_TARGET 与 LOOK_TARGET。它曾经恒开：战斗动作每 tick 写移动目标，
     * 惊慌每 tick 删掉它，一个拿着剑面对僵尸的女仆什么都不做。当时的修法是让
     * {@code enablePanic} 按背包里有没有武器来答，把两者钉成互斥。
     *
     * <p>那仍然是两套系统轮流上场。现在自由模式根本不注册惊慌，逃跑完全由
     * 战斗意图决定——它只看敌对压力、不看有没有武器，而空手正是
     * {@code EngagementRiskPolicy} 判 {@code WITHDRAW} 的情形。
     *
     * <p>所以断言从"武器与惊慌互斥"改成"惊慌恒关，且空手时风险判定仍然让她
     * 离开"。后半句才是玩家真正在意的行为，前半句只是当时的实现手段。
     */
    @GameTest(batch = "combatengagement", templateNamespace = "minecraft", template = "empty")
    public static void fleeingIsOursArmedOrNot(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        FreedomMaidTask task = new FreedomMaidTask();

        helper.assertFalse(
                task.enablePanic(maid),
                "本体的惊慌还开着，它会每 tick 抹掉我们写的移动目标"
        );

        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.IRON_SWORD)
        );
        helper.assertFalse(
                task.enablePanic(maid),
                "带上武器之后本体惊慌又开了回来"
        );

        // 空手遇敌：判定必须是"离开"，否则拿掉本体惊慌就等于让她站着挨打。
        maid.getAvailableBackpackInv().setStackInSlot(0, ItemStack.EMPTY);
        Zombie threat = seeHostile(helper, maid, 3.0D);
        helper.assertTrue(
                threat.isAlive(),
                "夹具没有放出敌人，这条断言什么都没验证到"
        );
        helper.assertTrue(
                RiskVerdict.WITHDRAW == EngagementRiskPolicy.instance().assess(
                        CombatProbe.field(maid, CombatProbe.scan(maid)),
                        CombatProbe.capability(maid),
                        CombatReadiness.healthFraction(maid),
                        true
                ),
                "空手面对敌人时判定不是 WITHDRAW；本体惊慌已经拿掉，"
                        + "这意味着没有任何东西会让她离开"
        );
        helper.succeed();
    }

    /**
     * 射不到就该走近，而不是站着举弓。
     *
     * <p>此前"保持八格"与"没有视线就返回"两条各自成立的规则合起来是：站在
     * 八格外对着一堵墙，永远不动——距离恰好等于期望值，移动目标于是被清掉。
     * 断言看的是她有没有被派往某处，而不是箭有没有射出：卡住的表现是不动，
     * 可执行的形式也就必须是"她被告知要去哪"。
     *
     * <p>对手是骷髅，不是僵尸。她守的距离已经从八格提到弓自己的射程上限，
     * 而威胁场只把"三秒内能够到她的"算作一场仗——一只十四格外的僵尸走不到，
     * 于是根本不成其为战斗。能在这个距离上还击的，只有同样打得到这么远的
     * 东西，所以这条要验证的场景必须由它来搭。
     */
    @GameTest(batch = "combatengagement", templateNamespace = "minecraft", template = "empty")
    public static void aBlockedShotSendsHerCloser(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.BOW)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.ARROW, 32)
        );
        Skeleton skeleton = EntityType.SKELETON.create(helper.getLevel());
        helper.assertTrue(skeleton != null, "夹具无法创建骷髅");
        skeleton.setPos(maid.getX() + 14.0D, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, skeleton);

        TlmCombatAction combat = new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        );

        // 看得见的时候：十四格外用弓，正落在她该守的距离上，她站定不动。
        combat.execute(maid);
        helper.assertFalse(
                maid.getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .isPresent(),
                "Fixture is not reproducing the standing-still setup: she "
                        + "moved while the shot was still clear"
        );

        // 埋到地平面以下：她还看得见它的位置，但箭已经射不到了。
        skeleton.setPos(
                skeleton.getX(), skeleton.getY() - 6.0D, skeleton.getZ()
        );
        helper.assertFalse(
                maid.hasLineOfSight(skeleton),
                "Fixture failed to block the line of sight"
        );

        combat.execute(maid);
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .isPresent(),
                "With the shot blocked she was sent nowhere, which is the "
                        + "standing-still freeze"
        );
        helper.succeed();
    }

    /**
     * 被贴脸就换刀，端到端地换。
     *
     * <p>纯逻辑测试只能验证"她挑了近战姿态"，换手这一步是另一回事：真正把
     * 剑拿到手上要经过背包扫描、类别匹配和宿主的换装接口，任何一环接错，
     * 她都会摆着近战架势举着弓。
     *
     * <p>这条一度断言的是相反的事——"退得开就继续射"。那个设计让她几乎不
     * 拔刀，因为女仆基础移速 0.7 而僵尸 0.23，"退得开"对几乎所有怪都成立。
     */
    @GameTest(batch = "combatengagement", templateNamespace = "minecraft", template = "empty")
    public static void pressedSheDrawsSteelNotTheBow(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(4, 2, 2);
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.BOW)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                1, new ItemStack(Items.ARROW, 32)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                2, new ItemStack(Items.IRON_SWORD)
        );
        // 贴到一格二：僵尸的实际触及是 sqrt(1.2² + 0.6) ≈ 1.43 格（本体判定
        // 攻击是否命中用的就是这个式子），所以两格外它根本够不到她——那不是
        // "贴脸"，而这条测的正是贴脸。
        //
        // 原来写两格也能过，靠的是一个错值：定价对背包里的近战武器用她的
        // ATTACK_SPEED 属性，而弓不带攻速修饰符，于是那个值是裸基础 4.0，
        // 铁剑真实只有 1.6。近战输出被高估两倍半，剑于是在够不到的距离上
        // 也照样赢。攻速改成问武器自己之后，这条按新的（正确的）数字变红，
        // 夹具也就得摆出它自己声称的那个局面。
        seeHostile(helper, maid, 1.2D);

        new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        ).execute(maid);

        helper.assertTrue(
                maid.getMainHandItem().is(Items.IRON_SWORD),
                "Something was already on top of her and she still held "
                        + maid.getMainHandItem().getItem()
                        + " instead of drawing the sword from her pack"
        );
        helper.succeed();
    }

    /**
     * 身后是断崖就不算退得开。
     *
     * <p>拉扯只在能拉的地方才是战术。背后没有落脚点却照退不误，换来的是摔
     * 下去——比挨那一下糟得多，所以空掉的地面和墙一样算作退无可退。纯逻辑
     * 测试喂不进地形，这一条必须在真实世界里问。
     */
    @GameTest(batch = "combatengagement", templateNamespace = "minecraft", template = "empty")
    public static void aLedgeBehindHerIsNotAnEscape(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        // 站在地板靠 -x 的一侧，往那边退三格就出界了。
        EntityMaid maid = scene.maid(1, 2, 2);
        Vec3 here = maid.position();

        helper.assertTrue(
                RetreatSpace.cornered(maid, here.add(4.0D, 0.0D, 0.0D)),
                "Backing off the edge of the floor was treated as an escape"
        );
        helper.assertFalse(
                RetreatSpace.cornered(maid, here.subtract(4.0D, 0.0D, 0.0D)),
                "Solid floor behind her was treated as a dead end, which "
                        + "gives up kiting wherever she happens to stand"
        );
        helper.succeed();
    }

    /**
     * 退不退得掉，要看腿脚，不是光看空地。
     *
     * <p>这个判断只用在"要不要真的后退"上，不再决定拿什么武器：被贴脸就该
     * 拔刀，跟腿脚快慢无关。但既然要退，就得先确认退得掉——身后是墙或断崖
     * 时原地蹭步，目标照样欺身而上，看起来就是她站着挨打。
     *
     * <p>两个方向都要断言：只测一边的话，把判据写死成"永远不能退"也能通过。
     */
    @GameTest(batch = "combatengagement", templateNamespace = "minecraft", template = "empty")
    public static void kitingNeedsTheLegsToDoIt(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 7, 7);
        // 站在地板中间，往哪边退都还有地，好把地形这个因素排除掉。
        EntityMaid maid = scene.maid(4, 2, 3);
        Zombie chaser = new Zombie(helper.getLevel());
        chaser.setPos(maid.getX() + 2.0D, maid.getY(), maid.getZ());

        // 挪不动的东西，退得掉。
        chaser.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.02D);
        helper.assertTrue(
                RetreatSpace.canGiveGround(
                        maid, chaser, COMBAT_SPEED, RETREAT_NEEDED
                ),
                "She would not give ground even to something that can barely "
                        + "move, so she never backs off at all"
        );

        // 快过她的东西，退是白退——原地蹭步只会让它欺身而上。
        chaser.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(5.0D);
        helper.assertFalse(
                RetreatSpace.canGiveGround(
                        maid, chaser, COMBAT_SPEED, RETREAT_NEEDED
                ),
                "She tried to back away from something far faster than she "
                        + "is, which just hands it free hits"
        );
        helper.succeed();
    }

    /**
     * 拿着剑贴着怪，就得真的挥出去。
     *
     * <p>玩家报告"近战只会撤退，无法攻击"。撤退与交战是风险裁决的两个分支，
     * 而 ATTACK_TARGET 正好把它们分开：交战会写下它，撤退会抹掉它。断言因此
     * 同时看两头——目标掉没掉血，以及她究竟有没有把对方当成目标。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 160
    )
    public static void aSwordMaidActuallySwings(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setTask(new FreedomMaidTask());
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.IRON_SWORD)
        );

        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX() + 1.5D, maid.getY(), maid.getZ());
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);

        helper.startSequence()
                .thenExecuteFor(120, () -> {
                })
                .thenExecute(() -> {
                    boolean engaged = maid.getBrain()
                            .getMemory(MemoryModuleType.ATTACK_TARGET)
                            .isPresent();
                    float taken =
                            zombie.getMaxHealth() - zombie.getHealth();
                    helper.assertTrue(
                            !zombie.isAlive() || taken > 0.0F,
                            "In 120 ticks at arm's length she never landed a "
                                    + "hit. She was "
                                    + (engaged ? "engaging" : "withdrawing")
                                    + ", holding "
                                    + maid.getMainHandItem().getItem()
                    );
                })
                .thenSucceed();
    }

    /**
     * 要退多远，就查多远。
     *
     * <p>此前只朝身后探一个固定的三格，于是"退得开"这个答案跟她实际打算退的
     * 距离无关：远程想拉开到八格时要走的是四五格，而一格的余量对此一无所知。
     * 半路才发现墙的撤退，等于撤退停在半途而目标继续逼近——在旁边看就是她
     * 决定站在那里不动。
     *
     * <p>逐格走查还顺带管住了另一种情形：落脚点是空地，但中间隔着一堵墙。
     */
    @GameTest(batch = "combatengagement", templateNamespace = "minecraft", template = "empty")
    public static void aRetreatChecksTheWholeDistance(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 7, 7);
        // 站在靠 +x 的一侧，威胁也在 +x，于是她的退路朝 -x，地板到 x=0 为止。
        EntityMaid maid = scene.maid(5, 2, 3);
        Vec3 threat = maid.position().add(2.0D, 0.0D, 0.0D);

        helper.assertFalse(
                RetreatSpace.cornered(maid, threat, 2.0D),
                "Two blocks of solid floor behind her were called a dead end"
        );
        helper.assertTrue(
                RetreatSpace.cornered(maid, threat, 20.0D),
                "Twenty blocks of retreat were approved on a floor only a few "
                        + "blocks wide, which is what a fixed one-stride probe "
                        + "cannot tell apart"
        );
        helper.succeed();
    }

    /** 空手时不该硬拼——风险裁决应当让她放弃交战。 */
    @GameTest(batch = "combatengagement", templateNamespace = "minecraft", template = "empty")
    public static void barehandedSheDoesNotCommit(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        seeHostile(helper, maid, 3.0D);

        settle(maid, helper.getLevel().getGameTime());

        helper.assertFalse(
                maid.getBrain()
                        .getMemory(MemoryModuleType.ATTACK_TARGET)
                        .isPresent(),
                "She took an attack target with nothing to fight with"
        );
        helper.succeed();
    }

    /**
     * 推进足够多的 tick，越过编排器的评估间隔。
     *
     * <p>候选按实体 ID 错峰评估，间隔十 tick：只推一次时，某个实体这一 tick
     * 恰好不被评估，断言就会随实体 ID 变化时灵时不灵。这个测试最初正是只推
     * 一次，先通过后失败，而中间并没有人改动战斗逻辑。
     */
    private static void settle(EntityMaid maid, long gameTime) {
        for (int tick = 0; tick <= 20; tick++) {
            productionIntents().tick(maid, gameTime + tick);
        }
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
