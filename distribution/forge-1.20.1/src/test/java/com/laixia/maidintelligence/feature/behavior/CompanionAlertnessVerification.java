package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.CompanionAlertness;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionBand;

/**
 * 状态分类：处境决定她被允许做什么。
 *
 * <p>这一层回答的是中断 band 和行为占用都答不了的那个问题——"周围有东西，
 * 所以现在不该走开去捡胡萝卜"。band 只排本模组自己的意图，占用只描述本体在
 * 干什么，两者都不表达"处境紧不紧"。
 */
public final class CompanionAlertnessVerification {
    /** 她拔刀、转身、做好准备所需的时间。 */
    private static final double REACTION = 2.0D;

    private CompanionAlertnessVerification() {
    }

    public static void main(String[] args) {
        verifiesQuietWorldAllowsEverything();
        verifiesDistantHostileStillLetsHerWork();
        verifiesArrivalNotDistanceRaisesTheAlarm();
        verifiesFightingOverridesEverything();
        verifiesUnknownArrivalIsTreatedAsUrgent();
        verifiesTheWholePermissionMatrix();
        verifiesAnUnknownBandIsRefused();
        System.out.println("Companion alertness verification passed.");
    }

    /**
     * 整张许可矩阵，逐格。
     *
     * <p>写成一张字面表而不是"重述一遍实现"，是这条断言的全部意义：把每一格的
     * 期望值单独列出来，改实现时必须回来改这里，于是"顺手放宽一格"不可能悄悄
     * 发生。行数换的是这个。
     */
    private static void verifiesTheWholePermissionMatrix() {
        CompanionBand[] columns = {
                CompanionBand.SAFETY, CompanionBand.OWNER_COMMAND,
                CompanionBand.NEEDS, CompanionBand.COMPANIONSHIP,
                CompanionBand.LEISURE
        };
        CompanionAlertness[] rows = {
                CompanionAlertness.CALM, CompanionAlertness.WARY,
                CompanionAlertness.THREATENED, CompanionAlertness.FIGHTING
        };
        boolean[][] expected = {
                {true, true, true, true, true},
                {true, true, true, true, false},
                {true, true, false, false, false},
                {true, false, false, false, false}
        };
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < columns.length; column++) {
                require(
                        rows[row].permits(columns[column])
                                == expected[row][column],
                        rows[row] + " 对 " + columns[column] + " 答了 "
                                + rows[row].permits(columns[column])
                                + "，与矩阵不符"
                );
            }
        }
    }

    /**
     * 认不出的类别一律拒绝。
     *
     * <p>数据包写了一个新的优先级数字时 {@code CompanionBand.of} 答 null，而这里
     * 必须是"不许"而不是"随便"——否则一个绕过闸门的新 band 会静默获得全部许可。
     */
    private static void verifiesAnUnknownBandIsRefused() {
        require(
                CompanionBand.of(77) == null,
                "77 不是一个 band，却被归了类"
        );
        for (CompanionAlertness state : CompanionAlertness.values()) {
            require(!state.permits(null), state + " 放行了一个认不出的类别");
        }
    }

    /** 没有敌人就没有限制。 */
    private static void verifiesQuietWorldAllowsEverything() {
        CompanionAlertness calm = CompanionAlertness.of(
                false, 0, Double.POSITIVE_INFINITY, REACTION
        );
        require(calm == CompanionAlertness.CALM, "空场却不是 CALM");
        require(calm.allowsErrands(), "没有敌人时她连差事都不许做");
        require(calm.allowsLeisure(), "没有敌人时她连坐下都不许");
    }

    /**
     * 看得见但过不来的敌人，不该让她停下手里的活。
     *
     * <p>这条是防过度反应的：山谷对面一只骷髅如果能让她停掉全部家务，
     * 那她在任何有怪的夜晚都会呆站着。她放弃的只是"坐下来看书"这类
     * 一时半会儿起不来的事。
     */
    private static void verifiesDistantHostileStillLetsHerWork() {
        CompanionAlertness wary = CompanionAlertness.of(
                false, 1, 12.0D, REACTION
        );
        require(wary == CompanionAlertness.WARY, "远处的敌人没有被判成 WARY");
        require(wary.allowsErrands(), "一只过不来的怪就让她停掉了差事");
        require(!wary.allowsLeisure(), "有怪在场她还坐下来消遣");
    }

    /**
     * 升级的判据是"还有多久到"，不是"离得多近"。
     *
     * <p>同样八格，走过来的和站着不动的必须给出不同答案——这正是预测层存在的
     * 理由。只看距离的话，冲刺的和发呆的一视同仁，她要么过度紧张要么反应不及。
     */
    private static void verifiesArrivalNotDistanceRaisesTheAlarm() {
        require(
                CompanionAlertness.of(false, 1, 0.5D, REACTION)
                        == CompanionAlertness.THREATENED,
                "半秒后就会打到她的东西没有让她进入戒备"
        );
        require(
                CompanionAlertness.of(false, 1, Double.POSITIVE_INFINITY, REACTION)
                        == CompanionAlertness.WARY,
                "一个根本不会到达的东西把她吓成了 THREATENED"
        );
        require(
                !CompanionAlertness.of(false, 1, 0.5D, REACTION).allowsErrands(),
                "东西马上就到，她还准备走开去干别的"
        );
    }

    /** 打起来了就什么都别想了。 */
    private static void verifiesFightingOverridesEverything() {
        CompanionAlertness fighting = CompanionAlertness.of(
                true, 0, Double.POSITIVE_INFINITY, REACTION
        );
        require(
                fighting == CompanionAlertness.FIGHTING,
                "已经在打了却不是 FIGHTING"
        );
        require(!fighting.allowsErrands(), "战斗中她还会走开去捡东西");
        require(fighting.needsHandsFree(), "战斗中她的手却被判为可以占用");
    }

    /**
     * 算不出到达时间时按紧张处理。
     *
     * <p>NaN 会穿过所有比较，默认落到 WARY——而 WARY 是仍然允许她走开的那一档。
     * 一个算不出来的数不该换来更多自由。
     */
    private static void verifiesUnknownArrivalIsTreatedAsUrgent() {
        require(
                CompanionAlertness.of(false, 1, Double.NaN, REACTION)
                        == CompanionAlertness.THREATENED,
                "算不出到达时间时她被放行了"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
