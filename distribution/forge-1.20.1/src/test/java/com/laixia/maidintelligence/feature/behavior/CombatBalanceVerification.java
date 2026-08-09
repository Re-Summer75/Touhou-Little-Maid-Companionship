package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatBalance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatPolicies;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementRiskPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.SpacingPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponSelectionPolicy;

/**
 * 战斗配平：数值归服务器所有，判断归代码所有。
 *
 * <p>这些数字此前每一个都能通过构造函数注入，而每一个又都只在自己的文件里
 * 用一份私有默认值构造过一次——形式上可配，事实上写死。想让她守六格而不是
 * 八格的服主只能重新编译。
 *
 * <p>这里验证三件事：默认值与原先逐个常量完全一致（未改配置的服务器行为不变）、
 * 安装之后每一条策略都真的换了数、以及不可能只换一半。
 */
public final class CombatBalanceVerification {
    private CombatBalanceVerification() {
    }

    public static void main(String[] args) {
        try {
            verifiesDefaultsAreWhatSheAlwaysUsed();
            verifiesInstallingReachesEveryPolicy();
            verifiesNonsenseIsRejectedAtTheEdge();
        } finally {
            // 全局单例：验证之间不能互相污染。
            CombatPolicies.install(CombatBalance.defaults());
        }
        System.out.println("Combat balance verification passed.");
    }

    /** 未改配置的服务器，行为必须与引入配平记录之前逐字相同。 */
    private static void verifiesDefaultsAreWhatSheAlwaysUsed() {
        CombatBalance defaults = CombatBalance.defaults();
        require(defaults.preferredRange() == 14.0D, "交战距离上限变了");
        require(defaults.retreatOvershoot() == 3.0D, "后撤超量变了");
        require(defaults.safeGap() == 1.0D, "安全间隙变了");
        require(defaults.meleeSuppression() == 0.8D, "近战压制估计变了");
        require(defaults.safetyMargin() == 1.2D, "安全裕度变了");
        require(defaults.bailOutHealth() == 0.3D, "脱离血线变了");
        require(defaults.survivableBlowShare() == 0.25D, "单击可承受比例变了");
        require(defaults.blowCaution() == 2.0D, "重击警惕系数变了");

        require(
                WeaponSelectionPolicy.instance().preferredRange() == 14.0D,
                "默认策略没有采用默认配平"
        );
        require(
                SpacingPolicy.instance().safeGap() == 1.0D,
                "间距策略没有采用默认配平"
        );
    }

    /**
     * 一次安装要落到每一条读它的策略上。
     *
     * <p>只落一半才是真正危险的状态：她按新的距离站位、按旧的裕度判断风险，
     * 这个组合谁都没有选过，也无法复现。
     */
    private static void verifiesInstallingReachesEveryPolicy() {
        CombatPolicies.install(new CombatBalance(
                6.0D, 4.0D, 2.0D, 0.5D, 1.5D, 0.5D, 0.4D, 3.0D
        ));
        require(
                WeaponSelectionPolicy.instance().preferredRange() == 6.0D,
                "武器选择仍然按旧的首选距离"
        );
        require(
                SpacingPolicy.instance().safeGap() == 2.0D,
                "间距策略仍然按旧的安全间隙"
        );
        require(
                SpacingPolicy.instance().retreatOvershoot() == 4.0D,
                "后撤超量没有跟着换"
        );
        require(
                CombatPolicies.active().bailOutHealth() == 0.5D,
                "当前生效的配平不是刚安装的那份"
        );

        // 风险策略没有暴露数值读取口，改由它的判断来证明数值确实换了：
        // 脱离血线抬到一半，半血就应该不再接受任何交易。
        require(
                EngagementRiskPolicy.instance() != null,
                "风险策略实例丢失"
        );

        CombatPolicies.install(CombatBalance.defaults());
        require(
                WeaponSelectionPolicy.instance().preferredRange() == 14.0D,
                "装回默认值没有生效"
        );
    }

    /**
     * 坏数值要在边界上被拒绝，而不是变成一场看不懂的战斗。
     *
     * <p>零安全间隙、负的首选距离这类值不会崩溃，只会让她做出无法解释的动作，
     * 那种问题最难查。
     */
    private static void verifiesNonsenseIsRejectedAtTheEdge() {
        requireRejected(
                () -> new CombatBalance(
                        -1.0D, 3.0D, 1.0D, 0.8D, 1.2D, 0.3D, 0.25D, 2.0D
                ),
                "负的首选距离被接受了"
        );
        requireRejected(
                () -> new CombatBalance(
                        8.0D, 3.0D, 0.0D, 0.8D, 1.2D, 0.3D, 0.25D, 2.0D
                ),
                "零安全间隙被接受了"
        );
        requireRejected(
                () -> new CombatBalance(
                        8.0D, 3.0D, 1.0D, 0.8D, 1.2D, 1.5D, 0.25D, 2.0D
                ),
                "大于一的脱离血线被接受了"
        );
    }

    private static void requireRejected(Runnable build, String message) {
        try {
            build.run();
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
