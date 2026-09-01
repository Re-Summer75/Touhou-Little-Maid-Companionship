package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 寻路自己花了多少时间——**量我们的代码，按女仆分账**。
 *
 * <p>先前拿服务器的 tick 时长当读数，量到的是别的东西：GameTest 框架的
 * 开销、上一局的收尾、垃圾回收。改成计自己的纳秒之后又栽了第二跤：账是
 * **全局**的，而 {@code VoxelAstar} 全模组共用——对照组明明不走自有引
 * 擎，却记到了十九次规划；执行侧的调用次数是本局期望的三倍多。同一 JVM
 * 里别的批次一有女仆铺路，就全算进来了。
 *
 * <p>所以按 UUID 分账：谁的开销记在谁头上，基准局只把自己那几只的账加
 * 起来。这也是测试章程第三条——**测量的边界要划在被测对象上**。
 *
 * <p>两条链都记：自由模式的体素引擎，和对照链（本体 A* ＋ 分段执行器）
 * ——同一本账、同一单位，ours/stock 才可比。计时点全在
 * {@code SureFootedNavigation} 的边界上。
 */
public final class PathClock {
    private record Tally(long nanos, long calls) {
        Tally plus(long more) {
            return new Tally(nanos + more, calls + 1L);
        }
    }

    private static final Map<UUID, Tally> PLANS = new ConcurrentHashMap<>();
    private static final Map<UUID, Tally> WALKS = new ConcurrentHashMap<>();

    private PathClock() {
    }

    /** 记一次铺路。 */
    public static void plan(UUID who, long nanos) {
        PLANS.merge(who, new Tally(nanos, 1L),
                (was, one) -> was.plus(one.nanos()));
    }

    /** 记一次执行（一只女仆的一 tick）。 */
    public static void walk(UUID who, long nanos) {
        WALKS.merge(who, new Tally(nanos, 1L),
                (was, one) -> was.plus(one.nanos()));
    }

    /** 把这几只的账清零，开始一段新的测量。 */
    public static void reset(Iterable<UUID> whom) {
        for (UUID one : whom) {
            PLANS.remove(one);
            WALKS.remove(one);
        }
    }

    /** 这几只铺路一共花了多少毫秒。 */
    public static double planMillis(Iterable<UUID> whom) {
        return sum(PLANS, whom, true);
    }

    public static long planCalls(Iterable<UUID> whom) {
        return (long) sum(PLANS, whom, false);
    }

    /** 这几只执行一共花了多少毫秒。 */
    public static double walkMillis(Iterable<UUID> whom) {
        return sum(WALKS, whom, true);
    }

    public static long walkCalls(Iterable<UUID> whom) {
        return (long) sum(WALKS, whom, false);
    }

    private static double sum(Map<UUID, Tally> book, Iterable<UUID> whom,
            boolean millis) {
        double total = 0.0D;
        for (UUID one : whom) {
            Tally tally = book.get(one);
            if (tally == null) {
                continue;
            }
            total += millis ? tally.nanos() / 1.0E6D : tally.calls();
        }
        return total;
    }
}
