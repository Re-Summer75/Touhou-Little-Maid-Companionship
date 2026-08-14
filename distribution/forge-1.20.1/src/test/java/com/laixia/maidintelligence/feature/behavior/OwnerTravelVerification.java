package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.OwnerLingerPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.owner.OwnerTravelWatch;

/**
 * "他在赶路"和"她在他附近晃"——两段算术，都不需要世界。
 *
 * <p>玩家报的是"跟随的响应不好"。响应不好有两半：起步太晚（她要等主人走出八格），
 * 和主人停下之后她钉在原地不动。前一半靠这个判据修，后一半靠那个落点修，而两者
 * 都是纯算术，所以在这里问清楚比在游戏里盯着看便宜得多。
 *
 * <p>最要紧的是**回差**那一条。判据只有一个门槛时，主人每一次停下来放方块都会让她
 * 改一次主意，而改主意在意图层是有成本的（承诺时长、切换裕度、冷却）——抖动比迟钝
 * 更难看。所以下面既有"三秒才认"，也有"停一下不算数"。
 */
public final class OwnerTravelVerification {
    /** 走路约是疾跑的 0.77，用它当"他在走"的读数。 */
    private static final double WALKING = 0.77D;

    /** 原地转身、挖矿时的小幅位移，落在门槛之下。 */
    private static final double FIDGETING = 0.1D;

    private OwnerTravelVerification() {
    }

    public static void main(String[] args) {
        verifiesOneStepIsNotADeparture();
        verifiesThreeSecondsOfWalkingIs();
        verifiesAShortBreakDoesNotRestartTheClock();
        verifiesABriefPauseDoesNotCallItOff();
        verifiesAFullBreakStartsOver();
        verifiesTheClockCountsTicksNotReadings();
        verifiesALostReadingStartsOver();
        verifiesAStrollStaysInsideHerOwnersCircle();
        verifiesAStrollActuallyGoesSomewhere();
        verifiesSheSometimesGoesBackToHim();
        verifiesSheStandsAroundForAWhile();
        verifiesSheAmblesRatherThanMarches();
        verifiesASpotHerOwnerLeftIsAbandoned();
        System.out.println("Owner travel verification passed.");
    }

    /** 挪了一下不是要走了——这正是瞬时速度分不开的那两件事。 */
    private static void verifiesOneStepIsNotADeparture() {
        OwnerTravelWatch watch = OwnerTravelWatch.UNSEEN
                .advance(WALKING, 1L)
                .advance(FIDGETING, 1L)
                .advance(WALKING, 1L);
        require(!watch.onTheMove(), "他挪了三下，她就认定他要走了");
    }

    /** 累计走够三秒才认。 */
    private static void verifiesThreeSecondsOfWalkingIs() {
        require(
                walkFor(OwnerTravelWatch.SUSTAINED_TICKS).onTheMove(),
                "他走够了三秒，她还没认为他在赶路"
        );
        require(
                !walkFor(OwnerTravelWatch.SUSTAINED_TICKS - 1).onTheMove(),
                "差一 tick 就认了——门槛不是三秒"
        );
    }

    /**
     * 中断不满两秒的不算中断，计时不清零。
     *
     * <p>起判这一侧：走一半停一下再接着走，累计应当接着往上加，而不是从头数。
     * 跨一次跳跃、一次开门、一次绕柱子都是这种"停一下"，严格连续的判据在有地形
     * 的地方几乎不可能满足。
     */
    private static void verifiesAShortBreakDoesNotRestartTheClock() {
        OwnerTravelWatch half = walkFor(OwnerTravelWatch.SUSTAINED_TICKS / 2);
        OwnerTravelWatch resumed = still(
                half, OwnerTravelWatch.BREAK_TICKS - 1
        );
        require(
                !resumed.onTheMove(),
                "只走了一半，停一下反而让她认定他在赶路"
        );
        for (int tick = 0; tick < OwnerTravelWatch.SUSTAINED_TICKS / 2; tick++) {
            resumed = resumed.advance(WALKING, 1L);
        }
        require(
                resumed.onTheMove(),
                "他走一半、停了不到两秒、又走完另一半，计时却从头数了"
        );
    }

