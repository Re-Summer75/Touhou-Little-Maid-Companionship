package com.laixia.maidintelligence.feature.behavior.domain.combat.threat;

import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;

/**
 * 一个物种**实际**够得到多远，减去它自己声明的那个数。
 *
 * <p>威胁的够到距离一律问它自己（{@code getMeleeAttackRangeSqr}），而这对原版和
 * 守规矩的模组都成立。问题出在不守规矩的那些：把攻击距离硬写在自己的 Goal 里、
 * 从不覆写那个方法的模组怪物，从外面**读不到**任何东西。她于是按一个偏小的数算
 * 安全距离，站在自以为够不着的位置上挨打。
 *
 * <p>外部唯一可读的信号是**她被打中时两者相隔多远**。所以这里记的不是"它够多远"，
 * 而是"它比它承认的多够多远"——那一份差额。声明的部分仍然逐个体去问，个体之间
 * 真正的差别（换了武器、上了效果）因此照旧被尊重，学习只补上世界拒绝说出口的那一截。
 *
 * <h2>为什么是"会褪的最大值"，不是越记越大的数</h2>
 *
 * <p>同一个物种也可能有不同的攻击距离，所以死记一个只增不减的最大值是错的：她会
 * 因为一次远距离的命中，从此永远躲着这个物种的每一只。
 *
 * <ul>
 *   <li><b>挨了一下就立刻抬上去</b>——那是硬证据，一次就够，犹豫没有意义；</li>
 *   <li><b>没再挨就慢慢褪回零</b>——{@link #FADING_TICKS} 之后完全忘掉，回到它
 *       自己声明的那个数；</li>
 *   <li><b>更近的一下不算证据</b>，也**不刷新计时**。一次打得近只说明这一下打得近，
 *       不说明它够不着；但它也不该让旧的记忆续命，否则那个数永远褪不掉。</li>
 * </ul>
 *
 * <p>于是它一直在更新：一只真的够得远的怪会不断把这个数顶上去，而一个被换掉武器、
 * 或者本来就是另一个变种的个体，会让它自然褪下来。
 */
public record ThreatReachMemory(double surplus, long learnedAt) {
    /**
     * 差额的上限。
     *
     * <p>感知半径的一半。够得比这还远的东西不是近战威胁，是炮——按近战距离给它定价
     * 会让她躲开所有东西。这一条同时是这套学习的安全带：读数出错、或者某个模组用
     * 近战伤害实现了一发炮弹，最坏也只让她多站八格。
     */
    public static final double MOST_A_SURPLUS_CAN_BE = PerceptionRange.BLOCKS / 2.0D;

    /**
     * 完全忘掉需要多久。
     *
     * <p>两分钟。短到"这一带的怪换了一批"能跟上，长到一场仗打完还记得——一场仗
     * 里她会被同一个东西打好几次，每一次都会把计时重新拨满。
     */
    public static final long FADING_TICKS = 2400L;

    /** 褪到这个值以下就当作没学过，条目可以丢掉。 */
    public static final double FAINTEST = 0.05D;

    /** 还没学过任何东西。 */
    public static final ThreatReachMemory NOTHING_LEARNED =
            new ThreatReachMemory(0.0D, 0L);

    public ThreatReachMemory {
        if (!Double.isFinite(surplus) || surplus < 0.0D) {
            surplus = 0.0D;
        }
        surplus = Math.min(surplus, MOST_A_SURPLUS_CAN_BE);
    }

    /**
     * 此刻还记得多少。
     *
     * <p>线性褪去而不是指数：指数永远褪不到零，条目就永远丢不掉，而"忘得干净"正是
     * 这份记忆敢于存在的前提。
     */
    public double surplusAt(long now) {
        long age = now - learnedAt;
        if (age < 0L || age >= FADING_TICKS) {
            return 0.0D;
        }
        return surplus * (1.0D - (double) age / FADING_TICKS);
    }

    /**
     * 她刚被这个物种从 {@code struckFrom} 格外打中。
     *
     * @param declared   这一只自己声明的够到距离
     * @param struckFrom 挨打那一刻两者的实际距离
     * @return 更新后的记忆；这一下打得比记忆更近时**原样返回**，让旧记忆继续褪
     */
    public ThreatReachMemory afterBeingStruckFrom(
            double declared, double struckFrom, long now
    ) {
        if (!Double.isFinite(declared) || !Double.isFinite(struckFrom)) {
            return this;
        }
        double observed = Math.min(
                Math.max(0.0D, struckFrom - declared), MOST_A_SURPLUS_CAN_BE
        );
        if (observed <= surplusAt(now)) {
            return this;
        }
        return new ThreatReachMemory(observed, now);
    }

    /** 这条记忆还值不值得占一个格子。 */
    public boolean worthKeeping(long now) {
        return surplusAt(now) > FAINTEST;
    }
}
