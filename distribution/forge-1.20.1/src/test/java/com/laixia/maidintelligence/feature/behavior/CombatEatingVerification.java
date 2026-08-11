package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.sustenance.EatingPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.sustenance.FoodValue;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;

import java.util.List;

/**
 * 吃不吃、吃哪一口——同一套算术，在不同处境下给出不同答案。
 *
 * <p>没有一条断言提到"金苹果"。金苹果之所以特殊，是因为它用一秒半换八点伤害
 * 吸收和四点回血，而一个做到同样事情的模组水果就是同一样东西。凡是只靠物品名
 * 就能通过的断言，都说明这条规则根本没有被验证到。
 *
 * <p>最要紧的是"值不值得"这一半。食物永远有帮助，所以"有帮助"不是判据；判据是
 * **这一仗会不会因为吃了而不同**。少了这一条，她会把家底喝进一场本来就赢的架里。
 */
public final class CombatEatingVerification {
    private static final EatingPolicy POLICY = EatingPolicy.instance();

    /** 她自己的近战触及，用于聚合威胁场。 */
    private static final double HER_REACH = 2.0D;

    /** 吃一口的时长，与原版一致。 */
    private static final double EATING_SECONDS = 1.6D;

    /** 面包：只管饱，战斗里买不到任何东西。 */
    private static final FoodValue BREAD =
            new FoodValue(0, 25.0D, 0.0D, 0.0D, EATING_SECONDS);

    /** 金苹果：四点回血 + 八点伤害吸收。 */
    private static final FoodValue GOLDEN_APPLE =
            new FoodValue(1, 20.0D, 4.0D, 8.0D, EATING_SECONDS);

    /** 附魔金苹果：贵得多，也强得多。 */
    private static final FoodValue ENCHANTED_APPLE =
            new FoodValue(2, 20.0D, 16.0D, 32.0D, EATING_SECONDS);

    private CombatEatingVerification() {
    }

    public static void main(String[] args) {
        verifiesAFullMaidDoesNotSnack();
        verifiesALullIsSpentOnTheCheapestThing();
        verifiesAWinnableFightCostsHerNothing();
        verifiesAnUnwinnableFightBuysTheCheapestThatTurnsIt();
        verifiesSheArmsHerselfEvenForAFightSheCannotWin();
        verifiesDyingSheReachesForTheMostItCanBuy();
        verifiesSheDoesNotEatWhileBeingBeaten();
        verifiesABuffAlreadyOnHerIsNotBoughtTwice();
        verifiesBackingAwayMakesTheWindowAffordable();
        verifiesASlowMealIsAWorseRiskThanAQuickOne();
        System.out.println("Combat eating verification passed.");
    }

    /** 不饿就不吃，哪怕四下无人——总有下一口"不要钱"的。 */
    private static void verifiesAFullMaidDoesNotSnack() {
        require(
                POLICY.choose(
                        List.of(BREAD), quiet(), healthy(), 1.0D, 1.0D, true
                ) == null,
                "四下无人且并不饿，她还是吃了一口"
        );
    }

    /**
     * 没人够得着的时候，那一口是免费的——而免费的时候该吃最便宜的。
     *
     * <p>这一条同时覆盖"逃跑途中补状态"：一次成功的后撤就是一段没人能碰到她的
     * 时间，那是整场战斗里最该用来吃东西的时刻。
     */
    private static void verifiesALullIsSpentOnTheCheapestThing() {
        FoodValue chosen = POLICY.choose(
                List.of(GOLDEN_APPLE, BREAD, ENCHANTED_APPLE),
                quiet(), healthy(), 1.0D, 0.4D, true
        );
        require(chosen != null, "空档期她饿着却什么都不吃");
        require(
                chosen == BREAD,
                "空档期她啃了 " + name(chosen) + "；饱腹不挑食物，"
                        + "而空档期正是最不该动金苹果的时候"
        );
    }

    /** 打得过的架不该花掉家底——食物永远有帮助，这不是判据。 */
    private static void verifiesAWinnableFightCostsHerNothing() {
        require(
                POLICY.choose(
                        List.of(GOLDEN_APPLE), oneZombie(), healthy(),
                        1.0D, 1.0D, true
                ) == null,
                "一只僵尸她就开了金苹果"
        );
    }

