package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementRiskPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.RiskVerdict;
import com.laixia.maidintelligence.feature.behavior.domain.combat.TargetSelectionPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;

import java.util.List;

/**
 * 她守着一个点，而不只是站在场上。
 *
 * <p>感知此前只有一个中心——她自己。那对"这一架打不打"是够的，对"她是来保护谁
 * 的"不够：主人走开十格，围着主人的那一圈就落在她的球外了，而那正是她该管的事。
 * 于是感知成了两个球的并集，先打谁的平局判据也从"离我近"改成"离我守的东西近"。
 *
 * <p>这一条与 {@link TargetSelectionPolicy} 注释里记的三次失败形状很像，必须说
 * 清楚区别，否则下一个人会把它当成第四次重蹈覆辙：那三次改的是**按血量**挑目标
 * （先打残血的），失败原因是"任何能改选打谁的地方都会把她身上某个部分一起转过
 * 去"——脚被牵去追一只走开的，头被牵去瞄一只远的，而被选中的那一只与威胁本身
 * 无关。这一条挑的是"离要守的东西最近"，那正是**最先要发生的伤害**：她被牵过去
 * 的方向恰好是威胁正在去的方向，脚和头本来就该朝那里。
 */
public final class CombatWardVerification {
    private static final double CLOSING = 1.0D;

    private static final EngagementRiskPolicy POLICY =
            EngagementRiskPolicy.instance();

    private CombatWardVerification() {
    }

    public static void main(String[] args) {
        verifiesSomethingAtTheWardOutranksAnIdlerAtHerElbow();
        verifiesWithinTheWardBandSheStillTakesTheNearest();
        verifiesRelationStillOutranksProximity();
        verifiesNoWardIsTheOldOrdering();
        verifiesAWardDoesNotOverrideAnOwnerAttacker();
        verifiesSheDiesTryingRatherThanLeaveHerOwnerToIt();
        verifiesALastStandNeedsSomethingToFightWith();
        verifiesHerOwnLosingFightIsStillHersToLeave();
        System.out.println("Combat ward verification passed.");
    }

    /**
     * 两只同样没在打人，一只贴着她、一只贴着主人——先处理贴着主人的。
     *
     * <p>这就是"她为什么在场"。贴着她的那只对她更方便，而贴着主人的那只是她
     * 存在的理由。
     */
    private static void verifiesSomethingAtTheWardOutranksAnIdlerAtHerElbow() {
        ThreatSample atHerElbow = zombie(1.5D, 14.0D);
        ThreatSample atTheOwner = new ThreatSample(
                12.0D, 4.0D, 2.5D, 20, 20.0D, false, CLOSING,
                ThreatRelation.NEAR_WARD, 1.0D
        );
        require(
                TargetSelectionPolicy.INSTANCE.select(
                        List.of(atHerElbow, atTheOwner)) == atTheOwner,
                "她挑了离自己近的那只，而另一只正站在她要守的人身边"
        );
    }

    /**
     * 但"站在锚点旁边"仍然只是一个档，同档里照旧取近。
     *
     * <p>这一条是上一版翻车的护栏：ward 距离一旦参与连续比较，目标就会随两边
     * 微动每 tick 翻面，而每次改选都会牵动她的脚和头。离散的档不会。
     */
    private static void verifiesWithinTheWardBandSheStillTakesTheNearest() {
        ThreatSample nearHer = new ThreatSample(
                2.0D, 4.0D, 2.5D, 20, 20.0D, false, CLOSING,
                ThreatRelation.NEAR_WARD, 3.0D
        );
        ThreatSample furtherFromHer = new ThreatSample(
                9.0D, 4.0D, 2.5D, 20, 20.0D, false, CLOSING,
                ThreatRelation.NEAR_WARD, 0.5D
        );
        require(
                TargetSelectionPolicy.INSTANCE.select(
                        List.of(furtherFromHer, nearHer)) == nearHer,
                "同一档内她没有就近处理，而是被更靠近锚点的那一只牵走了"
        );
    }

    /**
     * 关系仍然先行。
     *
     * <p>正在打主人的那只，即便离锚点更远，也排在一只只是"站得近"的前面——
     * 已经发生的伤害胜过即将发生的。
     */
    private static void verifiesRelationStillOutranksProximity() {
        ThreatSample mobbingOwner = new ThreatSample(
                10.0D, 4.0D, 2.5D, 20, 20.0D, false, CLOSING,
                ThreatRelation.ATTACKING_OWNER, 9.0D
        );
        ThreatSample idleButClose = zombie(3.0D, 12.0D);
        require(
                TargetSelectionPolicy.INSTANCE.select(
                        List.of(idleButClose, mobbingOwner)) == mobbingOwner,
                "近处一只没参战的压过了正在打主人的那只"
        );
    }

