package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponKind;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmCombatAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard.ShieldLedger;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import net.minecraft.world.entity.item.ItemEntity;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
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

import java.util.List;

/**
 * 她翻自己的背包，而不是只看手上有什么。
 *
 * <p>这里不放任何**会动的**敌对生物。真实僵尸的索敌达十六格，放进共享测试
 * 世界会波及邻座的测试，而"背包里有什么、能不能用"这半条链本来就不需要有人
 * 来打她。交战本身的判断由 CombatRiskAssessmentVerification 与
 * CombatWeaponSelectionVerification 以纯 JVM 断言覆盖。
 *
 * <p>换手是例外：它要跑真正的战斗动作才发生，而战斗动作要有敌人才启动。那些
 * 用 {@code placeInert} 放下——无 AI、不索敌、不移动，只作为几何存在。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class CombatArsenalGameTests {
    private static final TlmWeaponScanner SCANNER =
            new TlmWeaponScanner(RangedWeaponRecognizer.NONE);

    private CombatArsenalGameTests() {
    }

    /**
     * 空着手站着，武器在包里——本体只看主手，会认为她没有武器。
     */
    @GameTest(batch = "combatarsenal", templateNamespace = "minecraft", template = "empty")
    public static void weaponsInThePackAreFound(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        give(maid, 0, new ItemStack(Items.IRON_SWORD));

        List<WeaponCandidate> arsenal = SCANNER.scan(maid);
        WeaponCandidate melee = firstOf(arsenal, WeaponKind.MELEE);
        helper.assertTrue(
                melee != null,
                "A sword in her pack was not found while her hands were empty"
        );
        helper.assertFalse(
                melee.inHand(),
                "A packed sword was reported as already held"
        );
        helper.assertTrue(
                melee.usable(),
                "A sword was reported as unusable"
        );
        helper.succeed();
    }

    /**
     * 没箭的弓不是武器。
     *
     * <p>两次扫描只差一组箭，结论必须相反——否则她会举着空弓走进战斗。
     */
    @GameTest(batch = "combatarsenal", templateNamespace = "minecraft", template = "empty")
    public static void aBowWithoutArrowsIsNotUsable(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        give(maid, 0, new ItemStack(Items.BOW));

        WeaponCandidate dry = firstOf(SCANNER.scan(maid), WeaponKind.BOW);
        helper.assertTrue(dry != null, "The bow itself was not found");
        helper.assertFalse(
                dry.usable(),
                "A bow with no arrows was offered as a usable weapon"
        );

        give(maid, 1, new ItemStack(Items.ARROW, 8));
        WeaponCandidate armed = firstOf(SCANNER.scan(maid), WeaponKind.BOW);
        helper.assertTrue(
                armed.usable(),
                "Arrows in her pack did not make the bow usable"
        );
        helper.succeed();
    }

    /** 手上那把要被认出来是手上的，否则每次决策都会白换一次装。 */
    @GameTest(batch = "combatarsenal", templateNamespace = "minecraft", template = "empty")
    public static void theHeldWeaponIsReportedAsHeld(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setItemInHand(
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Items.DIAMOND_SWORD)
        );

        WeaponCandidate melee =
                firstOf(SCANNER.scan(maid), WeaponKind.MELEE);
        helper.assertTrue(melee != null, "The held sword was not found");
        helper.assertTrue(
                melee.inHand(),
                "The sword in her hand was reported as packed"
        );
        helper.succeed();
    }

    /** 面包不是武器，哪怕她很想吃。 */
    @GameTest(batch = "combatarsenal", templateNamespace = "minecraft", template = "empty")
    public static void foodIsNotAWeapon(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        give(maid, 0, new ItemStack(Items.BREAD, 16));

        helper.assertTrue(
                SCANNER.scan(maid).isEmpty(),
                "Something inedible-as-a-weapon was offered as one"
        );
        helper.succeed();
    }

    /**
     * 手上那把更重，不等于手上那把更该用。
     *
     * <p>玩家报的是"主手拿着斧头她就只用斧头，再也不换"。既有的换手测试只验了
     * 变强的那一半——石剑换钻石剑——而谓词写的是"选中那把**或同类里更强的**"，
     * 于是宿主先看主手就直接命中：斧头的伤害评分高于剑，任何"去拿剑"的请求
     * 都被手里的斧头满足，查找根本到不了背包。棘轮只朝一个方向转。
     *
     * <p>用一堆敌人做场景，因为这正是剑该赢的那一档：斧头一下重，剑一下打好
     * 几个。断言看的是最终握着什么——选择那一层本来就选对了。
     */
    @GameTest(batch = "combatarsenal", templateNamespace = "minecraft", template = "empty")
    public static void aHeavierBladeInHandDoesNotVetoTheChoice(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 6, 6);
        EntityMaid maid = scene.maid(3, 2, 3);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE)
        );
        give(maid, 0, new ItemStack(Items.IRON_SWORD));

        List<Zombie> pack = new java.util.ArrayList<>();
        for (int slot = 0; slot < 5; slot++) {
            Zombie zombie = new Zombie(helper.getLevel());
            zombie.setPos(
                    maid.getX() + 2.0D + slot * 0.2D, maid.getY(), maid.getZ()
            );
            pack.add(CompanionScene.placeInert(helper, zombie));
        }
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, List.copyOf(pack))
        );

        new TlmCombatAction(
                new TlmThreatScanner(), SCANNER
        ).execute(maid);

        helper.assertTrue(
                maid.getMainHandItem().is(Items.IRON_SWORD),
                "五只围着她，她仍握着 " + maid.getMainHandItem().getItem()
                        + "；重武器一进手就否决了后面每一次选择"
        );
        helper.succeed();
    }

    /**
     * 包里的盾也要能拿出来，而且拿出来就得举。
     *
     * <p>两件事一条测试，因为它们在同一个 tick 上：{@code ShieldGuard} 先把盾
     * 装上副手，再决定举不举。分开写会出现"装上了但没举"却两条都绿的情况。
     *
     * <p>为什么必须是"从包里"而不是"预先放在副手"：本体覆写了
     * {@code completeUsingItem}，它无条件调用 {@code backCurrentHandItemStack}，
     * 而那个方法会把副手里的东西整个塞回背包。于是她战斗中吃下的第一口食物就
     * 会静默卸掉自己的盾。实测：一颗苹果三十二 tick，随后两百六十八 tick 的
     * {@code offhand=empty}。装备这一步就是那条修复，不是便利功能。
     */
    @GameTest(batch = "combatarsenal", templateNamespace = "minecraft", template = "empty")
    public static void aShieldInThePackReachesHerOffHand(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 6, 6);
        EntityMaid maid = scene.maid(3, 2, 3);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        give(maid, 0, new ItemStack(Items.SHIELD));
        ShieldLedger.reset(maid);

        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX() + 1.5D, maid.getY(), maid.getZ());
        Zombie inert = CompanionScene.placeInert(helper, zombie);
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, List.of(inert))
        );

        new TlmCombatAction(
                new TlmThreatScanner(), SCANNER
        ).execute(maid);

        helper.assertTrue(
                maid.getOffhandItem().is(Items.SHIELD),
                "包里有盾、手边有敌人，她的副手仍然是 "
                        + maid.getOffhandItem().getItem()
        );
        helper.assertTrue(
                ShieldLedger.raised(maid) > 0,
                "盾装上了却没举起来——装备与举盾是同一 tick 的两步，"
                        + "只做前一半等于没有防御"
        );
        helper.succeed();
    }

    /**
     * 副手本来就有东西时不许抢。
     *
     * <p>玩家往副手里放什么是他自己的决定。这道门是 {@code equipFromPack} 唯一
     * 的前置条件，而它同时也保证我们不会和本体争夺同一个槽位——本体自己也会往
     * 副手写（雪球任务、隐藏槽恢复），两边都无条件写就会每 tick 互相覆盖。
     */
    @GameTest(batch = "combatarsenal", templateNamespace = "minecraft", template = "empty")
    public static void anOccupiedOffHandIsLeftAlone(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 6, 6);
        EntityMaid maid = scene.maid(3, 2, 3);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        maid.setItemInHand(
                InteractionHand.OFF_HAND, new ItemStack(Items.TORCH)
        );
        give(maid, 0, new ItemStack(Items.SHIELD));

        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX() + 1.5D, maid.getY(), maid.getZ());
        Zombie inert = CompanionScene.placeInert(helper, zombie);
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, List.of(inert))
        );

        new TlmCombatAction(
                new TlmThreatScanner(), SCANNER
        ).execute(maid);

        helper.assertTrue(
                maid.getOffhandItem().is(Items.TORCH),
                "她把玩家放进副手的东西换掉了，现在拿着 "
                        + maid.getOffhandItem().getItem()
        );
        helper.succeed();
    }

    private static void give(EntityMaid maid, int slot, ItemStack stack) {
        maid.getAvailableBackpackInv().setStackInSlot(slot, stack);
    }

    private static WeaponCandidate firstOf(
            List<WeaponCandidate> arsenal,
            WeaponKind kind
    ) {
        for (WeaponCandidate candidate : arsenal) {
            if (candidate.kind() == kind) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 空着手挨追时，她去捡地上那把剑。
     *
     * <p>玩家报告：武器打没了、怪在后面追，她只会一直跑，脚边躺着剑也不捡。
     * 空手在风险裁决里是直接判撤离的，所以那条路上她永远不会重新武装。
     *
     * <p>断言看**走路目标**而不是"她有没有捡到"：捡起来那一下归宿主，寻路耗时
     * 归引擎，等它们会在慢机器上随机失败。这一条只问她有没有被派过去。
     */
    @GameTest(batch = "combatarsenal", templateNamespace = "minecraft", template = "empty")
    public static void unarmedSheGoesForTheBladeOnTheGround(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 12, 8);
        EntityMaid maid = scene.maid(2, 2, 4);
        // 什么都不给她：手是空的，包是空的。

        ItemEntity blade = new ItemEntity(
                helper.getLevel(),
                maid.getX() + 5.0D, maid.getY(), maid.getZ(),
                new ItemStack(Items.IRON_SWORD)
        );
        blade.setPickUpDelay(0);
        helper.getLevel().addFreshEntity(blade);

        Zombie chaser = new Zombie(helper.getLevel());
        chaser.setPos(maid.getX() - 3.0D, maid.getY(), maid.getZ());
        Zombie inert = CompanionScene.placeInert(helper, chaser);
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, List.of(inert))
        );

        TlmAffordancePerceptionService perception =
                new TlmAffordancePerceptionService();
        perception.observeMaid(maid, helper.getLevel().getGameTime());

        new TlmCombatAction(
                new TlmThreatScanner(), SCANNER, null, perception
        ).execute(maid);

        var walk = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
        helper.assertTrue(walk.isPresent(), "空手挨追，她哪儿也没去");
        double toBlade = walk.get().getTarget().currentPosition()
                .distanceTo(blade.position());
        helper.assertTrue(
                toBlade < 1.5D,
                "她被派去的地方离那把剑还有 " + toBlade + " 格——"
                        + "空手时地上的武器没有进入她的选择"
        );
        helper.succeed();
    }
}