    /**
     * 打不过、吃了能打过，才值得吃——而且吃够就行。
     *
     * <p>断言挑的是"最便宜的那个能翻盘的"，不是"最强的那个"。答案一旦被改写，
     * 再买就没有东西可买了，而"够用的面包"和"过剩的附魔苹果"之间差着一个附魔
     * 苹果。
     */
    private static void verifiesAnUnwinnableFightBuysTheCheapestThatTurnsIt() {
        // 三只、八格外走过来，她只剩十点可挨。裸打是 SKIRMISH，吃下金苹果
        // （净赚 5.7）之后是 ENGAGE——夹具选在这个翻转点两侧都留足余量的地方。
        ThreatField crowd = swarm(3, 8.0D);
        CombatCapability frail =
                new CombatCapability(10.0D, 11.2D, 0.0D, HER_REACH);
        require(
                POLICY.choose(
                        List.of(GOLDEN_APPLE), crowd, frail, 1.0D, 1.0D, true
                ) != null,
                "这一仗她本来打不过、吃了就打得过，她却没吃"
        );
        FoodValue chosen = POLICY.choose(
                List.of(ENCHANTED_APPLE, GOLDEN_APPLE), crowd, frail,
                1.0D, 1.0D, true
        );
        require(
                chosen == GOLDEN_APPLE,
                "两样都能翻盘，她挑了 " + name(chosen)
                        + "；答案改写之后再贵也买不到别的"
        );
    }

    /**
     * 打不赢的仗照样要穿上——**这一条是反过来的，原来钉的是相反的行为**。
     *
     * <p>原来的说法是"吃了也打不过就别吃，那只是晚一点输"，判据是吃完之后裁决
     * 仍然不是 ENGAGE。它把"赢不了"和"这口买不到东西"当成了一回事，而这套模型里
     * 根本不是：裁决还有 WITHDRAW 和 SKIRMISH，那是**她不赢但要活着走出来**的仗，
     * 而伤害吸收买的正是那个。实机暴露的现象就是这条规则造成的——
     * 打不过的敌人她一口不吃，等到补的时候已经晚了。
     *
     * <p>所以判据从"这口能不能让她赢"改成"她身上的够不够扛住要来的"。仗越难
     * 越该穿，而不是越难越不穿。
     *
     * <p>省着点花靠的是另外三样，不是靠不吃：已经生效的效果被扫描器记为零收益
     * （不会叠着吃），空档期要求没人够得着（不会在挨打时嚼），以及挑**最便宜**
     * 够用的那个而不是最厚的。
     */
    private static void verifiesSheArmsHerselfEvenForAFightSheCannotWin() {
        require(
                POLICY.choose(
                        List.of(GOLDEN_APPLE), swarm(6, 5.0D),
                        new CombatCapability(8.0D, 11.2D, 0.0D, HER_REACH),
                        1.0D, 1.0D, true
                ) != null,
                "一场她赢不了的仗，她连状态都不上——活着退出来也是要本钱的"
        );
    }

    /** 快死的时候不比价钱，只比"这一口能让我多扛多少"。 */
    private static void verifiesDyingSheReachesForTheMostItCanBuy() {
        FoodValue chosen = POLICY.choose(
                List.of(BREAD, GOLDEN_APPLE, ENCHANTED_APPLE),
                oneZombie(), healthy(), 0.1D, 1.0D, true
        );
        require(chosen != null, "血量见底她什么都没吃");
        require(
                chosen == ENCHANTED_APPLE,
                "血量见底她吃的是 " + name(chosen) + "；这种时候省下来的东西没有用处"
        );
    }

    /**
     * 但"快死了"不等于"什么都往嘴里塞"。
     *
     * <p>一口回两点、而嚼的时候飞进来四点，这一口让她更糟。因为快死就去够它，
     * 正是"快死"变成"死了"的那条路径。
     */
    private static void verifiesSheDoesNotEatWhileBeingBeaten() {
        require(
                POLICY.choose(
                        List.of(BREAD, GOLDEN_APPLE), swarm(8, 1.0D), healthy(),
                        0.1D, 1.0D, false
                ) == null,
                "八只贴身猛打，她还是低头去啃东西——那一口买到的还不够嚼的时候挨的"
        );
    }

    /**
     * 已经吃下去的那一个，不该再买第二次。
     *
     * <p>玩家报的是"吃了一个、buff 已经在身上了，条件还是不满足，于是接着吃"。
     * 根因有两层，这条钉的是决策这一层：只要收益如实报成零，同一个处境下第二口
     * 就不再划算——而收益是否如实，由扫描器那一层负责（它会问她身上有没有这个
     * 效果）。
     *
     * <p>所以这里用两个 FoodValue 表达"同一个金苹果，在她已经有吸收之前和之后"：
     * 前者值十二点，后者值零。断言只要求后者不被吃掉，不关心前者。
     */
    private static void verifiesABuffAlreadyOnHerIsNotBoughtTwice() {
        ThreatField crowd = swarm(3, 8.0D);
        CombatCapability frail =
                new CombatCapability(10.0D, 11.2D, 0.0D, HER_REACH);
        require(
                POLICY.choose(
                        List.of(GOLDEN_APPLE), crowd, frail, 1.0D, 1.0D, true
                ) != null,
                "夹具本身不成立：这一口本该是划算的"
        );

        // 同一个物品，效果已经在她身上——扫描器据此把收益报成零。
        FoodValue spent = new FoodValue(1, 20.0D, 0.0D, 0.0D, EATING_SECONDS);
        require(
                POLICY.choose(
                        List.of(spent), crowd, frail, 1.0D, 1.0D, true
                ) == null,
                "buff 已经在身上，她还是又吃了一个——一摞金苹果会这样在八秒里喝光"
        );
        require(
                POLICY.choose(
                        List.of(spent), crowd, frail, 0.1D, 1.0D, true
                ) == null,
                "血量见底时她把买不到任何东西的那一口也吃了"
        );
    }

