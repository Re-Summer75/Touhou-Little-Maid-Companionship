package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatStance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementContext;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.TradeCost;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponKind;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponSelectionPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;

import java.util.ArrayList;
import java.util.List;

/**
 * 人多的时候，同一套定价该给出不同的答案。
 *
 * <p>与武器选择那一组分开，是因为问的东西不一样：那边问"这个处境她挑哪一把"，
 * 这边问"旁边多站几个，同一个决定该贵多少"。前者的夹具只需要一只敌人，后者的
 * 每一条都必须造出一群，而"造出一群"本身就容易造错——距离差半格，`pressing`
 * 就从两个人变成一个人，断言照样绿，只是什么都没测到。所以这里的每一条都先
 * 断言夹具确实成立。
 *
 * <p>聚合会抹掉方位，这是它的代价。整个威胁场只报总量：几个、多少伤害、多少
 * 血。于是"左边那只马上够得到我"这件事没有地方落脚，而她被侧面打中恰恰是因为
 * 决定是对着正面那只做的。{@code pressing} 就是补回来的那个微观事实，这一组
 * 是它唯一的验证。
 */
public final class CombatCrowdPricingVerification {
    private static final WeaponSelectionPolicy POLICY =
            WeaponSelectionPolicy.instance();

    /** 与策略同一套定价，用于直接比较两个处境下同一把武器的代价。 */
    private static final TradeCost COST =
            new TradeCost(WeaponSelectionPolicy.instance().preferredRange());

    private static final double SWINGS = 1.6D;
    private static final double SHOTS = 1.0D;
    private static final double HER_REACH = 3.0D;
    private static final double CLOSING_SPEED = 1.0D;

    /** 铁剑的攻击力修饰符，以及横扫落在每个旁人身上的一点。 */
    private static final double IRON_SWORD = 5.0D;
    private static final double SWEEP_DAMAGE = 1.0D;

    /** 铁斧：一下重得多，但每秒只挥 0.9 下，而且不横扫。 */
    private static final double IRON_AXE = 8.0D;
    private static final double AXE_SWINGS = 0.9D;

    private static final double MELEE_REACH = 2.0D;
    private static final double BOW_POWER = 0.65D;
    private static final double BOW_RANGE = 15.0D;

    private CombatCrowdPricingVerification() {
    }

    public static void main(String[] args) {
        verifiesACrowdDrawsSteelAtBowRange();
        verifiesAnArcBeatsWeightInACrowd();
        verifiesWalkingIntoACrowdIsNotFree();
        System.out.println("Combat crowd pricing verification passed.");
    }

    /**
     * 孤身时举弓的那个距离，被围住时该拔刀。
     *
     * <p>没有任何一条规则写着"人多就近战"。多出来的两只只是让挨打的间隔缩短，
     * 而拉弓需要那个间隔——围殴之所以要动刀，是因为在围殴里根本拉不完一次弓。
     * 同一个距离、同一套武器，只有周围的敌人数量不同。
     */
    private static void verifiesACrowdDrawsSteelAtBowRange() {
        List<WeaponCandidate> both = List.of(sword(IRON_SWORD), bow());
        require(
                POLICY.choose(both, against(zombie(5.0D))).posture()
                        == CombatStance.Posture.RANGED,
                "孤身一只时她都不肯开弓，那这条测的就不是人多"
        );
        require(
                POLICY.choose(both, against(zombie(1.5D), zombie(1.6D),
                        zombie(1.8D))).posture()
                        == CombatStance.Posture.MELEE,
                "被三只围着她还在拉弓，而那一箭永远放不出去"
        );
    }

    /**
     * 一堆敌人里，横扫比单下重要。
     *
     * <p>斧头的优势是每下重，剑的优势是一下打好几个，所以哪把好取决于旁边站着
     * 几个，而不取决于哪把"更强"。这条钉住的是后者不该赢的那一半。
     */
    private static void verifiesAnArcBeatsWeightInACrowd() {
        ThreatSample front = zombie(2.5D);
        CombatStance stance = POLICY.choose(
                List.of(sword(IRON_SWORD), axe(IRON_AXE)),
                against(front, zombie(2.6D), zombie(2.7D),
                        zombie(2.8D), zombie(2.9D), zombie(3.0D))
        );
        require(
                stance.weapon().arcDamage() > 0.0D,
                "六只挤在一起她挑了不横扫的斧头，一下只打一个"
        );
    }

    /**
     * 走过去这件事，在被包围时不是免费的。
     *
     * <p>定价一直假设"她在靠近的时候对方也够不着她"，这话一对一时成立，人多时
     * 不成立——走过去就是走进第二双手里，而她为之走过去的那一下，无论落没落上
     * 都已经付过账了。
     *
     * <p>只比同一把武器的两个处境，所以差别只可能来自旁边站了几个。第一条断言
     * 是防空跑的：`pressing` 用的是"再走一步就够得到"，夹具里两只的距离必须真
     * 的落进去，否则这条会以全绿的样子什么都不测。
     */
    private static void verifiesWalkingIntoACrowdIsNotFree() {
        ThreatSample front = zombie(4.0D);
        ThreatField lonely = ThreatField.of(List.of(front), HER_REACH);
        ThreatField swarmed = ThreatField.of(
                List.of(front, zombie(2.0D), zombie(2.2D)), HER_REACH
        );
        require(
                lonely.pressing() <= 1 && swarmed.pressing() > 1,
                "夹具没有真的造出'被包围'，这条测不到东西：孤身 "
                        + lonely.pressing() + " 人、成群 "
                        + swarmed.pressing() + " 人"
        );
        require(
                COST.of(sword(IRON_SWORD), contextOf(front, swarmed))
                        > COST.of(sword(IRON_SWORD), contextOf(front, lonely)),
                "身边多了两只够得着她的敌人，走过去砍同一个目标却一样便宜"
        );
    }

    /** 普通僵尸：三点伤害、每秒一下、够到两格半。 */
    private static ThreatSample zombie(double distance) {
        return new ThreatSample(
                distance, 3.0D, 2.4D, 20, 20.0D, false,
                CLOSING_SPEED, ThreatRelation.UNENGAGED
        );
    }

    private static EngagementContext against(
            ThreatSample target,
            ThreatSample... others
    ) {
        List<ThreatSample> all = new ArrayList<>();
        all.add(target);
        for (ThreatSample other : others) {
            all.add(other);
        }
        return contextOf(target, ThreatField.of(all, HER_REACH));
    }

    /** 只换威胁场、不换目标的处境，用来做同类比较。 */
    private static EngagementContext contextOf(
            ThreatSample target,
            ThreatField field
    ) {
        return EngagementContext.of(
                target, field, SWINGS, SHOTS, true, false
        );
    }

    private static WeaponCandidate sword(double damage) {
        return new WeaponCandidate(
                WeaponKind.MELEE, 1, damage / WeaponCandidate.POWER_SCALE,
                false, MELEE_REACH, SWEEP_DAMAGE, SWINGS, 1.0D,
                WeaponCandidate.UNLIMITED
        );
    }

    private static WeaponCandidate axe(double damage) {
        return new WeaponCandidate(
                WeaponKind.MELEE, 4, damage / WeaponCandidate.POWER_SCALE,
                false, MELEE_REACH, 0.0D, AXE_SWINGS, 1.0D,
                WeaponCandidate.UNLIMITED
        );
    }

    private static WeaponCandidate bow() {
        return new WeaponCandidate(
                WeaponKind.BOW, 2, BOW_POWER, true, BOW_RANGE,
                0.0D, 0.0D, 1.0D, WeaponCandidate.UNLIMITED
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