    /**
     * 没有要守之处时，排序必须与从前逐字相同。
     *
     * <p>所有既有实测读数都产自"只有她自己一个中心"的那套。若无主无家的女仆
     * 行为变了，那些基线就作废了。
     */
    private static void verifiesNoWardIsTheOldOrdering() {
        ThreatSample near = zombie(2.0D, Double.POSITIVE_INFINITY);
        ThreatSample far = zombie(9.0D, Double.POSITIVE_INFINITY);
        require(
                TargetSelectionPolicy.INSTANCE.select(List.of(far, near))
                        == near,
                "没有要守的东西时，她没有就近处理"
        );
    }

    /** 守点不该把"有人正在打主人"这条盖过去，两者方向一致时更不该。 */
    private static void verifiesAWardDoesNotOverrideAnOwnerAttacker() {
        ThreatSample onOwner = new ThreatSample(
                6.0D, 4.0D, 2.5D, 20, 20.0D, false, CLOSING,
                ThreatRelation.ATTACKING_OWNER, 0.5D
        );
        ThreatSample onHer = new ThreatSample(
                1.0D, 4.0D, 2.5D, 20, 20.0D, false, CLOSING,
                ThreatRelation.ATTACKING_MAID, 8.0D
        );
        require(
                TargetSelectionPolicy.INSTANCE.select(List.of(onHer, onOwner))
                        == onOwner,
                "主人正在挨打，她先答复了咬自己的那一只"
        );
    }

    /**
     * 怪贴到主人身上时，她不跑——哪怕这一架她打不过。
     *
     * <p>玩家报告的原话是"判定打不过就一直跑，怪都贴到主人脸上了还在跑，也不
     * 试一下"。裁决此前只回答"**她**活不活得下来"，而那一刻要保的根本不是她。
     *
     * <p>用一场她**确实**打不过的仗来断言：同一群敌人、同一副装备，只把关系从
     * "冲着她来"换成"冲着主人去"，结论必须从撤离翻成交战。若两者答案相同，
     * 这条规则就没有在起作用，而测试会绿——所以两个方向都要断。
     */
    private static void verifiesSheDiesTryingRatherThanLeaveHerOwnerToIt() {
        CombatCapability outmatched =
                new CombatCapability(20.0D, 1.0D, 0.0D, 3.0D);
        require(
                POLICY.assess(hopelessFieldAimedAt(ThreatRelation.ATTACKING_MAID),
                        outmatched, 1.0D, true) != RiskVerdict.ENGAGE,
                "这一架本该是打不过的，基准前提就不成立"
        );
        require(
                POLICY.assess(hopelessFieldAimedAt(ThreatRelation.ATTACKING_OWNER),
                        outmatched, 1.0D, true) == RiskVerdict.ENGAGE,
                "怪正在打主人，她仍然选择了撤离"
        );
        // 血快没了也一样。三成血那条撤离线同样是为保住她自己写的。
        require(
                POLICY.assess(hopelessFieldAimedAt(ThreatRelation.ATTACKING_OWNER),
                        outmatched, 0.05D, true) == RiskVerdict.ENGAGE,
                "只剩一丝血时她丢下了正在挨打的主人"
        );
    }

    /** 赴死也得有东西可用：空手不是舍身，是白送。 */
    private static void verifiesALastStandNeedsSomethingToFightWith() {
        CombatCapability bare = new CombatCapability(20.0D, 0.0D, 0.0D, 3.0D);
        require(
                POLICY.assess(hopelessFieldAimedAt(ThreatRelation.ATTACKING_OWNER),
                        bare, 1.0D, true) == RiskVerdict.WITHDRAW,
                "空着手她也留下来了，那不是保护，是多一具尸体"
        );
    }

    /**
     * 冲着她自己来的那场仗，进退仍然由她自己算。
     *
     * <p>这条是上面那条的护栏：舍身的门必须窄到不影响她平时的取舍，否则等于把
     * 整套风险裁决关掉了。
     */
    private static void verifiesHerOwnLosingFightIsStillHersToLeave() {
        CombatCapability outmatched =
                new CombatCapability(20.0D, 1.0D, 0.0D, 3.0D);
        require(
                POLICY.assess(hopelessFieldAimedAt(ThreatRelation.UNENGAGED),
                        outmatched, 0.2D, false) == RiskVerdict.WITHDRAW,
                "没人碰主人、她自己又快死了，她却留下来对砍"
        );
    }

    /** 一场她确实赢不了的仗：四只重手，围着。 */
    private static ThreatField hopelessFieldAimedAt(ThreatRelation relation) {
        ThreatSample[] pack = new ThreatSample[4];
        for (int slot = 0; slot < pack.length; slot++) {
            pack[slot] = new ThreatSample(
                    1.5D, 13.0D, 2.5D, 20, 24.0D, false, CLOSING,
                    relation, relation == ThreatRelation.ATTACKING_OWNER
                            ? 1.0D : 12.0D
            );
        }
        return ThreatField.of(List.of(pack), 3.0D);
    }

    private static ThreatSample zombie(double toHer, double toWard) {
        return new ThreatSample(
                toHer, 4.0D, 2.5D, 20, 20.0D, false, CLOSING,
                ThreatRelation.UNENGAGED, toWard
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
