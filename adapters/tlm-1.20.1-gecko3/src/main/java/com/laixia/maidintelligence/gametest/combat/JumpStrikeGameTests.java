package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.CombatMovement;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.JumpLedger;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.JumpStrike;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.MeleeSwing;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 离地这件事：什么时候值得跳，跳下来的那一刀值多少。
 *
 * <p>与 {@link MeleeEngagementGameTests} 分开，是因为这里问的不是"刀能不能落在
 * 对方身上"，而是"要不要把脚从地上拿开"。两个原因各自成立、互不替代：够不着的
 * 高处要跳，够得着的目标则是为了让下一刀落在下落途中——那是暴击，是白拿的伤害。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class JumpStrikeGameTests {
    private JumpStrikeGameTests() {
    }

    /**
     * 头顶上的东西够不着，就跳起来够。
     *
     * <p>她的触及是个球，所以悬在头顶一格多的恼鬼和隔着半个房间的僵尸一样在
     * 范围外——不是选敌选错了，是从站着的位置真的碰不到。
     *
     * <p>夹具用钉住不动的僵尸，而不是催生这条规则的恼鬼：恼鬼每 tick 都在换位置，
     * 断言就会混进"这一 tick 它飘到哪儿"。**规则本身要覆盖会飞的目标**——跳起来
     * 本就能提高够到它的概率——只是夹具得把那份不确定性排除掉。
     *
     * <p>两条断言钉住判断的两侧：够不着的高处要跳，够得着的平地不许跳。
     */
    @GameTest(batch = "jumpstrike", templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 120)
    public static void sheJumpsForWhatHoversOverhead(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        // 先落地：刚放下的实体 onGround 还是 false，而"站着"正是第一道门。
        maid.tick();
        maid.tick();

        Zombie overhead = new Zombie(helper.getLevel());
        overhead.setPos(maid.getX(), maid.getY() + 2.4D, maid.getZ());
        CompanionScene.placeInert(helper, overhead);
        helper.assertTrue(
                JumpStrike.worthLeavingTheGround(maid, overhead),
                "头顶两格多的目标她够不着，却也不打算跳（触及 "
                        + MeleeSwing.reach(maid, overhead) + "，站地 "
                        + maid.onGround() + "）"
        );

        Zombie alongside = seeHostile(helper, maid, 1.0D);
        helper.assertFalse(
                JumpStrike.worthLeavingTheGround(maid, alongside),
                "面前一格的目标本来就够得着，跳起来只是把落脚点花掉"
        );
        helper.succeed();
    }

    /**
     * 上升段不许挥刀——这就是"跳劈没对上节奏"的那一处。
     *
     * <p>之前的表现是"总是先攻击后再跳"：`swingIfReady` 一到冷却就挥，而起跳后
     * 前六 tick 她都在往上走。上升段挥刀是两头落空——vanilla 的暴击要
     * {@code fallDistance > 0}，横扫要两只脚落地，上升段两个都不成立。
     *
     * <p>所以这一条钉的不是"她跳不跳"，是"这一刀落在什么时候"：往上走的时候不许
     * 出刀，开始下落之后必须出刀。两侧都断言，否则"永远不挥"也能过。
     */
    @GameTest(batch = "jumpstrike", templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 120)
    public static void sheDoesNotSwingOnTheWayUp(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        Zombie victim = seeHostile(helper, maid, 1.0D);

        maid.setOnGround(false);
        maid.setDeltaMovement(0.0D, 0.42D, 0.0D);
        helper.assertTrue(
                JumpStrike.climbing(maid),
                "竖速 +0.42 还判不出她在上升，后面的断言就没有意义"
        );
        float before = victim.getHealth();
        helper.assertFalse(
                MeleeSwing.swingIfReady(maid, victim),
                "她还在往上走就把刀挥了——暴击和横扫都拿不到，这一刀白挥"
        );
        helper.assertTrue(
                victim.getHealth() == before,
                "上升段那一刀被拦下了，伤害却照样结算了"
        );

        // 同一副条件，只把竖直速度翻到下落。这一刀现在必须出。
        maid.setDeltaMovement(0.0D, -0.08D, 0.0D);
        maid.fallDistance = 1.0F;
        helper.assertTrue(
                MeleeSwing.swingIfReady(maid, victim),
                "已经在下落了还不出刀，那就不是等时机，是不会挥"
        );
        helper.assertTrue(
                victim.getHealth() < before,
                "下落那一刀挥了，对方却没掉血"
        );
        helper.succeed();
    }

    /**
     * 预判圈只有外沿：一次滞空够不着的，不跳。
     *
     * <p>宽度是她自己一跳能横向挪的距离（约 0.99 格）。没有这道界的时候，她会朝
     * 四格外正冲过来的东西起跳、落进包围：实测持剑 83t → 4t（R-20）。界要来自
     * **她自己**能挪多远，不来自对方冲多快。
     *
     * <p>**没有内沿**——曾经有过一道"够得着就别跳"，靶场把它证伪了：冷却 13 tick、
     * 窗口 [7,11]，而她站在触及之外那些 tick 上读到的剩余恒定是 [0,6]。该起跳的
     * 那半个冷却里她还贴在触及之内，内沿挡掉的正是她每一次真实的机会。所以这里
     * 只断言外沿，并且明确钉住"贴身也照跳"。
     */
    @GameTest(batch = "jumpstrike", templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 120)
    public static void theLeapReachHasOnlyAnOuterEdge(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 9, 9);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        maid.tick();
        maid.tick();
        Zombie victim = seeHostile(helper, maid, 1.0D);
        double reach = MeleeSwing.reach(maid, victim);

        // 一次滞空也挪不过去：不跳。
        victim.setPos(maid.getX() + reach + 3.0D, maid.getY(), maid.getZ());
        helper.assertFalse(
                JumpStrike.worthCrittingNow(
                        maid, closing(maid, victim), List.of()
                ),
                "三格开外她也跳，那不是提前量，是乱跳："
                        + JumpLedger.phase(maid, closing(maid, victim))
        );
        helper.succeed();
    }

    /**
     * 起跳窗口是下落段，不是上升段。
     *
     * <p>旧代码写的是"冷却七 tick 内就起跳"，而起跳后要到第七 tick 才开始下落——
     * 七次里有六次落在上升段。窗口是 {@code [7, 11]}，两侧都要钉住：早一 tick 起跳
     * 就砍在最高点（{@code fallDistance} 还是零），晚一 tick 她已经落地。
     *
     * <p>时间由关卡推进而不是手工 {@code maid.tick()}：实测手工调用不会推进
     * {@code tickCount}，而"还剩几 tick"整个建立在它上面。
     */
    @GameTest(batch = "jumpstrike", templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 160)
    public static void theLeapWindowIsTheDescent(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        // 放在触及外面那一圈里——判据只在这一圈内起跳，站在触及之内它根本不看时机。
        Zombie victim = seeHostile(helper, maid, 1.0D);
        double perch = MeleeSwing.reach(maid, victim) + 0.4D;
        ScannedThreat quarry = measured(maid, victim);
        // 关掉她自己的 AI：这一条量的是判据的形状，不是她这一局会怎么打。她自己
        // 决定起跳的话，离地那几 tick 判据本来就该是假，读数就混了。时间仍由关卡
        // 推进——tickCount 走的是 Entity.baseTick，跟 AI 开不开无关。
        maid.setNoAi(true);
        Map<Integer, Boolean> verdicts = new HashMap<>();
        Map<Integer, String> why = new HashMap<>();

        helper.startSequence()
                .thenExecute(() -> helper.assertTrue(
                        MeleeSwing.swingIfReady(maid, victim),
                        "这一刀没挥出去，后面的时机无从谈起"
                ))
                // 每 tick 记一次"冷却还剩多少"对应的判断，冷却走完为止。
                //
                // 每 tick 先把敌人按回原位：两个实体会互相推开，二十四 tick 下来
                // 那点位移足以把水平距离推出触及之外，读到的就成了碰撞物理，不是
                // 这条判据。上一版正是这么整片读成 false 的。
                .thenExecuteFor(24, () -> {
                    victim.setPos(
                            maid.getX() + perch, maid.getY(), maid.getZ()
                    );
                    // 站地也钉住：关掉 AI 之后她不再被判定为站在地上，而"站不站着"
                    // 是另一道门、另有测试管。这一条问的只是窗口的形状。
                    maid.setOnGround(true);
                    int left = JumpStrike.ticksUntilSwing(maid);
                    why.putIfAbsent(left, JumpLedger.phase(maid, quarry));
                    verdicts.putIfAbsent(
                            left,
                            JumpStrike.worthCrittingNow(maid, quarry, List.of())
                    );
                })
                .thenExecute(() -> {
                    // 一侧：凡是判"跳"的 tick，都必须落在下落段里。这是相位那条
                    // 错误的直接反面——旧代码在上升段判跳，一次暴击也拿不到。
                    verdicts.forEach((left, leap) -> helper.assertFalse(
                            Boolean.TRUE.equals(leap)
                                    && (left < JumpStrike.fallBeginsAt()
                                            || left > JumpStrike.landsAt()),
                            "冷却剩 " + left + " tick 就起跳，落在窗口 ["
                                    + JumpStrike.fallBeginsAt() + ","
                                    + JumpStrike.landsAt() + "] 之外："
                                    + why.get(left)
                    ));
                    // 另一侧：窗口里至少要真有跳得成的时刻，否则"永远不跳"也能
                    // 把上面那条断言过掉。具体哪几 tick 成立取决于几何——目标离
                    // 得越近、能跳的 tick 越多——所以这里不钉死是哪几个。
                    boolean any = false;
                    for (int left = JumpStrike.fallBeginsAt();
                            left <= JumpStrike.landsAt(); left++) {
                        any |= Boolean.TRUE.equals(verdicts.get(left));
                    }
                    helper.assertTrue(
                            any,
                            "整个下落段里她一次也不跳，这条规则等于没接上：" + why
                    );
                })
                .thenSucceed();
    }

    /**
     * 离地之后她还能往目标那边挪——玩家跳起来也一直能控制方向。
     *
     * <p>原版把这条路堵死在 {@code PathNavigation#createPath}：不站在地上就直接
     * 返回 null，于是腾空那几 tick 里任何一次重新寻路都会让
     * {@code MoveToTargetSink} 起不来并抹掉 WALK_TARGET。看上去就是"跳一下，白白
     * 送出去一步"。
     *
     * <p>断言比的是同一副条件下的水平位移，而不是某个速度常数：常数会把原版的
     * 0.02 抄进断言里，那样这条测试只能证明常量被抄对了。
     */
    @GameTest(batch = "jumpstrike", templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 120)
    public static void sheStillSteersWhileOffTheGround(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 7, 7);
        EntityMaid maid = scene.maid(1, 2, 3);
        maid.tick();
        maid.tick();

        Zombie quarry = seeHostile(helper, maid, 4.0D);
        double from = maid.getX();
        // 起跳要等一 tick 才生效：leap() 只是把请求交给 JumpControl，真正离地在
        // 下一次 aiStep 里。所以循环不能拿"她已经离地了"当进入条件。
        JumpStrike.leap(maid);
        boolean leftTheGround = false;
        double airborne = 0.0D;
        for (int tick = 0; tick < 16; tick++) {
            CombatMovement.chase(maid, quarry, 1, 0.6F);
            maid.tick();
            if (!maid.onGround()) {
                leftTheGround = true;
                airborne = maid.getX() - from;
            }
        }

        helper.assertTrue(leftTheGround, "她压根没跳起来，这一局比不出东西");
        helper.assertTrue(
                airborne > 0.3D,
                "腾空的这一跳她只是原地上下，往目标挪了 "
                        + String.format("%.2f", airborne) + " 格"
        );
        helper.succeed();
    }

    /**
     * 落下来的那一刀更疼——原版只给玩家，和横扫是同一笔欠账。
     *
     * <p>不比较绝对伤害值：那会把攻击力、附魔、护甲一起写死进断言。比的是
     * 同一副装备、同一个目标，落地砍与下落砍之间的差。
     */
    @GameTest(batch = "jumpstrike", templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 120)
    public static void aFallingBlowLandsHarder(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );

        Zombie grounded = seeHostile(helper, maid, 1.0D);
        float before = grounded.getHealth();
        MeleeSwing.swingIfReady(maid, grounded);
        float flat = before - grounded.getHealth();

        // 同一副装备、同一种目标，只把她换成正在下落的状态。
        Zombie falling = seeHostile(helper, maid, -1.0D);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_COOLING_DOWN);
        maid.setOnGround(false);
        maid.fallDistance = 1.0F;
        float mark = falling.getHealth();
        MeleeSwing.swingIfReady(maid, falling);
        float crit = mark - falling.getHealth();

        helper.assertTrue(
                flat > 0.0F,
                "地面上那一刀就没打中，这一局比不出东西来"
        );
        helper.assertTrue(
                crit > flat,
                "下落中砍出 " + crit + "，站着砍出 " + flat
                        + "：跳劈没有多出任何伤害"
        );
        helper.succeed();
    }


    /** 每一道门的现值，失败时直接说清是哪一道拦下的。 */
    private static String gates(EntityMaid maid, Zombie victim) {
        return "站地=" + maid.onGround()
                + " 冷却已空=" + MeleeSwing.recovered(maid)
                + " 距挥刀=" + JumpStrike.ticksUntilSwing(maid)
                + "/窗口[" + JumpStrike.fallBeginsAt()
                + "," + JumpStrike.landsAt() + "]"
                + " 距离=" + String.format("%.2f", maid.distanceTo(victim))
                + " 触及=" + String.format("%.2f", MeleeSwing.reach(maid, victim));
    }

    /** 一份量测，闭合速度为零：谁也没在靠近谁。 */
    private static ScannedThreat measured(EntityMaid maid, Zombie hostile) {
        return measured(maid, hostile, 0.0D);
    }

    /**
     * 同上，但两人正以走路速度接近（每秒四格）。
     *
     * <p>"提前起跳"这件事整个建立在闭合速度上——钉成零就等于说"她永远走不到"，
     * 判据当然永远拒绝。上一版夹具正是这么写的，于是断言全错在夹具而不在代码。
     */
    private static ScannedThreat closing(EntityMaid maid, Zombie hostile) {
        return measured(maid, hostile, 4.0D);
    }

    private static ScannedThreat measured(
            EntityMaid maid,
            Zombie hostile,
            double closingSpeed
    ) {
        return new ScannedThreat(hostile, new ThreatSample(
                maid.distanceTo(hostile), 3.0D, 2.0D, 20,
                hostile.getHealth(), false, closingSpeed,
                ThreatRelation.ATTACKING_MAID
        ));
    }

    /** 放一只钉住不动的敌人，并塞进她的可见实体记忆。 */
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