    /** 认了之后同样：停不满两秒不收回。 */
    private static void verifiesABriefPauseDoesNotCallItOff() {
        OwnerTravelWatch moving = walkFor(OwnerTravelWatch.SUSTAINED_TICKS);
        OwnerTravelWatch paused = still(
                moving, OwnerTravelWatch.BREAK_TICKS - 1
        );
        require(paused.onTheMove(), "他停了不到两秒，她就不跟了");
        require(
                paused.advance(WALKING, 1L).onTheMove(),
                "他接着走，而她已经把计数丢了"
        );
    }

    /** 停满两秒就是真的停下了，整段作废。 */
    private static void verifiesAFullBreakStartsOver() {
        OwnerTravelWatch stopped = still(
                walkFor(OwnerTravelWatch.SUSTAINED_TICKS),
                OwnerTravelWatch.BREAK_TICKS
        );
        require(!stopped.onTheMove(), "他已经站住两秒，她还当他在赶路");
        require(
                !stopped.advance(WALKING, 1L).onTheMove(),
                "整段本该作废，他抬脚第一步就又算他在赶路了"
        );
    }

    /**
     * 三秒必须是三秒，不是"六十次调用"。
     *
     * <p>事实的读取节奏不由这个判据决定。按调用次数数的话，读得密就变成一秒半、
     * 读得疏就永远认不了——而那正是最难在游戏里看出来的一种错。
     */
    private static void verifiesTheClockCountsTicksNotReadings() {
        OwnerTravelWatch sparse = OwnerTravelWatch.UNSEEN
                .advance(WALKING, 20L)
                .advance(WALKING, 20L)
                .advance(WALKING, 20L);
        require(sparse.onTheMove(), "三秒被数成了三次");
    }

    /** 读数断了就从头数：没看见的那一段不能算他在走。 */
    private static void verifiesALostReadingStartsOver() {
        require(
                !walkFor(OwnerTravelWatch.SUSTAINED_TICKS).lost().onTheMove(),
                "他下线又上线，她还记得他在赶路"
        );
    }

    /**
     * 游走不许把她晃出主人那一圈。
     *
     * <p>上限重要，是因为越界的后果不是"走远一点"：拉回的兜底在二十四格、感知在
     * 十六格，一个会把自己晃到被瞬移回来的游走，是在用一个功能制造另一个功能的
     * 故障。整圈扫一遍，不挑几个点。
     */
    private static void verifiesAStrollStaysInsideHerOwnersCircle() {
        for (int step = 0; step <= 40; step++) {
            double bearing = step / 40.0D;
            for (int reach = 0; reach <= 20; reach++) {
                double roll = reach / 20.0D;
                double x = OwnerLingerPolicy.INSTANCE.offsetX(bearing, roll);
                double z = OwnerLingerPolicy.INSTANCE.offsetZ(bearing, roll);
                double away = Math.sqrt(x * x + z * z);
                require(
                        away <= OwnerLingerPolicy.RADIUS + 1.0E-9D,
                        "落点离主人 " + away + " 格，超出了 "
                                + OwnerLingerPolicy.RADIUS
                );
            }
        }
    }

    /** 也不许挑一个原地的落点——那看起来是抽搐，不是散步。 */
    private static void verifiesAStrollActuallyGoesSomewhere() {
        for (int step = 0; step <= 40; step++) {
            double bearing = step / 40.0D;
            double x = OwnerLingerPolicy.INSTANCE.offsetX(bearing, 0.0D);
            double z = OwnerLingerPolicy.INSTANCE.offsetZ(bearing, 0.0D);
            double away = Math.sqrt(x * x + z * z);
            require(
                    away >= OwnerLingerPolicy.MINIMUM_STEP - 1.0E-9D,
                    "最近的落点只有 " + away + " 格，她会在原地抽搐"
            );
        }
    }

    /** "有一定概率会回去到主人身边"——这一条就是那个概率。 */
    private static void verifiesSheSometimesGoesBackToHim() {
        OwnerLingerPolicy policy = OwnerLingerPolicy.INSTANCE;
        require(policy.returnsToOwner(0.0D), "抽到最小值也不回去");
        require(!policy.returnsToOwner(0.999D), "抽到最大值也往回走");
        int returns = 0;
        for (int roll = 0; roll < 1000; roll++) {
            if (policy.returnsToOwner(roll / 1000.0D)) {
                returns++;
            }
        }
        require(
                returns > 100 && returns < 500,
                "一千次里回去了 " + returns
                        + " 次；再高就是跟随，再低就看不出她还惦记着他"
        );
    }

