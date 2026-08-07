package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementRiskPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.RiskVerdict;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.TargetSelectionPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;

import java.util.List;

/**
 * 风险评估：一群怪聚合成态势，再与她自己的能力对比。
 *
 * <p>没有一条断言提到生物种类。同一群敌人在不同装备下应当得出相反结论，
 * 这正是"不能写死"的可执行形式。
 */
public final class CombatRiskAssessmentVerification {
    private static final EngagementRiskPolicy POLICY =
            EngagementRiskPolicy.INSTANCE;
    private static final double MELEE_REACH = 3.0D;

    private CombatRiskAssessmentVerification() {
    }

    public static void main(String[] args) {
        verifiesGoodArmourDoesNotBuyACrowd();
        verifiesCrowdOnlyCountsWhatCanReachHer();
        verifiesClosingHostilesCountBeforeTheyArrive();
        verifiesSameCrowdFlipsWithHerGear();
        verifiesDensityDecidesNotHeadcount();
        verifiesRangedAnswerNeedsThemToBeMelee();
        verifiesWoundedMaidDeclinesAWinnableTrade();
        verifiesAnOrdinarySwordFightIsWorthTaking();
        verifiesHerOwnOutputIsNotUnderstated();
        verifiesAHordeIsStillAHorde();
        verifiesUnarmedNeverEngages();
        verifiesOwnerAttackerOutranksHerOwn();
        System.out.println("Combat risk assessment verification passed.");
    }

    /**
     * 墙那边的一群怪不该把她吓跑。
     *
     * <p>承伤只累计"此刻够得到她"的敌人；把整张地图的 DPS 相加会让她从
     * 一个安全的房间里逃出去。
     */
    private static void verifiesCrowdOnlyCountsWhatCanReachHer() {
        ThreatField far = ThreatField.of(
                List.of(zombie(30.0D), zombie(30.0D), zombie(30.0D)),
                MELEE_REACH
        );
        require(far.total() == 3, "Distant hostiles vanished from the count");
        require(
                far.converging() == 0,
                "Hostiles out of reach were counted as converging"
        );
        require(
                far.incomingDps() == 0.0D,
                "Damage was attributed to hostiles that cannot reach her"
        );
    }

    /**
     * 正在逼近的敌人，在够到她之前就该计入。
     *
     * <p>两个场景此刻够得到她的数量完全相同，只有远处那几只的距离不同。
     * 若距离在裁决中缺席，两者会得出同一个结论——她安心开打，
     * 一秒后被三只围上。
     */
    private static void verifiesClosingHostilesCountBeforeTheyArrive() {
        CombatCapability capability =
                new CombatCapability(30.0D, 6.0D, 0.0D, MELEE_REACH);
        ThreatField closing = ThreatField.of(
                List.of(zombie(2.0D), zombie(5.0D), zombie(5.0D), zombie(5.0D)),
                MELEE_REACH
        );
        ThreatField distant = ThreatField.of(
                List.of(zombie(2.0D), zombie(30.0D), zombie(30.0D),
                        zombie(30.0D)),
                MELEE_REACH
        );

        require(
                closing.converging() == distant.converging(),
                "The two scenes should have the same hostiles in reach"
        );
        require(
                closing.incomingDps() > distant.incomingDps(),
                "Hostiles closing in were not counted before they arrived"
        );
        require(
                POLICY.assess(distant, capability, 1.0D) == RiskVerdict.ENGAGE,
                "She declined a single zombie because of three far-off ones"
        );
        require(
                POLICY.assess(closing, capability, 1.0D) != RiskVerdict.ENGAGE,
                "She committed to a fight three more were about to join"
        );
    }

