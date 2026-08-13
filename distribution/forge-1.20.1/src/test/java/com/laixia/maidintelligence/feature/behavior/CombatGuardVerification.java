package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementContext;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementRiskPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.RiskVerdict;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.TradeCost;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponKind;

import java.util.List;

/**
 * 盾牌：它改变的是**她愿意站在哪里**，不是她挥得多快。
 *
 * <p>这一套断言全部围绕一件事——格挡在 1.20.1 里是整下抵消，所以它进的是
 * "承受伤害"那一侧，而不是护甲那一侧。两个后果必须同时成立：带盾她敢接一场
 * 空手会退的仗，以及**盾只让近战便宜**。第二条是整个设计的支点：副手一次只
 * 拿一样东西，举着盾就拉不了弓，所以"她带着盾"不等于"她到处都更安全"。
 *
 * <p>还有一条回归性质的：`guardedShare` 为零时每一个数必须与从前完全一致。
 * 这个字段是加进既有记录里的，而既有的判据全部是在没有盾的前提下量出来的——
 * 一旦零值不再是恒等元，之前所有实测读数就都作废了。
 */
public final class CombatGuardVerification {
    private static final EngagementRiskPolicy POLICY =
            EngagementRiskPolicy.instance();

    private static final double MELEE_REACH = 3.0D;

    private static final double CLOSING_SPEED = 1.0D;

    private static final double SWINGS = 1.6D;

    private static final double SHOTS = 1.0D;

    private CombatGuardVerification() {
    }

    public static void main(String[] args) {
        verifiesAGuardMakesALosingTradeWorthTaking();
        verifiesNoGuardIsTheIdentity();
        verifiesTheGuardOnlyPaysForSteel();
        verifiesAFullGuardStillNeedsAWeapon();
        verifiesShareMustBeAShare();
        System.out.println("Combat guard verification passed.");
    }

    /**
     * 同一群敌人、同一副装备，只多一面盾，答案就该翻面。
     *
     * <p>这正是这套战斗代码反复声明的性质："同一群敌人穿钻石值得冲、穿围裙
     * 值得逃，一条规则给出两个答案。"盾是这句话里的又一件装备，不是一个新分支。
     */
    private static void verifiesAGuardMakesALosingTradeWorthTaking() {
        ThreatField pair = ThreatField.of(
                List.of(heavy(2.0D), heavy(2.2D)), MELEE_REACH
        );
        CombatCapability bare =
                new CombatCapability(40.0D, 10.0D, 0.0D, MELEE_REACH);
        CombatCapability guarded =
                new CombatCapability(40.0D, 10.0D, 0.0D, MELEE_REACH, 0.5D);

        require(
                POLICY.assess(pair, bare, 1.0D, true) != RiskVerdict.ENGAGE,
                "Without a guard she was already willing to trade with two"
        );
        require(
                POLICY.assess(pair, guarded, 1.0D, true) == RiskVerdict.ENGAGE,
                "A raised guard did not make the same exchange worth taking"
        );
    }

    /**
     * 没有盾的时候，一个数都不许动。
     *
     * <p>四参构造是加字段之前的全部调用形式，而所有已记录的实测读数都产自它。
     * 若它不再等价于"零盾"，{@code docs/combat/tactics-log.md} 里的每一条基线
     * 就都失去了意义——那比这个特性本身值钱得多。
     */
    private static void verifiesNoGuardIsTheIdentity() {
        CombatCapability legacy =
                new CombatCapability(20.0D, 6.0D, 0.0D, MELEE_REACH);
        require(
                legacy.guardedShare() == 0.0D,
                "The four-argument capability did not mean an unguarded maid"
        );
        CombatCapability spelled =
                new CombatCapability(20.0D, 6.0D, 0.0D, MELEE_REACH, 0.0D);
        require(legacy.equals(spelled), "Zero guard was not the identity");

        ThreatField crowd = ThreatField.of(
                List.of(zombie(2.0D), zombie(2.5D), zombie(3.0D)), MELEE_REACH
        );
        require(
                POLICY.assess(crowd, legacy, 1.0D, true)
                        == POLICY.assess(crowd, spelled, 1.0D, true),
                "Stating the absent guard changed the verdict"
        );
    }

