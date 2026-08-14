package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatReachMemory;

/**
 * 从挨打里学到的够到距离：学得进去，也忘得掉。
 *
 * <p>"学不进去"是玩家报的那件事——模组怪物把攻击距离硬写在自己的 Goal 里，从外面
 * 读到的永远只是碰撞箱宽度，她站在自以为够不着的位置上挨打。
 *
 * <p>"忘不掉"是修它时最容易换来的新毛病，而且更难发现：同一个物种也可能有不同的
 * 攻击距离，一个只增不减的最大值会让她因为一次远距离的命中，从此永远躲着这个物种
 * 的每一只。所以下面一半的判据是关于**褪去**的，其中最要紧的一条是
 * {@link #verifiesACloserHitDoesNotRenewTheLease()}——那是"不断更新"与"死记"之间
 * 唯一的分界。
 */
public final class ThreatReachVerification {
    /** 某个模组怪物自己声明的够到距离。 */
    private static final double DECLARED = 1.5D;

    /** 而它实际上从这么远打到了她。 */
    private static final double ACTUALLY_STRUCK_FROM = 4.0D;

    private static final double TOLERANCE = 1.0E-9D;

    private ThreatReachVerification() {
    }

    public static void main(String[] args) {
        verifiesSheStartsOutTrustingWhatItSays();
        verifiesOneHitIsEnoughToLearn();
        verifiesAHitFromInsideItsOwnReachTeachesNothing();
        verifiesTheLessonFadesRatherThanSticking();
        verifiesACloserHitDoesNotRenewTheLease();
        verifiesAFurtherHitRaisesAndRenews();
        verifiesNoCreatureCanTeachHerToFearTheHorizon();
        verifiesAFadedLessonStopsTakingUpRoom();
        System.out.println("Threat reach verification passed.");
    }

    /** 没挨过打之前，她照它自己声明的那个数行事——也就是原来的行为。 */
    private static void verifiesSheStartsOutTrustingWhatItSays() {
        require(
                ThreatReachMemory.NOTHING_LEARNED.surplusAt(0L) == 0.0D,
                "还没学过任何东西，她已经在给对方加距离了"
        );
    }

    /**
     * 挨一下就够，不必挨第二下。
     *
     * <p>这是硬证据：它**确实**从那么远打到了她。要求"多次确认"在这里没有意义，
     * 每一次确认都是一次挨打。
     */
    private static void verifiesOneHitIsEnoughToLearn() {
        ThreatReachMemory learned = struck(0L);
        require(
                Math.abs(learned.surplusAt(0L)
                        - (ACTUALLY_STRUCK_FROM - DECLARED)) < TOLERANCE,
                "挨了一下四格外的近战，她学到的差额是 " + learned.surplusAt(0L)
        );
    }

    /** 打在它自己声明的范围里的那一下，什么也没说明。 */
    private static void verifiesAHitFromInsideItsOwnReachTeachesNothing() {
        ThreatReachMemory learned = ThreatReachMemory.NOTHING_LEARNED
                .afterBeingStruckFrom(DECLARED, DECLARED - 0.5D, 0L);
        require(
                learned.surplusAt(0L) == 0.0D,
                "它在自己承认的范围内打了她一下，她却学到了额外的距离"
        );
    }

    /** 没再挨打就慢慢褪回零。 */
    private static void verifiesTheLessonFadesRatherThanSticking() {
        ThreatReachMemory learned = struck(0L);
        double full = learned.surplusAt(0L);
        double half = learned.surplusAt(ThreatReachMemory.FADING_TICKS / 2L);
        require(
                Math.abs(half - full / 2.0D) < 1.0E-6D,
                "褪到一半时还剩 " + half + "，而学到的是 " + full
        );
        require(
                learned.surplusAt(ThreatReachMemory.FADING_TICKS) == 0.0D,
                "两分钟过去了，她还记着"
        );
    }

    /**
     * 更近的一下不刷新计时——"不断更新"与"死记"的分界就在这里。
     *
     * <p>刷新的话，只要她还在挨这个物种的打，那个数就永远褪不掉：一只换了短武器、
     * 或者本来就是另一个变种的个体，会被上一只的记录一直罩着。
     */
    private static void verifiesACloserHitDoesNotRenewTheLease() {
        ThreatReachMemory learned = struck(0L);
        long halfway = ThreatReachMemory.FADING_TICKS / 2L;
        ThreatReachMemory after = learned.afterBeingStruckFrom(
                DECLARED, DECLARED + 0.1D, halfway
        );
        require(
                after == learned,
                "一记更近的攻击改写了记忆"
        );
        require(
                after.surplusAt(ThreatReachMemory.FADING_TICKS) == 0.0D,
                "挨了一下更近的，反而把旧记忆的寿命续上了——那就是死记"
        );
    }

    /** 更远的一下则要抬上去，并重新计时。 */
    private static void verifiesAFurtherHitRaisesAndRenews() {
        ThreatReachMemory learned = struck(0L);
        long halfway = ThreatReachMemory.FADING_TICKS / 2L;
        ThreatReachMemory after = learned.afterBeingStruckFrom(
                DECLARED, ACTUALLY_STRUCK_FROM + 1.0D, halfway
        );
        require(
                Math.abs(after.surplusAt(halfway)
                        - (ACTUALLY_STRUCK_FROM + 1.0D - DECLARED)) < TOLERANCE,
                "它打得更远了，她没跟上"
        );
        require(
                after.surplusAt(halfway + ThreatReachMemory.FADING_TICKS - 1L)
                        > 0.0D,
                "抬上去之后没有重新计时"
        );
    }

    /** 上限。读数出错或者哪个模组用近战伤害实现了一发炮弹，最坏也就到这里。 */
    private static void verifiesNoCreatureCanTeachHerToFearTheHorizon() {
        ThreatReachMemory learned = ThreatReachMemory.NOTHING_LEARNED
                .afterBeingStruckFrom(DECLARED, 500.0D, 0L);
        require(
                learned.surplusAt(0L)
                        <= ThreatReachMemory.MOST_A_SURPLUS_CAN_BE + TOLERANCE,
                "一次五百格的读数教会了她躲开 " + learned.surplusAt(0L) + " 格"
        );
    }

    /** 褪干净的条目要能被丢掉，否则这张表只增不减。 */
    private static void verifiesAFadedLessonStopsTakingUpRoom() {
        ThreatReachMemory learned = struck(0L);
        require(learned.worthKeeping(0L), "刚学到就被判成可以丢");
        require(
                !learned.worthKeeping(ThreatReachMemory.FADING_TICKS),
                "已经褪干净了却还占着格子"
        );
    }

    private static ThreatReachMemory struck(long now) {
        return ThreatReachMemory.NOTHING_LEARNED
                .afterBeingStruckFrom(DECLARED, ACTUALLY_STRUCK_FROM, now);
    }

    private static void require(boolean condition, String complaint) {
        if (!condition) {
            throw new AssertionError(complaint);
        }
    }
}
