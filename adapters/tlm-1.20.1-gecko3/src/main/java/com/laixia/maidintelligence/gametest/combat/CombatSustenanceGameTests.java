package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmCombatAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance.MaidEating;
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

    private static void seeHostile(
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
    }
}
