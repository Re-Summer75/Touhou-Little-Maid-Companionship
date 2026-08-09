package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponKind;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 她翻自己的背包，而不是只看手上有什么。
 *
 * <p>这里不放任何敌对生物。真实僵尸的索敌达十六格，放进共享测试世界会波及
 * 邻座的测试，而"背包里有什么、能不能用"这半条链本来就不需要有人来打她。
 * 交战本身的判断由 CombatRiskAssessmentVerification 与
 * CombatWeaponSelectionVerification 以纯 JVM 断言覆盖。
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
    @GameTest(templateNamespace = "minecraft", template = "empty")
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
    @GameTest(templateNamespace = "minecraft", template = "empty")
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
    @GameTest(templateNamespace = "minecraft", template = "empty")
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
    @GameTest(templateNamespace = "minecraft", template = "empty")
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
}
