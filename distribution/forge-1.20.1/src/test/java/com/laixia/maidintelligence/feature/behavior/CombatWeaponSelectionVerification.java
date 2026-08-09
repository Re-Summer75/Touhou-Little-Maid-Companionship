package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatStance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementContext;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponKind;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponSelectionPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponStowPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;

import java.util.ArrayList;
import java.util.List;

/**
 * 武器选择：同一套算术，在不同处境下给出不同答案。
 *
 * <p>没有一条断言提到"贴脸距离"或者"优先远程"。这些曾经是写死的规则，现在
 * 是定价的结果——所以这里能问的问题变了：不再是"她有没有照规则走"，而是
 * "换一个处境，她会不会自己改主意"。凡是只靠一条阈值就能通过的断言，都说明
 * 那个处境没有被真正验证到。
 */
public final class CombatWeaponSelectionVerification {
    private static final WeaponSelectionPolicy POLICY =
            WeaponSelectionPolicy.instance();

    /** 她的挥击频率，铁剑水平。 */
    private static final double SWINGS = 1.6D;

    /** 她的射击频率，一发约一秒——也就是一次拉弓的时长。 */
    private static final double SHOTS = 1.0D;

    /** 她自己的近战触及，用于聚合威胁场。 */
    private static final double HER_REACH = 3.0D;

    /** 普通怪走向她的速度，约等于僵尸的步行速度。 */
    private static final double CLOSING_SPEED = 1.0D;

