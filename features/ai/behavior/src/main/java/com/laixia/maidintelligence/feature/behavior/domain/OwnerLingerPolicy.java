package com.laixia.maidintelligence.feature.behavior.domain;

import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;

/**
 * 主人停下来的时候，她待在哪儿。
 *
 * <p>自由模式把宿主的 {@code RANDOM_STROLL} 整个删掉了，而且删得对：那是第二个
 * 决策者，它和意图层同时往 {@code WALK_TARGET} 上写，撤退因此会中途没了腿。但删掉
 * 之后剩下的是另一个极端——主人一停，她就钉在离他三格的地方一动不动，看上去不像
 * 一个人，像一件家具。
 *
 * <p>所以游走以**她自己的意图**的形式回来，而这里是它的算术：往哪儿走、多远、
 * 以及什么时候干脆回到他身边。没有世界，没有实体，只有两个 [0,1) 的随机数进来、
 * 一个落点出去——于是"她会不会走出感知范围"这种问题可以在这里问清楚，而不是靠
 * 在游戏里盯着看。
 *
 * <p>锚在主人身上，不是锚在她自己身上。锚在自己身上的游走是随机游走，会漂走；
 * 锚在主人身上的游走每一步都重新以他为圆心，所以她永远在他附近打转，而这正是
 * 玩家要的那个画面。
 */
public final class OwnerLingerPolicy {
    /**
     * 她会晃开多远，以主人为圆心。
     *
     * <p>**就是感知本身，不是一个新的数。**每一个射程都以 {@link PerceptionRange}
     * 表达，这是本仓库的既有约定——各行为各写各的距离，就会出现"能捡到比能看见更远的
     * 东西"那类无人写下也无人察觉的矛盾。{@code GazeRecallPolicy} 当年写死八格正是
     * 这么错的，后来改回与感知一致。
     *
     * <p>取满而不是收窄，是一个明确的取舍：**她走得动的范围就是她看得见的范围**。
     * 收窄过的版本（四分之一，四格）确实能保证一次散步不会顺带触发一次追人，但那个
     * 半径下她基本在主人脚边打转，看不出是在活动。
     *
     * <p>**代价如实记在这里**：结伴那条意图从五格开始考虑出发，而它在 band 30、游走
     * 在 band 10，所以她晃出五格之后可能被结伴打断并带回主人身边。于是散步的实际
     * 边界由那条拉力决定，而不是由这个半径决定——好感度不够、结伴不成立时，她才真的
     * 会晃到十六格。这不是缺陷，是这个取舍的另一面；真正不能越过的那条线是拉回兜底的
     * 二十四格，它由 {@code PerceptionRangeVerification} 钉住。
     */
    public static final double RADIUS = PerceptionRange.BLOCKS;

    /**
     * 最近也要走出这么远才算换了个地方。
     *
     * <p>一格半。这一个不是射程而是步幅，量的是"她到底有没有挪窝"，所以它按身体的
     * 尺度写而不从感知推——落点太近时她会原地抽搐：走两步就到了，意图随即完成，
     * 下一轮又选一个近点。下限比上限重要，因为坏的形态出在下限这一侧。
     */
    public static final double MINIMUM_STEP = 1.5D;

    /**
     * 每次挑落点时，直接回到主人身边的概率。
     *
     * <p>三成。玩家要的是"有一定概率会回去到主人身边"，而三次里有一次是能看出来
     * 的节奏——再高就成了跟随，再低就看不出她还惦记着他。
     */
    public static final double RETURN_CHANCE = 0.3D;

    /**
     * 一个落点最多认多久。
     *
     * <p>十秒。抽中的点要一直用到走完，否则每 tick 重抽就是每 tick 改主意；但
     * "一直"必须有个头——她可能**根本走不到**那儿：路被切断、落在悬崖对面、
     * 或者落点脚下压根不是能站的地方。到不了就不会到站，不到站就不会忘记，于是
     * 她被钉在原地反复走向一个够不着的点，看上去就是"她不动了"。
     *
     * <p>十秒是量出来的下限之上：半径十六格、游走速度 0.4（约 0.12 格/tick），
     * 横穿整个圆约需一百三十 tick，所以两百 tick 不会打断一趟正常的散步。
     */
    public static final int PATIENCE_TICKS = 200;

