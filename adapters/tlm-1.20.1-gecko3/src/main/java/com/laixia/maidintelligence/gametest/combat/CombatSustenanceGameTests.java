package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.sustenance.FoodValue;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmCombatAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.MeleeSwing;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ThreatProfile;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance.MaidEating;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance.TlmFoodScanner;
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
 * 她在战斗里把东西吃下去这件事，真的发生。
 *
 * <p>要不要吃、吃哪一口，由 CombatEatingVerification 以纯 JVM 断言覆盖，那里
 * 七条规则各有夹具。这里只问一件那边问不到的事：**决定作出之后，那口东西有没有
 * 真的进到她嘴里**。选择和执行是两段代码，而基准局十二次全部答"不吃"——那是正确
 * 答案，但一条只验证得到"不吃"的链路等于完全没有被验证。
 *
 * <p>敌人用 {@code placeInert} 放：这里要的是几何和威胁读数，不是一场真打，而
 * 会索敌的僵尸在共用世界里会走进邻座夹具。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class CombatSustenanceGameTests {
    private CombatSustenanceGameTests() {
    }

    /**
     * 血量见底、身边只有一只，她该把金苹果吃了。
     *
     * <p>两条断言：她开始进食，且那颗苹果离开了背包。只断言前者的话，一个"举起
     * 手做了个吃的动作但物品还在包里"的实现也能通过——而那正是执行层最容易出的
     * 那类错。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void dyingSheEatsWhatSheIsCarrying(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.GOLDEN_APPLE)
        );
        // 一只就够，而且要离得开：贴脸猛打的时候不吃才是对的，那条由纯 JVM
        // 那边覆盖，这里要的是"该吃"的处境。
        seeHostile(maid, helper, 4.0D);
        maid.setHealth(2.0F);

        new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        ).execute(maid);

        helper.assertTrue(
                MaidEating.chewing(maid),
                "只剩两点血、包里有金苹果，她没有去吃；手里是 "
                        + maid.getMainHandItem().getItem()
        );
        helper.assertTrue(
                maid.getMainHandItem().is(Items.GOLDEN_APPLE),
                "她在吃东西，但手里拿的是 "
                        + maid.getMainHandItem().getItem()
        );
        helper.assertFalse(
                maid.getAvailableBackpackInv().getStackInSlot(0)
                        .is(Items.GOLDEN_APPLE),
                "苹果还在原来的格子里；她做了个吃的动作却没把它拿出来"
        );
        helper.succeed();
    }

    /**
     * 嚼东西的这段时间里，她不许站进对方的攻击范围。
     *
     * <p>手里是食物、武器在包里，这一刀本来就挥不出来。可决定站位的那几条分支全
     * 建立在"她随时可能挥"之上：冷却是空的，于是 strikeWindow 让她站到触及边缘，
     * 甚至直接返回 0（走进去）。于是那三十二 tick 她走进对方的攻击范围，白挨一到
     * 两下，什么也换不回来——实机暴露的就是这个。
     *
     * <p>断言比的是**她要的距离**而不是最终落点：走没走到位受寻路、击退、拥挤影响，
     * 而这条规则管的只是"她想站哪儿"。基线用同一副处境下不嚼东西时的距离，所以
     * 断言不会把某个具体数字写死。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void chewingSheKeepsOutOfReach(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 7, 7);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        Zombie zombie = seeHostile(maid, helper, 2.0D);
        ScannedThreat threat = new ScannedThreat(zombie, new ThreatSample(
                maid.distanceTo(zombie),
                ThreatProfile.strikeDamage(zombie),
                ThreatProfile.reach(zombie, maid),
                ThreatProfile.attackPeriod(zombie),
                zombie.getHealth(),
                false,
                0.0D,
                ThreatRelation.ATTACKING_MAID
        ));
        double fighting = MeleeSwing.holdDistance(
                maid, threat, List.of(), List.of(threat), 0.6F
        );

        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE)
        );
        maid.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(
                MaidEating.chewing(maid),
                "夹具没把她放进进食状态，这一条就没在考它"
        );
        double chewing = MeleeSwing.holdDistance(
                maid, threat, List.of(), List.of(threat), 0.6F
        );

        helper.assertTrue(
                chewing > threat.sample().reach(),
                "她在嚼东西，却要站到 " + String.format("%.2f", chewing)
                        + "——对方触及 "
                        + String.format("%.2f", threat.sample().reach())
                        + "，这是走进去白挨"
        );
        helper.assertTrue(
                chewing > fighting,
                "嚼东西时要的距离（" + String.format("%.2f", chewing)
                        + "）没有比打得动时（" + String.format("%.2f", fighting)
                        + "）更远，那这条规则等于没生效"
        );
        helper.succeed();
    }

    /**
     * 剑没有被丢掉——它去了苹果原来的位置。
     *
     * <p>吃完之后她还得接着打。手上那把如果在进食时被丢在地上，这个功能就是拿
     * 一次续命换一把武器，而背包是它唯一该去的地方。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void herWeaponGoesIntoThePackNotOntoTheFloor(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.GOLDEN_APPLE)
        );
        seeHostile(maid, helper, 4.0D);
        maid.setHealth(2.0F);

        new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        ).execute(maid);

        helper.assertTrue(
                MaidEating.chewing(maid),
                "夹具没让她吃起来，这条什么都测不到"
        );
        helper.assertTrue(
                maid.getAvailableBackpackInv().getStackInSlot(0)
                        .is(Items.IRON_SWORD),
                "为了吃一口，她的剑不在包里了——现在是 "
                        + maid.getAvailableBackpackInv().getStackInSlot(0)
                                .getItem()
        );
        helper.succeed();
    }

    /**
     * 有副作用的那一口，价钱里要看得见那笔账。
     *
     * <p>玩家报的是"她似乎不知道吃了有什么增益或减益"。要不要吃归 {@code
     * CombatEatingVerification}，但那边的 {@code FoodValue} 是手写的——**"原版/模组
     * 真的这么描述这件物品吗"只有这一层答得了**：食用效果挂在 {@code
     * FoodProperties.getEffects()} 上，带概率、带时长、带等级，而此前扫描器只认
     * 回血、吸收、瞬间治疗三种，别的一律记零。
     *
     * <p>毒马铃薯是原版物品，所以这条不需要装一个模组：任何一个"给食物挂负面
     * 效果"的模组走的是同一个 API。三条断言分别钉住三件不同的事——它仍然算食物
     * （不是被藏起来了）、它的价值是负的、以及它排在干净食物后面。缺了第一条，
     * 一个"把有害食物直接当成不可食用"的实现也能通过，而那会让她饿死在一包腐肉
     * 边上。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void sheKnowsTheMouthfulThatCostsHer(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 3);
        EntityMaid maid = scene.maid(1, 2, 1);
        TlmFoodScanner larder = new TlmFoodScanner();

        FoodValue poison = larder.valueOf(
                maid, new ItemStack(Items.POISONOUS_POTATO), 0
        );
        FoodValue clean = larder.valueOf(maid, new ItemStack(Items.BREAD), 1);

        helper.assertTrue(
                poison != null && poison.nourishing(),
                "毒马铃薯根本没被当成食物——把有害的藏起来不是认识它，"
                        + "那会让她守着一包腐肉饿死"
        );
        helper.assertTrue(
                poison.effectiveHealthGain() < 0.0D,
                "毒马铃薯的净收益是 " + poison.effectiveHealthGain()
                        + "；那五秒的毒没有进价钱"
        );
        helper.assertTrue(
                clean != null
                        && poison.effectiveHealthGain()
                                < clean.effectiveHealthGain(),
                "毒马铃薯排在面包前面：毒 " + poison.effectiveHealthGain()
                        + " vs 面包 " + (clean == null
                                ? "不是食物" : clean.effectiveHealthGain())
        );
        helper.succeed();
    }

    private static Zombie seeHostile(
            EntityMaid maid,
            GameTestHelper helper,
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