    /** 站着不动的敌人：不会到达，因此不构成时间压力。 */
    private static final double STATIONARY = 0.0D;

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
        verifiesAFlierIsShotNotSwungAt();
        verifiesACrowdDrawsSteelAtBowRange();
        verifiesNoRoomToKiteDrawsSteel();
        verifiesAnArrivingFoeIsMetWithSteel();
        verifiesLongerReachCostsLess();
        verifiesStowingWaitsForQuiet();
        System.out.println("Combat weapon selection verification passed.");
    }

    /** 有弓有箭且够不着她，就不该走过去拿剑砍。 */
    private static void verifiesRangePreferredWhenNotPressed() {
        CombatStance stance = POLICY.choose(
                List.of(sword(0.8D), bow(0.5D, true)),
                against(zombie(10.0D))
        );
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
     * 被贴脸时弓没有用，哪怕它评分高得多。
     *
     * <p>理由不再是"距离小于四格"，而是拉弓拉不完：僵尸每秒打一下，一次拉弓
     * 正好一秒，于是她永远在起手、永远被打断。写成算术之后这条规则自己会推广
     * ——打得慢的东西留得出空隙，打得快的留不出。
     */
    private static void verifiesMeleeTakesOverWhenPressed() {
        CombatStance stance = POLICY.choose(
                List.of(sword(0.3D), bow(0.9D, true)),
                against(zombie(1.5D))
        );
        require(
                stance.posture() == CombatStance.Posture.MELEE,
                "She kept aiming a bow at something already on top of her"
        );
    }

    /**
     * 同一个距离不能因为她当前拿什么就换个答案——除非那个答案更稳。
     *
     * <p>玩家看到的症状是"近处又退又想砍、砍不着"。滞回现在不是一个常数：
     * 已经在近战里的她要改走远程，得先走回真正能射的距离，而站在远处继续射
     * 一步都不用走。这个不对称本身就是滞回。
     */
    private static void verifiesStanceDoesNotFlipOnTheThreshold() {
        List<WeaponCandidate> both = List.of(sword(0.8D), bow(0.5D, true));
        CombatStance fresh = POLICY.choose(both, against(zombie(5.0D)));
        require(
                fresh.posture() == CombatStance.Posture.RANGED,
                "Standing clear at five blocks she still chose melee"
        );
        CombatStance committed = POLICY.choose(
                both, committed(zombie(5.0D))
        );
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
                against(zombie(12.0D))
        );
        require(
                stance.posture() == CombatStance.Posture.MELEE,
                "An empty bow was chosen over a sword"
        );
        require(
                stance.weapon().kind() == WeaponKind.MELEE,
                "The chosen weapon was not the sword"
        );
    }

    /**
     * 只有弓却被贴脸，仍然要打——糟糕的选择好过没有选择。
     *
     * <p>此时每个选项的定价都是无穷：拉不完弓，也没有别的东西可用。定价不能
     * 决定的事，兜底来决定，否则她会举着唯一的武器站着不动。
     */
    private static void verifiesBowOnlyMaidStillFightsUpClose() {
        CombatStance stance =
                POLICY.choose(List.of(bow(0.6D, true)), against(zombie(1.0D)));
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
                POLICY.choose(List.of(held, packed), committed(zombie(1.0D)));
        require(
                stance.weapon().inHand(),
                "She swapped weapons for a negligible gain"
        );

        WeaponCandidate clearlyBetter = new WeaponCandidate(
                WeaponKind.MELEE, 3, 0.95D, false
        );
        CombatStance upgraded = POLICY.choose(
                List.of(held, clearlyBetter), committed(zombie(1.0D))
        );
        require(
                !upgraded.weapon().inHand(),
                "She refused a clearly better weapon"
        );
    }

    private static void verifiesNothingUsableDisengages() {
        CombatStance stance =
                POLICY.choose(List.of(bow(0.9D, false)), against(zombie(6.0D)));
        require(
                !stance.engaged(),
                "With nothing usable she still tried to fight"
        );
        require(
                stance.weapon() == null,
                "A disengaging stance named a weapon"
        );
    }

    /**
     * 飞在天上的东西只能射，哪怕剑好得多。
     *
     * <p>这条是"死板"最直白的那个后果：旧规则只看距离，于是幻翼俯冲到六格
     * 她就拔剑，然后对着头顶挥空气。代码里没有一处提到幻翼——挥不到就是挥
     * 不到，定价成无穷，剩下的自然只有弓。
     *
     * <p>两头都要断言：同样的数字落到地面上就该拔剑，否则把"永远选弓"写死
     * 也能通过。
     */
    private static void verifiesAFlierIsShotNotSwungAt() {
        ThreatSample flier = new ThreatSample(
                6.0D, 4.0D, 2.0D, 60, 20.0D, true, CLOSING_SPEED, ThreatRelation.UNENGAGED
        );
        CombatStance air = POLICY.choose(
                List.of(sword(0.9D), bow(0.4D, true)), against(flier)
        );
        require(
                air.posture() == CombatStance.Posture.RANGED,
                "She drew a sword on something out of reach overhead"
        );

        ThreatSample grounded = new ThreatSample(
                6.0D, 4.0D, 2.0D, 60, 20.0D, false, CLOSING_SPEED, ThreatRelation.UNENGAGED
        );
        CombatStance ground = POLICY.choose(
                List.of(sword(0.9D), bow(0.4D, true)), against(grounded)
        );
        require(
                ground.posture() == CombatStance.Posture.MELEE,
                "The same fight on the ground still chose the weaker bow, so "
                        + "the flier answer was not about being airborne"
        );
    }

    /**
     * 孤身时举弓的那个距离，被围住时该拔刀。
     *
     * <p>没有任何一条规则写着"人多就近战"。多出来的两只只是让挨打的间隔
     * 缩短，而拉弓需要那个间隔——围殴之所以要动刀，是因为在围殴里根本拉不完
     * 一次弓。同一个距离、同一套武器，只有周围的敌人数量不同。
     */
    private static void verifiesACrowdDrawsSteelAtBowRange() {
        List<WeaponCandidate> both = List.of(sword(0.8D), bow(0.5D, true));
        require(
                POLICY.choose(both, against(zombie(5.0D))).posture()
                        == CombatStance.Posture.RANGED,
                "Fixture is wrong: alone at five blocks she should shoot"
        );
        CombatStance mobbed = POLICY.choose(
                both,
                against(zombie(5.0D), zombie(2.0D), zombie(2.0D))
        );
        require(
                mobbed.posture() == CombatStance.Posture.MELEE,
                "Surrounded, she still tried to work a bow"
        );
    }

    /**
     * 退无可退就别想着拉扯。
     *
     * <p>身后是墙的时候，"保持距离"这个选项并不存在，射击就只是拿着更差的
     * 武器打近战。地形本来就被测量着，只是从来没有送到武器选择这里。
     */
    private static void verifiesNoRoomToKiteDrawsSteel() {
        List<WeaponCandidate> both = List.of(sword(0.8D), bow(0.5D, true));
        CombatStance cornered = POLICY.choose(
                both, cornered(zombie(5.0D))
        );
        require(
                cornered.posture() == CombatStance.Posture.MELEE,
                "With a wall behind her she still planned to keep her distance"
        );
    }

    /**
     * 会赶到的东西要先拔刀，不能等它打到脸上再换。
     *
     * <p>这是"预测"落到实处的那一条。同样是六格外的僵尸、同样一套武器，唯一
     * 的差别是它在不在走过来：站着不动的那只她从容开弓，正在逼近的那只她提前
     * 拔刀——因为拉一次弓要一秒，而它零点几秒就到，那一箭注定放不完。
     *
     * <p>旧判据只问"它现在够不够得着我"，答案翻面的那一刻它已经在打她了，于是
     * 她永远慢一个交换。距离一样、装备一样而结论相反，正是"她开始预判"的可执行
     * 形式。
     */
    private static void verifiesAnArrivingFoeIsMetWithSteel() {
        List<WeaponCandidate> both = List.of(sword(0.6D), bow(0.6D, true));
        ThreatSample idle = new ThreatSample(
                6.0D, 3.0D, 2.4D, 20, 20.0D, false, STATIONARY,
                ThreatRelation.UNENGAGED
        );
        require(
                POLICY.choose(both, against(idle)).posture()
                        == CombatStance.Posture.RANGED,
                "对着一只站着不动的敌人她都不肯开弓，那这条测的就不是逼近"
        );

        ThreatSample charging = new ThreatSample(
                6.0D, 3.0D, 2.4D, 20, 20.0D, false, 6.0D,
                ThreatRelation.UNENGAGED
        );
        require(
                POLICY.choose(both, against(charging)).posture()
                        == CombatStance.Posture.MELEE,
                "有东西正冲过来、一次拉弓根本来不及，她还在举弓"
        );
    }

    /** 收武器要等安静下来，不是等这一只死掉——怪是成波来的。 */
    /**
     * 够得更远的近战武器，代价更低。
     *
     * <p>攻击距离是 Forge 的 {@code ENTITY_REACH} 属性，物品可以给它加修饰符——
     * 玩家的长柄武器就是这么变长的。此前近战候选的触及一律报 0，于是背包里的
     * 长矛和匕首在定价里没有任何区别；她走到"能被对方打到"的距离才停，把长武器
     * 唯一的好处丢掉了。
     *
     * <p>断言只比两把**其它属性完全相同**的武器，所以差别只可能来自触及。
     */
    private static void verifiesLongerReachCostsLess() {
        WeaponCandidate longArm = polearm(0.6D, 5.0D);
        CombatStance stance = POLICY.choose(
                List.of(sword(0.6D), longArm),
                against(zombie(6.0D))
        );
        require(
                stance.weapon() == longArm,
                "两把伤害相同的近战武器里她挑了够得更近的那把，"
                        + "长柄武器的距离优势没有进入决定"
        );
    }

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

    /** 普通僵尸：三点伤害、每秒一下、够到两格半。 */
    private static ThreatSample zombie(double distance) {
        return new ThreatSample(
                distance, 3.0D, 2.4D, 20, 20.0D, false,
                CLOSING_SPEED, ThreatRelation.UNENGAGED
        );
    }

    /** 有地可退、还没动刀时的处境；额外参数是同时在场的其它敌人。 */
    private static EngagementContext against(
            ThreatSample target,
            ThreatSample... others
    ) {
        return context(target, true, false, others);
    }

    /** 已经在近战里的同一处境。 */
    private static EngagementContext committed(ThreatSample target) {
        return context(target, true, true);
    }

    /** 身后无路可退的同一处境。 */
    private static EngagementContext cornered(ThreatSample target) {
        return context(target, false, false);
    }

    private static EngagementContext context(
            ThreatSample target,
            boolean canOpenGround,
            boolean holdingMelee,
            ThreatSample... others
    ) {
        List<ThreatSample> all = new ArrayList<>();
        all.add(target);
        for (ThreatSample other : others) {
            all.add(other);
        }
        ThreatField field = ThreatField.of(all, HER_REACH);
        return EngagementContext.of(
                target, field, SWINGS, SHOTS, canOpenGround, holdingMelee
        );
    }

    private static WeaponCandidate sword(double power) {
        return new WeaponCandidate(WeaponKind.MELEE, 1, power, false);
    }

    /** 同样的一把近战武器，但够得更远。 */
    private static WeaponCandidate polearm(double power, double reach) {
        return new WeaponCandidate(WeaponKind.MELEE, 3, power, false, reach);
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