    /**
     * 这一趟走多快，相对计划里写的那个速度。
     *
     * <p>散步不是赶路，所以整体比标称慢；而**每趟不一样**和停顿不等长是同一条理由
     * ——恒定的速度和恒定的停顿一样，一眼就看得出是机器。
     *
     * <p>上限取 1.0 而不是更高：计划里那个数是"她散步时最快也就这样"，这里只往下
     * 调。要她走得比标称还快，那是另一件事（跟随），不该从散步这条路溜进来。
     */
    public static final double SLOWEST_PACE = 0.6D;

    /** 上限：就是计划里写的那个速度。 */
    public static final double QUICKEST_PACE = 1.0D;

    /** 到站之后最短站多久：三秒。 */
    public static final int SHORTEST_REST_TICKS = 60;

    /** 最长：十秒。 */
    public static final int LONGEST_REST_TICKS = 200;

    public static final OwnerLingerPolicy INSTANCE = new OwnerLingerPolicy();

    private OwnerLingerPolicy() {
    }

    /**
     * 这一趟她是回到他身边，还是找个地方待着。
     *
     * @param roll [0,1) 的随机数
     */
    public boolean returnsToOwner(double roll) {
        return roll < RETURN_CHANCE;
    }

    /**
     * 落点相对主人的横向偏移，东向。
     *
     * <p>半径按 {@code sqrt} 取，落点才会在圆内均匀分布。直接用 {@code roll × R}
     * 会把她挤向圆心——那是"随机取半径"和"在圆内随机取点"的差别，看起来就是她总在
     * 主人脚边打转，而不是在他周围活动。
     *
     * @param angleRoll  [0,1) 的随机数，决定方位
     * @param radiusRoll [0,1) 的随机数，决定远近
     */
    public double offsetX(double angleRoll, double radiusRoll) {
        return radius(radiusRoll) * Math.cos(angle(angleRoll));
    }

    /** 同上，南向。 */
    public double offsetZ(double angleRoll, double radiusRoll) {
        return radius(radiusRoll) * Math.sin(angle(angleRoll));
    }

    /**
     * 这一趟的步速倍率。
     *
     * @param roll [0,1) 的随机数
     */
    public double paceFactor(double roll) {
        return SLOWEST_PACE
                + clamp(roll) * (QUICKEST_PACE - SLOWEST_PACE);
    }

    /**
     * 到了之后站多久再挑下一个地方。
     *
     * <p>**停顿必须有，而且不能等长。**没有停顿她就一直在走（评估间隔只有两秒，
     * 到站两秒后就又出发）；等长的停顿——比如给意图配一个固定冷却——无论取多大都
     * 像节拍器：走、停 N 秒、走、停 N 秒。玩家两种都会看出来。
     *
     * <p>所以"停多久"和"去哪儿"是同一次决定里的两个问题，一起抽。三到十秒之间，
     * 均匀取。
     *
     * <p>这里用随机数不违反"概率是缺少一项考量的标记"那条：那条针对的是**要不要
     * 做**——那种问题总有一个该被写出来的判据。而"一个人站着发呆多久"本来就没有
     * 判据，写死一个数才是在假装它有。
     *
     * @param roll [0,1) 的随机数
     */
    public int restTicks(double roll) {
        int span = LONGEST_REST_TICKS - SHORTEST_REST_TICKS;
        return SHORTEST_REST_TICKS + (int) Math.round(clamp(roll) * span);
    }

    /**
     * 这个落点还值得走过去吗。
     *
     * <p>由错误的一侧定义：主人自己走开了，她脚下那个目的地就不再是"他附近"，
     * 而追着一个过期的点走是玩家看得最清楚的一种呆。判据用的是与半径同一个数，
     * 因为这两句话本来就是同一句。
     *
     * <p>同一条也用来筛掉刚抽出来的点。水平位置是抽的，高度只能由地形回答，而
     * 解析到地面之后它可能跑到主人楼上或崖底去——那时它已经不是"他附近"了。
     * 这里不需要第三个数（"高度差上限"），因为要问的问题和上面完全一样，只是
     * 问的时机不同。
     *
     * @param distanceToOwner 落点到主人此刻位置的格数
     */
    public boolean stillNearOwner(double distanceToOwner) {
        return Double.isFinite(distanceToOwner) && distanceToOwner <= RADIUS;
    }

    private static double angle(double roll) {
        return clamp(roll) * 2.0D * Math.PI;
    }

    private static double radius(double roll) {
        double even = Math.sqrt(clamp(roll));
        return MINIMUM_STEP + even * (RADIUS - MINIMUM_STEP);
    }

    private static double clamp(double roll) {
        if (!Double.isFinite(roll)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, roll));
    }
}
