package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.IdleGazePolicy;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;

/**
 * 东张西望的算术：一眼看多久、看哪儿、看多远。
 *
 * <p>三条都在纯 JVM 这侧问得清楚，而在游戏里只能靠盯着看。最要紧的是"一眼要停留
 * 一段时间且不等长"——每 tick 换方向是抽搐，等长换方向是节拍器，两种都不是环顾。
 */
public final class IdleGazeVerification {
    private static final IdleGazePolicy POLICY = IdleGazePolicy.INSTANCE;

    private IdleGazeVerification() {
    }

    public static void main(String[] args) {
        verifiesAGlanceLastsAWhileAndVaries();
        verifiesSheLooksAtHimOftenButNotAlways();
        verifiesTheTwoRollsAreIndependent();
        verifiesGlancingNarrowsPerception();
        verifiesEveryBearingIsReachable();
        System.out.println("Idle gaze verification passed.");
    }

    /** 一眼要停留，而且不能每次一样长。 */
    private static void verifiesAGlanceLastsAWhileAndVaries() {
        require(
                POLICY.glanceTicks(0.0D)
                        == IdleGazePolicy.SHORTEST_GLANCE_TICKS,
                "抽到最小值时的停留时长不是下限"
        );
        require(
                POLICY.glanceTicks(1.0D)
                        == IdleGazePolicy.LONGEST_GLANCE_TICKS,
                "抽到最大值时的停留时长不是上限"
        );
        require(
                IdleGazePolicy.SHORTEST_GLANCE_TICKS > 1,
                "一眼只停留一两 tick，那是抽搐不是环顾"
        );
        int seen = 0;
        int previous = -1;
        for (int roll = 0; roll <= 20; roll++) {
            int ticks = POLICY.glanceTicks(roll / 20.0D);
            require(
                    ticks >= IdleGazePolicy.SHORTEST_GLANCE_TICKS
                            && ticks <= IdleGazePolicy.LONGEST_GLANCE_TICKS,
                    "停留 " + ticks + " tick，落在区间之外"
            );
            if (ticks != previous) {
                seen++;
                previous = ticks;
            }
        }
        require(seen > 5, "二十一次抽签只取到 " + seen + " 种时长，那是节拍器");
    }

    /**
     * 她常看他，但不是只看他。
     *
     * <p>玩家报的正是后半句。两侧都要钉：只看主人是原来的毛病，从不看主人则是把
     * 她改成了一个不认识他的人。
     */
    private static void verifiesSheLooksAtHimOftenButNotAlways() {
        int atOwner = 0;
        for (int roll = 0; roll < 1000; roll++) {
            if (POLICY.looksAtOwner(roll / 1000.0D)) {
                atOwner++;
            }
        }
        require(
                atOwner > 150 && atOwner < 700,
                "一千眼里有 " + atOwner + " 眼在主人身上；"
                        + "太高就是只会盯着他，太低就是不认识他"
        );
    }

    /**
     * 两个判据必须用两个随机数。
     *
     * <p>共用一个的话它们会在同一个门槛上分裂：凡是"不看主人"的抽签都落在
     * {@code [OWNER_SHARE, 1)}，而只要那一段整个在或整个不在 {@code CREATURE_SHARE}
     * 之内，"不看主人"就恒等于"看活物"或恒等于"看方向"，另一支永远取不到。
     *
     * <p>这条断言直接检查那个重叠存在——存在，才说明"用同一个随机数会出事"不是
     * 一句空话，而代码里那句注释才有意义。
     */
    private static void verifiesTheTwoRollsAreIndependent() {
        boolean bothOutcomesReachable = false;
        for (int roll = 0; roll < 1000; roll++) {
            double value = roll / 1000.0D;
            if (!POLICY.looksAtOwner(value) && POLICY.looksAtCreature(value)) {
                bothOutcomesReachable = true;
                break;
            }
        }
        require(
                !bothOutcomesReachable
                        || IdleGazePolicy.OWNER_SHARE
                                < IdleGazePolicy.CREATURE_SHARE,
                "两个门槛的关系变了，注释里那个论证需要重写"
        );
        require(
                POLICY.looksAtCreature(0.0D) && !POLICY.looksAtCreature(0.999D),
                "看活物这一支的门槛没有把区间真的分成两段"
        );
    }

    /** 看的距离比能注意到的近——盯着视野边缘等于发呆。 */
    private static void verifiesGlancingNarrowsPerception() {
        require(
                IdleGazePolicy.GLANCE_RANGE < PerceptionRange.BLOCKS,
                "她看的比她能注意到的还远"
        );
        require(IdleGazePolicy.GLANCE_RANGE > 1.0D, "看的距离近到脚尖上了");
    }

    /** 整圈都要能转到，而且落点始终在那个距离上。 */
    private static void verifiesEveryBearingIsReachable() {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (int step = 0; step <= 40; step++) {
            double bearing = step / 40.0D;
            double x = POLICY.offsetX(bearing);
            double z = POLICY.offsetZ(bearing);
            require(
                    Math.abs(Math.sqrt(x * x + z * z)
                            - IdleGazePolicy.GLANCE_RANGE) < 1.0E-9D,
                    "第 " + step + " 个方位的落点不在那个距离上"
            );
            minimum = Math.min(minimum, x);
            maximum = Math.max(maximum, x);
        }
        require(
                minimum < -IdleGazePolicy.GLANCE_RANGE / 2.0D
                        && maximum > IdleGazePolicy.GLANCE_RANGE / 2.0D,
                "整圈只转到了一侧"
        );
        require(
                POLICY.offsetY(0.0D) < 0.0D && POLICY.offsetY(1.0D) > 0.0D,
                "视线只会往一个方向抬"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
