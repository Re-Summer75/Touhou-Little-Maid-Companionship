package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.combat.WithdrawCommitPolicy;

/**
 * 走还是站住，守得住吗。
 */
public final class WithdrawCommitVerification {
    private static final WithdrawCommitPolicy POLICY =
            WithdrawCommitPolicy.INSTANCE;

    /**
     * 卫道士局第一局 tick 90–101 的接近速率，原样抄下来。
     *
     * <p>这不是构造的边界情形，是量到的那一段：正是这十二 tick 里她站住六 tick、
     * 掉了全部血量的四成半。判据在第 5 项翻正、第 11 项翻回负，中间连续为正六次。
     */
    private static final double[] MEASURED_CLOSING = {
            -4.5D, -1.6D, -1.4D, -1.5D, -1.8D,
            1.2D, 2.1D, 3.0D, 4.0D, 5.0D, 4.0D,
            -1.5D
    };

    private WithdrawCommitVerification() {
    }

    public static void main(String[] args) {
        verifiesTheMeasuredFlickerNoLongerStopsHer();
        verifiesHoldWindowIgnoresASoftFlip();
        verifiesRawReadingWinsOnceTheWindowIsSpent();
        verifiesNoRoomOverridesTheHold();
        verifiesHeldOnlyResetsOnARealFlip();
        System.out.println("Withdraw commit verification passed.");
    }

    /**
     * 那六 tick 不许再发生。
     *
     * <p>断言的是"整段里她一次都没停下来"，而不是"停的次数变少了"。前者是这条
     * 姿态的规则，后者只是这一次的读数。
     */
    private static void verifiesTheMeasuredFlickerNoLongerStopsHer() {
        boolean leaving = false;
        int held = Integer.MAX_VALUE;
        int planted = 0;
        for (int step = 0; step < MEASURED_CLOSING.length; step++) {
            boolean raw = MEASURED_CLOSING[step] <= 0.0D;
            boolean was = leaving;
            leaving = POLICY.leaving(was, held, raw, true);
            held = POLICY.nextHeld(was, leaving, held == Integer.MAX_VALUE
                    ? 0
                    : held);
            if (!leaving) {
                planted++;
            }
        }
        expect(
                planted == 0,
                "接近速率翻正的那六 tick 里她仍然站住了 " + planted
                        + " tick——姿态没守住，实测的那次冻结会原样重演"
        );
    }

    /**
     * 保持期内翻转判据，"走"这个姿态不动——而"站住"不受保护。
     *
     * <p>最后一条断言的方向是反的，且必须是反的。第一版两侧都守，实测
     * {@code rootedTicks} 从 4 涨到 524–592：站住没有执行者，守住它等于守住一段
     * 没有腿的时间。这条断言就是不许那一版回来。
     */
    private static void verifiesHoldWindowIgnoresASoftFlip() {
        expect(
                POLICY.leaving(true, 0, false, true),
                "刚决定要走，判据翻一下就站住了"
        );
        expect(
                POLICY.leaving(true, WithdrawCommitPolicy.HOLD_TICKS - 1,
                        false, true),
                "保持期最后一 tick 就已经守不住了"
        );
        expect(
                POLICY.leaving(false, 0, true, true),
                "站着不许有保持期：判据一说能走就得走。守住'站住'的那一版实测把"
                        + "生根 tick 从 4 推到了 524，比不守还差"
        );
    }

    /** 保持期一满，判据说什么就是什么。 */
    private static void verifiesRawReadingWinsOnceTheWindowIsSpent() {
        expect(
                !POLICY.leaving(true, WithdrawCommitPolicy.HOLD_TICKS,
                        false, true),
                "守过头了：保持期是下界不是锁，过了就该听判据的"
        );
        expect(
                !POLICY.leaving(false, WithdrawCommitPolicy.HOLD_TICKS,
                        false, true),
                "判据说走不掉，她还是走了"
        );
    }

    /**
     * 没地方退是硬事实，不受保持期保护。
     *
     * <p>软判据可以守，硬事实不能——守住一个"往哪走"的姿态而那个方向已经不存在，
     * 就是让她走进墙里。
     */
    private static void verifiesNoRoomOverridesTheHold() {
        expect(
                !POLICY.leaving(true, 0, true, false),
                "退路没了还守着'走'这个姿态"
        );
    }

    /**
     * 计数只在真翻转时归零。
     *
     * <p>守住期间还归零的话，保持期永远走不完，姿态等于没守——这是这类回差最容易
     * 写错的一处，所以单独钉。
     */
    private static void verifiesHeldOnlyResetsOnARealFlip() {
        expect(
                POLICY.nextHeld(true, true, 5) == 6,
                "姿态没变，计数却没往前走"
        );
        expect(
                POLICY.nextHeld(true, false, 5) == 0,
                "姿态翻了，计数没归零"
        );
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
