package com.laixia.maidintelligence.feature.behavior.domain.owner;

/**
 * 主人是不是"正在赶路"，而不是"这一 tick 在动"。
 *
 * <p>瞬时速度已经有了（{@code fact/owner_speed}），但它回答不了跟随该问的那个问题。
 * 一个站在原地放方块的人每隔几 tick 就会挪半格，一个真的要走了的人会连着走上好几秒
 * ——两者的瞬时读数常常一样，而她该做的事完全不同。
 *
 * <p>所以这里数的是**累计移动**，而不是"这一刻在不在动"：动够
 * {@link #SUSTAINED_TICKS} 就认为他上路了，而中断只有满 {@link #BREAK_TICKS}
 * 才让整段作废。两条合起来是一句话——**中断不满两秒的都不算中断**。
 *
 * <p>不把"认"和"收"写成两个不同的门槛，是因为它们本来就是同一个判断。而中断要
 * 留出容忍，是因为主人每一次停下来放个方块都让她换一次主意的话，抖动比迟钝更糟：
 * "换主意"在意图层是有成本的（承诺时长、切换裕度、冷却）。
 *
 * <p>按**经过的 tick 数**累加，不按调用次数：事实读取的节奏不由这里决定，而
 * "三秒"必须是三秒。
 */
public record OwnerTravelWatch(
        boolean onTheMove,
        int movingTicks,
        int stillTicks
) {
    /**
     * 快到什么程度才算"在动"，以疾跑为 1。
     *
     * <p>四分之一。原版走路约是疾跑的 0.77，潜行约 0.23，所以这条线把"走"和"跑"
     * 都收进来，而把原地转身、挖矿时的小幅位移挡在外面。它不是一个物理量，是
     * "他有没有在往某个地方去"这句话的可执行形式。
     */
    public static final double MOVING_SPEED = 0.25D;

    /** 累计动够这么久才认为他上路了：三秒。 */
    public static final int SUSTAINED_TICKS = 60;

    /**
     * 中断多久算"这一段不作数了"：两秒。
     *
     * <p>**起判和收判用的是同一个数**，因为它们本来就是同一句话：中断不满两秒的
     * 都不算中断。跳一下、开个门、绕根柱子、停下来放个方块——这些都在两秒以内，
     * 计时因此不清零；真的坐下来不走了，两秒一到整段重数。
     *
     * <p>推论是"持续三秒"并不要求严格连续：走一秒、停一秒半、再走一秒，累计仍在
     * 往上加。这是有意的——严格连续的判据在有地形的地方几乎不可能满足，而玩家
     * 眼里那明明就是"他在赶路"。
     */
    public static final int BREAK_TICKS = 40;

    /** 还没有看过他。 */
    public static final OwnerTravelWatch UNSEEN =
            new OwnerTravelWatch(false, 0, 0);

    /**
     * 又看了他一眼。
     *
     * @param speed   这一段里的地面速度，以疾跑为 1
     * @param elapsed 距上一次读数过了多少 tick，非正数按一 tick 记
     * @return 新的判据状态；本记录不可变，调用方持有返回值
     */
    public OwnerTravelWatch advance(double speed, long elapsed) {
        int step = (int) Math.max(1L, Math.min(elapsed, SUSTAINED_TICKS));
        boolean moving = Double.isFinite(speed) && speed >= MOVING_SPEED;
        if (moving) {
            int walked = saturate(movingTicks + step);
            return new OwnerTravelWatch(walked >= SUSTAINED_TICKS, walked, 0);
        }
        // 停着。中断不满两秒的不算中断，累计原样保留——他跳一下、开个门、
        // 绕根柱子，都不该让"他在赶路"这件事从头数起。满两秒才整段作废。
        int paused = saturate(stillTicks + step);
        int walked = paused >= BREAK_TICKS ? 0 : movingTicks;
        return new OwnerTravelWatch(
                walked >= SUSTAINED_TICKS, walked, paused
        );
    }

    /**
     * 读数断了，从头数。
     *
     * <p>主人下线、跨维度、或者两次读数隔得太远，位移就不再是"他走了多少"。这种
     * 时候归零而不是保留，因为保留会把一段没看见的时间当成他在走。
     */
    public OwnerTravelWatch lost() {
        return UNSEEN;
    }

    /** 供事实层使用的 0/1。 */
    public double flag() {
        return onTheMove ? 1.0D : 0.0D;
    }

    /** 计数只用来和门槛比大小，不必无限增长。 */
    private static int saturate(int ticks) {
        return Math.min(ticks, SUSTAINED_TICKS * 4);
    }
}
