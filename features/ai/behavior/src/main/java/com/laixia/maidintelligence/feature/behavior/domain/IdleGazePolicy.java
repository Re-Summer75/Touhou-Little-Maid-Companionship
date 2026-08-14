package com.laixia.maidintelligence.feature.behavior.domain;

import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;

/**
 * 没事做的时候，她的眼睛落在哪儿。
 *
 * <p>自由模式把宿主那两条挑注视目标的行为一并让位了（它们是决定），而本模组只在
 * **有事做**的时候写注视目标——走向某处时看着那处，打架时看着对手。于是闲着的时候
 * 没有任何人写它，她的头就停在上一次被摆过的方向上，一动不动。玩家看到的是"她只
 * 会盯着主人"或者干脆盯着空气。
 *
 * <p>这一条补的是那个空缺：**看哪儿是默认值，不是决定**。它每 tick 写一次，但写在
 * 意图之前，所以任何有事做的意图都会盖掉它——环顾自然让位，不需要谁去协调。
 *
 * <p>因此它不是一个意图，也没有 band：它不占用她的脚，不与任何东西竞争，也不该在
 * 警戒时被收回——一个正被什么东西盯着的人**更**该四处看，而不是更不该。
 */
public final class IdleGazePolicy {
    /**
     * 一眼看多久。
     *
     * <p>每 tick 换一个方向不是环顾，是抽搐；而盯住不放又回到了原来那个毛病。所以
     * 一眼要停留一段时间，且**不能等长**——等长的节奏一眼就能看出来是机器。
     */
    public static final int SHORTEST_GLANCE_TICKS = 30;

    /** 最长：五秒。 */
    public static final int LONGEST_GLANCE_TICKS = 100;

    /**
     * 一眼落在主人身上的概率。
     *
     * <p>四成。她确实惦记着他，但"只会看着主人"正是要改掉的那件事——十次里有四次
     * 看他，剩下的看别处，这个比例既看得出她在意谁，又看得出她是活的。
     */
    public static final double OWNER_SHARE = 0.4D;

    /**
     * 剩下的那些里，落在附近某个活物身上的概率。
     *
     * <p>一半。另一半看向没有东西的方向——那正是"东张西望"里"张望"的部分，而一个
     * 只会看活物的人在空旷地带又会退回原地不动。
     */
    public static final double CREATURE_SHARE = 0.5D;

    /**
     * 看多远。
     *
     * <p>感知的一半。射程一律以 {@link PerceptionRange} 表达，而这一条显式收窄：
     * 她的头能转到的方向和她能注意到的范围不是一回事，把注视点放到视野边缘会让她
     * 长时间盯着一个几乎不动的远处，看起来像发呆而不是环顾。
     */
    public static final double GLANCE_RANGE = PerceptionRange.BLOCKS / 2.0D;

    /**
     * 太近的活物不看。
     *
     * <p>两格。{@code EntityTracker} 盯的是对方的**眼睛**，而一只站在她脚边的鸡，
     * 眼睛几乎在地面上——那一眼就是低头盯着地板，玩家报的"会看向地面"正是它。
     * 距离一拉开，同样的高度差就只是一个很浅的俯角。
     */
    public static final double NEAREST_GLANCE = 2.0D;

    public static final IdleGazePolicy INSTANCE = new IdleGazePolicy();

    private IdleGazePolicy() {
    }

    /** 这一眼停留多久。 */
    public int glanceTicks(double roll) {
        int span = LONGEST_GLANCE_TICKS - SHORTEST_GLANCE_TICKS;
        return SHORTEST_GLANCE_TICKS + (int) Math.round(clamp(roll) * span);
    }

    /** 这一眼是不是看主人。 */
    public boolean looksAtOwner(double roll) {
        return clamp(roll) < OWNER_SHARE;
    }

    /**
     * 不看主人的那些里，这一眼是不是看某个活物。
     *
     * @param roll 与 {@link #looksAtOwner} 用的是**不同的**随机数，否则两个判据会
     *             共用同一个门槛，"不看主人"就恒等于"看活物"
     */
    public boolean looksAtCreature(double roll) {
        return clamp(roll) < CREATURE_SHARE;
    }

    /** 随便看向哪个方向时，那个点相对她的横向偏移，东向。 */
    public double offsetX(double bearingRoll) {
        return GLANCE_RANGE * Math.cos(bearing(bearingRoll));
    }

    /** 同上，南向。 */
    public double offsetZ(double bearingRoll) {
        return GLANCE_RANGE * Math.sin(bearing(bearingRoll));
    }

    /**
     * 视线抬高或压低多少。
     *
     * <p>只在很小的范围内动。人环顾时视线基本是平的，偶尔抬一下；让它满幅摆动会
     * 变成翻白眼和盯地板。
     */
    public double offsetY(double roll) {
        return (clamp(roll) - 0.5D) * 2.0D;
    }

    private static double bearing(double roll) {
        return clamp(roll) * 2.0D * Math.PI;
    }

    private static double clamp(double roll) {
        if (!Double.isFinite(roll)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, roll));
    }
}