    /** 同一群敌人，装备决定结论——这正是不能写死的地方。 */
    private static void verifiesSameCrowdFlipsWithHerGear() {
        ThreatField crowd = ThreatField.of(
                List.of(zombie(1.5D), zombie(1.5D), zombie(2.0D)),
                MELEE_REACH
        );

        CombatCapability geared =
                new CombatCapability(120.0D, 14.0D, 0.0D, MELEE_REACH);
        require(
                POLICY.assess(crowd, geared, 1.0D) == RiskVerdict.ENGAGE,
                "A well equipped maid refused a fight she wins comfortably"
        );

        CombatCapability flimsy =
                new CombatCapability(20.0D, 1.5D, 0.0D, MELEE_REACH);
        require(
                POLICY.assess(crowd, flimsy, 1.0D) != RiskVerdict.ENGAGE,
                "An underequipped maid walked into a fight she loses"
        );
    }

    /**
     * 拿着铁剑打两只僵尸，是该打的仗。
     *
     * <p>玩家报的是"近战只会后退，明明够得着也不打"。旧算法把"赢"定义成
     * 清场：两只僵尸四十点血除以铁剑每秒六点，再乘保守系数，得出十秒多，
     * 而生存时间只有三秒——于是判定必败，转身就退。
     *
     * <p>实际上她不需要清场，放倒眼前这个局面就变了；而且挥中会击退，被
     * 推开的那零点几秒对方打不到她。两者都算进去，这场仗才回到它本来的
     * 样子。
     */
    /** 铁剑每秒挥一点六刀，每刀六点；二十点血，无甲——最普通的一身装备。 */
    private static final double SWORD_SWINGS_PER_SECOND = 1.6D;

    private static final CombatCapability IRON_SWORD = new CombatCapability(
            20.0D, 6.0D * SWORD_SWINGS_PER_SECOND, 0.0D, MELEE_REACH
    );

    private static void verifiesAnOrdinarySwordFightIsWorthTaking() {
        ThreatField pair = ThreatField.of(
                List.of(zombie(1.5D), zombie(2.0D)),
                MELEE_REACH
        );
        require(
                POLICY.assess(pair, IRON_SWORD, 1.0D) == RiskVerdict.ENGAGE,
                "Two zombies made an iron-sword maid turn and run, which is "
                        + "the retreat-instead-of-swing the players saw"
        );
    }

    /**
     * 低估自己的输出，和高估一样危险——只是危险的方向不同。
     *
     * <p>她的近战 dps 曾按"每秒一刀"折算，而铁剑每秒挥一点六刀。风险裁决据此
     * 认定的是一个输出打了六折的女仆，于是在她本该留下的架里判她撤退。这条把
     * 真实攻速和打折攻速摆在同一群怪面前，要求两者给出不同的答案。
     */
    private static void verifiesHerOwnOutputIsNotUnderstated() {
        ThreatField pair = ThreatField.of(
                List.of(zombie(1.5D), zombie(2.0D)),
                MELEE_REACH
        );
        CombatCapability understated = new CombatCapability(
                IRON_SWORD.effectiveHealth(),
                IRON_SWORD.meleeDps() / SWORD_SWINGS_PER_SECOND,
                0.0D,
                MELEE_REACH
        );
        require(
                POLICY.assess(pair, understated, 1.0D) != RiskVerdict.ENGAGE,
                "The understated maid was still told to engage, so this test "
                        + "cannot tell the two apart"
        );
        require(
                POLICY.assess(pair, IRON_SWORD, 1.0D) == RiskVerdict.ENGAGE,
                "Rated at her real swing speed she still refused a fight she "
                        + "wins comfortably"
        );
    }