    /**
     * 盾让刀变便宜，不让弓变便宜。
     *
     * <p>副手只有一个 {@code useItem} 槽：举着盾就拉不了弓。所以带盾的收益只能
     * 在近战那一侧兑现，而这正是"她为什么会走上去打"的算术来源——不是因为有人
     * 写了"有盾就近战"，是因为带盾的近战确实更便宜。
     *
     * <p>反过来同样要钉住：远程的价钱一分不能动。动了就等于宣称她能一边举盾
     * 一边放箭，而那在游戏里做不到。
     */
    private static void verifiesTheGuardOnlyPaysForSteel() {
        ThreatSample target = zombie(4.0D);
        EngagementContext unguarded = context(target, 0.0D);
        EngagementContext guarded = context(target, 0.5D);

        double steelBare = TradeCost.INSTANCE.of(sword(), unguarded);
        double steelGuarded = TradeCost.INSTANCE.of(sword(), guarded);
        require(
                Double.isFinite(steelBare) && Double.isFinite(steelGuarded),
                "Pricing a sword against a zombie produced no answer"
        );
        require(
                steelGuarded < steelBare,
                "A guard did not make steel cheaper: " + steelGuarded
                        + " against " + steelBare
        );

        double bowBare = TradeCost.INSTANCE.of(bow(), unguarded);
        double bowGuarded = TradeCost.INSTANCE.of(bow(), guarded);
        require(
                bowGuarded == bowBare,
                "A guard changed what shooting costs, which would mean she can "
                        + "block and draw with the same hand: " + bowGuarded
                        + " against " + bowBare
        );
    }

    /**
     * 一面盾不是武器。
     *
     * <p>满格挡也不能让空手的她去接一场仗——她挡得住，但打不死任何东西，那不是
     * 一场仗，是一次很慢的死亡。{@code armed()} 这道门在盾之前之后必须一样。
     */
    private static void verifiesAFullGuardStillNeedsAWeapon() {
        CombatCapability shieldOnly =
                new CombatCapability(20.0D, 0.0D, 0.0D, MELEE_REACH, 1.0D);
        require(!shieldOnly.armed(), "A shield counted as a weapon");
        require(
                POLICY.assess(
                        ThreatField.of(List.of(zombie(2.0D)), MELEE_REACH),
                        shieldOnly, 1.0D, true
                ) == RiskVerdict.WITHDRAW,
                "Holding only a shield she was told to fight"
        );
    }

    /** 份额就是份额：越界的输入要当场炸，不许悄悄夹到边界上。 */
    private static void verifiesShareMustBeAShare() {
        rejects(() -> new CombatCapability(
                20.0D, 6.0D, 0.0D, MELEE_REACH, 1.5D
        ), "A guard denying more than everything was accepted");
        rejects(() -> new CombatCapability(
                20.0D, 6.0D, 0.0D, MELEE_REACH, -0.1D
        ), "A negative guard was accepted");
        rejects(
                () -> context(zombie(4.0D), 2.0D),
                "An engagement context took a guard share above one"
        );
    }

    /** 每击九点的重手近战：一个不至于要命，两个就要算了。 */
    private static ThreatSample heavy(double distance) {
        return new ThreatSample(
                distance, 9.0D, 2.5D, 20, 24.0D, false, CLOSING_SPEED,
                ThreatRelation.ATTACKING_MAID
        );
    }

    private static ThreatSample zombie(double distance) {
        return new ThreatSample(
                distance, 4.0D, 2.5D, 20, 20.0D, false, CLOSING_SPEED,
                ThreatRelation.ATTACKING_MAID
        );
    }

    private static EngagementContext context(
            ThreatSample target,
            double guardedShare
    ) {
        ThreatField field = ThreatField.of(List.of(target), MELEE_REACH);
        return EngagementContext.of(
                target, field, SWINGS, SHOTS, true, true, guardedShare
        );
    }

    private static WeaponCandidate sword() {
        return new WeaponCandidate(
                WeaponKind.MELEE, 1, 7.0D / WeaponCandidate.POWER_SCALE,
                false, MELEE_REACH, 0.0D, SWINGS, 1.0D,
                WeaponCandidate.UNLIMITED
        );
    }

    private static WeaponCandidate bow() {
        return new WeaponCandidate(
                WeaponKind.BOW, 2, 6.0D / WeaponCandidate.POWER_SCALE,
                true, 15.0D, 0.0D, SHOTS, 1.0D, 64
        );
    }

    private static void rejects(Runnable construction, String message) {
        try {
            construction.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
