package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatStance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.WeaponKind;
import com.laixia.maidintelligence.feature.behavior.domain.combat.WeaponSelectionPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.WeaponStowPolicy;

import java.util.List;

/**
 * 武器选择与收纳的决策边界。
 */
public final class CombatWeaponSelectionVerification {
    private static final WeaponSelectionPolicy POLICY =
            WeaponSelectionPolicy.INSTANCE;

    private CombatWeaponSelectionVerification() {
    }

    public static void main(String[] args) {
        verifiesRangePreferredWhenNotPressed();
        verifiesMeleeTakesOverWhenPressed();
        verifiesStanceDoesNotFlipOnTheThreshold();
        verifiesEmptyBowIsNotAWeapon();
        verifiesBowOnlyMaidStillFightsUpClose();
        verifiesHeldWeaponResistsSwapping();
        verifiesNothingUsableDisengages();
        verifiesStowingWaitsForQuiet();
        System.out.println("Combat weapon selection verification passed.");
    }

    /** 有弓有箭且离得开，就不该走过去拿剑砍。 */
    private static void verifiesRangePreferredWhenNotPressed() {
        CombatStance stance = POLICY.choose(
                List.of(sword(0.8D), bow(0.5D, true)),
                10.0D, false);
        require(
                stance.posture() == CombatStance.Posture.RANGED,
                "A usable bow at ten blocks was passed over for melee"
        );
        require(
                stance.preferredRange() > 0.0D,
                "A ranged stance named no distance to hold"
        );
    }

    /**
     * 被贴脸时弓没有用，哪怕它评分更高。
     *
     * <p>这条一度被改成"退得开就继续射"，结果是她几乎不再拔刀：女仆基础
     * 移速 0.7，僵尸 0.23，"跑不跑得过"对几乎所有怪的答案都是跑得过。
     * 边退边射是花招，被贴上来就动刀才是玩家要的那个答案。
     */
    private static void verifiesMeleeTakesOverWhenPressed() {
        CombatStance stance = POLICY.choose(
                List.of(sword(0.3D), bow(0.9D, true)),
                1.5D, false);
        require(
                stance.posture() == CombatStance.Posture.MELEE,
                "She kept aiming a bow at something already on top of her"
        );
    }

    /**
     * 同一个距离不能因为她当前拿什么就换个答案——除非那个答案更稳。
     *
     * <p>玩家看到的症状是"近处又退又想砍、砍不着"：进近战和出近战用同一个
     * 阈值，目标在阈值附近晃动时姿态每 tick 翻面，后退那一步刚好把目标挪出
     * 攻击距离。滞回让她一旦动手就得真正拉开才改主意。
     */
    private static void verifiesStanceDoesNotFlipOnTheThreshold() {
        List<WeaponCandidate> both = List.of(sword(0.8D), bow(0.5D, true));
        // 阈值之外一点点：还没动手时，这是远程的距离。
        CombatStance fresh = POLICY.choose(both, 5.0D, false);
        require(
                fresh.posture() == CombatStance.Posture.RANGED,
                "Outside the pressed distance she chose melee anyway"
        );
        // 同样的距离，但她已经在砍了——不该因为一步之差就收刀举弓。
        CombatStance committed = POLICY.choose(both, 5.0D, true);
        require(
                committed.posture() == CombatStance.Posture.MELEE,
                "Her stance flipped at the same distance, which is the "
                        + "back-away-and-swing-at-nothing loop"
        );
    }

    /**
     * 没箭的弓不是武器。
     *
     * <p>这条单独钉住，是因为"有弓"和"能射"在背包里长得一模一样，
     * 漏掉弹药判断的表现是她举着空弓站在苦力怕面前。
     */
    private static void verifiesEmptyBowIsNotAWeapon() {
        CombatStance stance = POLICY.choose(
                List.of(sword(0.4D), bow(0.9D, false)),
                12.0D, false);
        require(
                stance.posture() == CombatStance.Posture.MELEE,
                "An empty bow was chosen over a sword"
        );
        require(
                stance.weapon().kind() == WeaponKind.MELEE,
                "The chosen weapon was not the sword"
        );
    }

    /** 只有弓却被贴脸，仍然要打——糟糕的选择好过没有选择。 */
    private static void verifiesBowOnlyMaidStillFightsUpClose() {
        CombatStance stance =
                POLICY.choose(List.of(bow(0.6D, true)), 1.0D, false);
        require(
                stance.engaged(),
                "A cornered archer refused to fight at all"
        );
        require(
                stance.posture() == CombatStance.Posture.RANGED,
                "A bow-only maid was told to melee with a bow"
        );
    }

    /** 手上那把要占优势，否则她会在两把差不多的剑之间来回翻包。 */
    private static void verifiesHeldWeaponResistsSwapping() {
        WeaponCandidate held = new WeaponCandidate(
                WeaponKind.MELEE, WeaponCandidate.MAIN_HAND, 0.50D, false
        );
        WeaponCandidate packed = new WeaponCandidate(
                WeaponKind.MELEE, 3, 0.55D, false
        );
        CombatStance stance =
                POLICY.choose(List.of(held, packed), 1.0D, true);
        require(
                stance.weapon().inHand(),
                "She swapped weapons for a negligible gain"
        );

        WeaponCandidate clearlyBetter = new WeaponCandidate(
                WeaponKind.MELEE, 3, 0.95D, false
        );
        CombatStance upgraded =
                POLICY.choose(List.of(held, clearlyBetter), 1.0D, true);
        require(
                !upgraded.weapon().inHand(),
                "She refused a clearly better weapon"
        );
    }

    private static void verifiesNothingUsableDisengages() {
        CombatStance stance =
                POLICY.choose(List.of(bow(0.9D, false)), 6.0D, false);
        require(
                !stance.engaged(),
                "With nothing usable she still tried to fight"
        );
        require(
                stance.weapon() == null,
                "A disengaging stance named a weapon"
        );
    }

    /** 收武器要等安静下来，不是等这一只死掉——怪是成波来的。 */
    private static void verifiesStowingWaitsForQuiet() {
        WeaponStowPolicy stow = WeaponStowPolicy.INSTANCE;
        require(
                !stow.shouldStow(stow.calmTicks() + 40, true, true),
                "She packed her sword with a threat still nearby"
        );
        require(
                !stow.shouldStow(stow.calmTicks() - 1, false, true),
                "She packed her sword before the world went quiet"
        );
        require(
                stow.shouldStow(stow.calmTicks(), false, true),
                "She stayed armed long after the fight"
        );
        require(
                !stow.shouldStow(stow.calmTicks() * 10, false, false),
                "Empty hands were treated as something to put away"
        );
    }

    private static WeaponCandidate sword(double power) {
        return new WeaponCandidate(WeaponKind.MELEE, 1, power, false);
    }

    private static WeaponCandidate bow(double power, boolean arrows) {
        return new WeaponCandidate(WeaponKind.BOW, 2, power, arrows);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