    /**
     * 但一群仍然是一群——不能因此变成莽夫。
     *
     * <p>上一条放宽了交战的门槛，这一条守住另一端：击退只压得住正在挨打
     * 的那一个，人多起来该退还是要退。少了它，"更愿意打"会一路滑成"什么
     * 都敢打"。
     */
    /**
     * 一身好装备不该让她敢站在人堆里。
     *
     * <p>玩家看到的是"在一群怪下近战硬抗到死"。代价一度只按"放倒最近那一个"
     * 计算，于是十只和一只报出同一个入场价，而挨打是十份一起来的——护甲越好，
     * 这个错得越彻底：多出来的血刚好够她把那个假的入场价付掉，然后死在真正的
     * 账单上。
     *
     * <p>这条刻意挑临界处：无甲的女仆两种算法都会拒绝六只僵尸，看不出区别。
     * 一身钻石对上四只，旧算法说打、新算法说走，两者才分得开。
     */
    private static void verifiesGoodArmourDoesNotBuyACrowd() {
        ThreatField crowd = ThreatField.of(
                List.of(
                        zombie(1.0D), zombie(1.4D),
                        zombie(1.8D), zombie(2.2D)
                ),
                MELEE_REACH
        );
        // 满血钻石甲：吸收量翻倍。武器仍是那把铁剑。
        CombatCapability armoured = new CombatCapability(
                40.0D, IRON_SWORD.meleeDps(), 0.0D, MELEE_REACH
        );
        require(
                POLICY.assess(crowd, armoured, 1.0D) != RiskVerdict.ENGAGE,
                "In diamond she waded into four zombies at once, which is the "
                        + "stand-and-die the players watched"
        );
        // 同一身装备，单挑照打——保守不能变成怯战。
        ThreatField alone = ThreatField.of(
                List.of(zombie(1.5D)), MELEE_REACH
        );
        require(
                POLICY.assess(alone, armoured, 1.0D) == RiskVerdict.ENGAGE,
                "The same maid refused a single zombie, so this is no longer "
                        + "caution, it is paralysis"
        );
    }

    private static void verifiesAHordeIsStillAHorde() {
        ThreatField swarm = ThreatField.of(
                List.of(
                        zombie(1.0D), zombie(1.2D), zombie(1.5D),
                        zombie(1.8D), zombie(2.0D), zombie(2.2D)
                ),
                MELEE_REACH
        );
        CombatCapability sword =
                new CombatCapability(20.0D, 6.0D, 0.0D, MELEE_REACH);
        require(
                POLICY.assess(swarm, sword, 1.0D) != RiskVerdict.ENGAGE,
                "She waded into six zombies in a plain apron"
        );
    }

    /**
     * 决定的是密集度，不是总数。
     *
     * <p>五只散在远处一个个来，和五只挤在她面前同时上，是两件事。
     */
    private static void verifiesDensityDecidesNotHeadcount() {
        CombatCapability capability =
                new CombatCapability(40.0D, 6.0D, 0.0D, MELEE_REACH);

        ThreatField spread = ThreatField.of(
                List.of(zombie(2.0D), zombie(18.0D), zombie(20.0D),
                        zombie(22.0D), zombie(25.0D)),
                MELEE_REACH
        );
        ThreatField packed = ThreatField.of(
                List.of(zombie(2.0D), zombie(2.0D), zombie(2.5D),
                        zombie(2.5D), zombie(3.0D)),
                MELEE_REACH
        );
        require(
                spread.total() == packed.total(),
                "The two crowds were not the same size"
        );
        require(
                packed.converging() > spread.converging(),
                "Packing them together did not raise the converging count"
        );
        require(
                POLICY.assess(spread, capability, 1.0D) == RiskVerdict.ENGAGE,
                "She refused hostiles that arrive one at a time"
        );
        require(
                POLICY.assess(packed, capability, 1.0D) != RiskVerdict.ENGAGE,
                "She charged five hostiles that all reach her at once"
        );
    }

    /** 拉开距离只在对方够不着时才是答案。 */
    private static void verifiesRangedAnswerNeedsThemToBeMelee() {
        CombatCapability archer =
                new CombatCapability(20.0D, 1.0D, 5.0D, MELEE_REACH);

        ThreatField melee = ThreatField.of(
                List.of(zombie(2.0D), zombie(2.0D), zombie(2.0D)),
                MELEE_REACH
        );
        require(
                POLICY.assess(melee, archer, 1.0D) == RiskVerdict.SKIRMISH,
                "She stood and traded instead of backing off and shooting"
        );

        ThreatField shooters = ThreatField.of(
                List.of(skeleton(10.0D), skeleton(12.0D), skeleton(14.0D)),
                MELEE_REACH
        );
        require(
                POLICY.assess(shooters, archer, 1.0D) == RiskVerdict.WITHDRAW,
                "She kept her distance from things that shoot back"
        );
    }