    /**
     * 到站之后要站一会儿，而且不能每次一样长。
     *
     * <p>两个极端都实测过：没有停顿她一直在走（评估间隔只有两秒），而给意图配一个
     * 固定冷却是等长的停顿，无论取多大都像节拍器。所以这里钉的是**有下限、有上限、
     * 且中间真的会取到别的值**。
     */
    private static void verifiesSheStandsAroundForAWhile() {
        OwnerLingerPolicy policy = OwnerLingerPolicy.INSTANCE;
        require(
                policy.restTicks(0.0D) == OwnerLingerPolicy.SHORTEST_REST_TICKS,
                "抽到最小值时的发呆时长不是下限"
        );
        require(
                policy.restTicks(1.0D) == OwnerLingerPolicy.LONGEST_REST_TICKS,
                "抽到最大值时的发呆时长不是上限"
        );
        require(
                OwnerLingerPolicy.SHORTEST_REST_TICKS > 0
                        && OwnerLingerPolicy.SHORTEST_REST_TICKS
                                < OwnerLingerPolicy.LONGEST_REST_TICKS,
                "发呆时长没有区间，那就是一个固定冷却换了个名字"
        );
        int seen = 0;
        int previous = -1;
        for (int roll = 0; roll <= 20; roll++) {
            int rest = policy.restTicks(roll / 20.0D);
            require(
                    rest >= OwnerLingerPolicy.SHORTEST_REST_TICKS
                            && rest <= OwnerLingerPolicy.LONGEST_REST_TICKS,
                    "发呆 " + rest + " tick，落在区间之外"
            );
            if (rest != previous) {
                seen++;
                previous = rest;
            }
        }
        require(seen > 5, "二十一次抽签只取到 " + seen + " 种时长");
    }

    /**
     * 散步比标称速度慢，而且每趟不一样。
     *
     * <p>与"发呆不等长"是同一条理由：恒定的速度和恒定的停顿一样，一眼就看得出是
     * 机器。上限钉在 1.0——计划里那个数是她散步时最快也就这样，要走得更快那是跟随
     * 的事，不该从散步这条路溜进来。
     */
    private static void verifiesSheAmblesRatherThanMarches() {
        OwnerLingerPolicy policy = OwnerLingerPolicy.INSTANCE;
        require(
                OwnerLingerPolicy.QUICKEST_PACE <= 1.0D,
                "散步允许走得比计划里的标称速度还快"
        );
        require(
                OwnerLingerPolicy.SLOWEST_PACE > 0.0D
                        && OwnerLingerPolicy.SLOWEST_PACE
                                < OwnerLingerPolicy.QUICKEST_PACE,
                "步速没有区间，那就是又一个固定值"
        );
        int seen = 0;
        double previous = -1.0D;
        for (int roll = 0; roll <= 20; roll++) {
            double pace = policy.paceFactor(roll / 20.0D);
            require(
                    pace >= OwnerLingerPolicy.SLOWEST_PACE
                            && pace <= OwnerLingerPolicy.QUICKEST_PACE,
                    "步速倍率 " + pace + " 落在区间之外"
            );
            if (pace != previous) {
                seen++;
                previous = pace;
            }
        }
        require(seen > 5, "二十一次抽签只取到 " + seen + " 种步速");
    }

    /** 主人自己走开之后，脚下那个落点就不再是"他附近"。 */
    private static void verifiesASpotHerOwnerLeftIsAbandoned() {
        OwnerLingerPolicy policy = OwnerLingerPolicy.INSTANCE;
        require(
                policy.stillNearOwner(OwnerLingerPolicy.RADIUS - 0.5D),
                "他还在旁边，她就把落点丢了"
        );
        require(
                !policy.stillNearOwner(OwnerLingerPolicy.RADIUS + 2.0D),
                "他已经走开，她还追着一个过期的落点"
        );
        require(
                !policy.stillNearOwner(Double.NaN),
                "量不到距离时她仍然认为落点有效"
        );
    }

    private static OwnerTravelWatch walkFor(int ticks) {
        OwnerTravelWatch watch = OwnerTravelWatch.UNSEEN;
        for (int tick = 0; tick < ticks; tick++) {
            watch = watch.advance(WALKING, 1L);
        }
        return watch;
    }

    private static OwnerTravelWatch still(OwnerTravelWatch from, int ticks) {
        OwnerTravelWatch watch = from;
        for (int tick = 0; tick < ticks; tick++) {
            watch = watch.advance(0.0D, 1L);
        }
        return watch;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