    /**
     * 后撤和补状态不是二选一——退着走的那几秒，正是他们在走路的那几秒。
     *
     * <p>同样八只、同样一口，差别只在距离：贴在她身上时那一口买到的不够嚼的时候
     * 挨的，而她拉开到他们够不着时，同一口就划算了。这条与"八只贴身猛打不该吃"
     * 成对，缺了任何一条，另一条都能被一个恒定答案糊弄过去。
     *
     * <p>要紧的是它按"**已经够得到她的有几个**"计价，不是按"场上有几个"。后者会
     * 让一个正在风筝六只僵尸的女仆把那一口算成六个人围着她吃。
     */
    private static void verifiesBackingAwayMakesTheWindowAffordable() {
        require(
                POLICY.choose(
                        List.of(GOLDEN_APPLE), swarm(8, 1.0D), healthy(),
                        0.1D, 1.0D, true
                ) == null,
                "八只贴在她身上，她还是低头去啃"
        );
        require(
                POLICY.choose(
                        List.of(GOLDEN_APPLE), swarm(8, 6.0D), healthy(),
                        0.1D, 1.0D, true
                ) != null,
                "同样八只、她已经拉开到他们够不着，血量见底却还不肯吃——"
                        + "退着走的那几秒是他们在走路，那口是划算的"
        );
    }

    /**
     * 同样的收益，嚼得久的那口更危险。
     *
     * <p>食用时长是物品自己的事——普通食物三十二 tick，干海带十六，模组口粮什么
     * 都可能。写死一个常数会把"抢一口"和"慢慢吃"算成同一种风险，而那正是这条
     * 决定的全部内容。
     *
     * <p>两个食物除了嚼多久之外完全相同，所以差别只可能来自窗口长度。
     */
    private static void verifiesASlowMealIsAWorseRiskThanAQuickOne() {
        ThreatField pressed = swarm(6, 1.0D);
        FoodValue quick = new FoodValue(3, 20.0D, 4.0D, 8.0D, 0.8D);
        FoodValue slow = new FoodValue(4, 20.0D, 4.0D, 8.0D, 3.2D);
        require(
                POLICY.choose(
                        List.of(quick), pressed, healthy(), 0.1D, 1.0D, true
                ) != null,
                "抢一口就能吃完的东西她也不吃"
        );
        require(
                POLICY.choose(
                        List.of(slow), pressed, healthy(), 0.1D, 1.0D, true
                ) == null,
                "同样的收益、四倍的嚼食时间，她照吃不误——"
                        + "食用时长没有进入决定"
        );
    }

    /** 四下无人：没有敌人，所以什么都到不了。 */
    private static ThreatField quiet() {
        return ThreatField.EMPTY;
    }

    /** 一只普通僵尸，四格外走过来。 */
    private static ThreatField oneZombie() {
        return ThreatField.of(List.of(zombie(4.0D)), HER_REACH);
    }

    /**
     * 一群僵尸，走过来。
     *
     * <p>距离是这些夹具里最要紧的参数，不是数量：同样六只，八格外走过来时那一口
     * 是划算的，贴到脸上时嚼的功夫挨的比吃到的还多。两种处境都要有夹具，而把它们
     * 混成一个"一群僵尸"是这条测试第一版自相矛盾的原因。
     */
    private static ThreatField swarm(int count, double distance) {
        List<ThreatSample> all = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            all.add(zombie(distance + index * 0.2D));
        }
        return ThreatField.of(all, HER_REACH);
    }

    private static ThreatSample zombie(double distance) {
        return new ThreatSample(
                distance, 3.0D, 1.43D, 20, 20.0D, false, 1.0D,
                ThreatRelation.UNENGAGED
        );
    }

    /** 一个装备齐整、血量满格的她。 */
    private static CombatCapability healthy() {
        return new CombatCapability(20.0D, 11.2D, 0.0D, HER_REACH);
    }

    private static String name(FoodValue food) {
        if (food == null) {
            return "什么都没";
        }
        if (food == BREAD) {
            return "面包";
        }
        return food == GOLDEN_APPLE ? "金苹果" : "附魔金苹果";
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