    /** 剩一颗心的胜利不是胜利，下一只走进来她就死了。 */
    private static void verifiesWoundedMaidDeclinesAWinnableTrade() {
        ThreatField one = ThreatField.of(List.of(zombie(2.0D)), MELEE_REACH);
        CombatCapability capability =
                new CombatCapability(60.0D, 10.0D, 0.0D, MELEE_REACH);
        require(
                POLICY.assess(one, capability, 1.0D) == RiskVerdict.ENGAGE,
                "A healthy maid declined an easy fight"
        );
        require(
                POLICY.assess(one, capability, 0.1D) != RiskVerdict.ENGAGE,
                "A maid on her last hearts took the same fight"
        );
    }

    private static void verifiesUnarmedNeverEngages() {
        ThreatField one = ThreatField.of(List.of(zombie(2.0D)), MELEE_REACH);
        CombatCapability bare =
                new CombatCapability(100.0D, 0.0D, 0.0D, MELEE_REACH);
        require(
                POLICY.assess(one, bare, 1.0D) == RiskVerdict.WITHDRAW,
                "She punched a hostile with nothing in her hands"
        );
        require(
                POLICY.assess(ThreatField.EMPTY, bare, 1.0D)
                        == RiskVerdict.STAND_DOWN,
                "With nothing to fight she was still told to withdraw"
        );
    }

    /**
     * 打主人的那个优先，哪怕她自己身上正挨打。
     *
     * <p>她是护卫不是幸存者：她能被修好，主人不能。远处那只射主人的骷髅
     * 比脚边这只咬她的僵尸更该先解决。
     */
    private static void verifiesOwnerAttackerOutranksHerOwn() {
        TargetSelectionPolicy targets = TargetSelectionPolicy.INSTANCE;

        ThreatSample bitingHer = zombie(1.0D, ThreatRelation.ATTACKING_MAID);
        ThreatSample shootingOwner =
                skeleton(14.0D, ThreatRelation.ATTACKING_OWNER);
        ThreatSample chosen =
                targets.select(List.of(bitingHer, shootingOwner));
        require(
                chosen == shootingOwner,
                "She answered her own attacker while her owner was shot"
        );

        // 同一优先级内才比距离，否则她会走过一个去打另一个。
        ThreatSample farMobbingOwner =
                zombie(12.0D, ThreatRelation.ATTACKING_OWNER);
        ThreatSample nearMobbingOwner =
                zombie(3.0D, ThreatRelation.ATTACKING_OWNER);
        require(
                targets.select(List.of(farMobbingOwner, nearMobbingOwner))
                        == nearMobbingOwner,
                "Among hostiles on her owner she did not start with the nearest"
        );

        // 谁都没在打人时，就近处理。
        require(
                targets.select(List.of(zombie(9.0D), zombie(2.0D))).distance()
                        == 2.0D,
                "With nobody engaged she did not pick the closest"
        );
        require(
                targets.select(List.of()) == null,
                "Selecting from nothing produced a target"
        );
    }

    /** 近战型敌人：够得到才打得到。 */
    private static ThreatSample zombie(double distance) {
        return zombie(distance, ThreatRelation.UNENGAGED);
    }

    private static ThreatSample zombie(
            double distance,
            ThreatRelation relation
    ) {
        return new ThreatSample(
                distance, 4.0D, 2.5D, 20, 20.0D, false, relation
        );
    }

    /** 远程型敌人：拉开距离对它无效。 */
    private static ThreatSample skeleton(double distance) {
        return skeleton(distance, ThreatRelation.UNENGAGED);
    }

    private static ThreatSample skeleton(
            double distance,
            ThreatRelation relation
    ) {
        return new ThreatSample(
                distance, 3.0D, 16.0D, 30, 20.0D, false, relation
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
