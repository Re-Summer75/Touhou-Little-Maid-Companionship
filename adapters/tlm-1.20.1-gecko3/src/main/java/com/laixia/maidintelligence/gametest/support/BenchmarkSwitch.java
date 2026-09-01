package com.laixia.maidintelligence.gametest.support;

/**
 * 基准局跑不跑，由环境变量说了算。
 *
 * <p>它们不是回归测试，是标尺：红是常态，读数才是产出（见
 * {@code docs/combat/tactics-log.md}）。可它们占掉整整一轮 GameTest 的四分之三——
 * 实测一轮 4 分 48 秒里，一百四十六条 required 测试只花了 25 秒，十八局基准花了
 * 3 分 35 秒。原因不在局数，在**每一局各占一个 batch，而 batch 之间是串行的**：
 * 一局卫道士 1100 tick，一个 batch 就是十四五秒。
 *
 * <p>局数曾经减半来换时间，只省下三十秒——因为省的是 tick，而串行的 batch 一个都
 * 没少。所以真正的开关在这里：平时那一轮不跑基准，要读数的时候才跑。
 *
 * <p>用环境变量而不是系统属性，是因为 {@code ./gradlew -Dxxx} 只到 Gradle 自己的
 * JVM，进不了 fork 出来的游戏进程；而环境变量是继承的。
 *
 * <pre>{@code
 * ./gradlew runGameTestServer                        # 不跑基准，约一分半
 * COMPANIONSHIP_BENCHMARK=1 ./gradlew runGameTestServer   # 跑，出读数
 * }</pre>
 *
 * <p>跳过的那一局仍然 {@code succeed()}，不是失败：基准本来就是
 * {@code required = false}，把它标红会淹掉真正该看的那一行。
 */
public final class BenchmarkSwitch {
    private static final String FLAG = "COMPANIONSHIP_BENCHMARK";

    private static final String DOJO = "COMPANIONSHIP_DOJO";

    private static final String PATHS = "COMPANIONSHIP_PATHBENCH";

    private BenchmarkSwitch() {
    }

    /** 这一轮是不是来量读数的。 */
    public static boolean measuring() {
        return set(FLAG);
    }

    /**
     * 这一轮跑不跑跳劈靶场。
     *
     * <p>单独一个开关，因为靶场是拿来**反复迭代**的：只开它就不用陪跑两条大基准，
     * 一轮回到两分半。两条大基准开着时它也跟着跑，省得读数分家。
     *
     * <pre>{@code
     * COMPANIONSHIP_DOJO=1 ./gradlew runGameTestServer      # 只跑靶场
     * COMPANIONSHIP_BENCHMARK=1 ./gradlew runGameTestServer # 基准 + 靶场
     * }</pre>
     */
    public static boolean training() {
        return measuring() || set(DOJO);
    }

    /**
     * 这一轮跑不跑**寻路**基准。
     *
     * <p>又一个单独的开关，理由同靶场：量寻路的时候不该陪跑战斗。实测
     * 一轮开着 {@code COMPANIONSHIP_BENCHMARK} 要四十多分钟，而十八条寻
     * 路读数在头几分钟就出完了，剩下全耗在战斗基准与跳劈靶场上。
     *
     * <pre>{@code
     * COMPANIONSHIP_PATHBENCH=1 ./gradlew runGameTestServer  # 只量寻路
     * }</pre>
     */
    public static boolean pathing() {
        return measuring() || set(PATHS);
    }

    private static boolean set(String name) {
        String flag = System.getenv(name);
        return flag != null && !flag.isBlank() && !"0".equals(flag)
                && !"false".equalsIgnoreCase(flag);
    }
}
