package com.laixia.maidintelligence.feature.behavior.domain.combat;

/**
 * "走还是站住"守多久才允许改主意。
 *
 * <p>脱离与站定之间的判据是**接近速率的符号**：为正说明它真的在追上她，那时候撤退
 * 只是在挑一个被堵住的地方；为负说明她拉得开，那就该走。这个判据本身是对的——它是
 * 实测的相对位移投影，比属性速度可靠得多。
 *
 * <p>坏在它是个**在零附近穿来穿去的符号**。两个速度相近的东西互相追逐时，那个符号
 * 每几 tick 就翻一次。实测四局，她活着的那段里翻了 16／21／33／27 次。而这个符号
 * 直接门控着"要不要写移动目标"：
 *
 * <pre>
 * t=94  closing=-1.8  走  退点 157.5,5.5
 * t=95  closing=+1.2  站  退点 166.5,1.5
 * t=96  closing=+2.1  站  退点 —          ← 上一个被寻路失败抹掉，无人重写
 * t=97..100                站  退点 —      ← 位移 0.0–0.3／tick，敌人 1.8 → 0.6
 * t=101 closing=-1.5  走
 * </pre>
 *
 * <p>抹掉退点的是宿主的 {@code MoveToTargetSink}——寻路要不到路径它就清空
 * {@code WALK_TARGET}。平时下一 tick 会重写，可站定那一支**根本不写**，于是符号一
 * 翻，她就在四把斧头面前站满六 tick。那六 tick 里她挨了九点，占她全部血量的四成半。
 *
 * <p>所以这里给的是 {@code behavior-spec.md} 第五节那条通用解法：**判据只用来开始
 * 和刷新一个姿态，不用来逐 tick 驱动动作。**姿态一旦成立就守 {@link #HOLD_TICKS}，
 * 软判据中途翻转不算退出；只有硬事实立刻作废。
 *
 * <h2>只守一侧，这是量出来的</h2>
 *
 * <p>第一版两侧都守，结果比不守还差：卫道士局 {@code rootedTicks} 从 4 涨到 524–592
 * （共 700），单挑卫道士那条场景测试从挨打 9 点涨到 23.5、`rootedTicks=173/200`。
 *
 * <p>原因是这两个姿态**不对称**。"走"有人执行——{@code giveGround} 每 tick 写一次
 * 移动目标；"站住"没有执行者，它只是不写。而宿主的 {@code MoveToTargetSink} 一遇到
 * 寻路失败就清空 {@code WALK_TARGET}，所以"站住"守住的不是一个姿态，是**一段没有腿
 * 的时间**。给它加保持期，等于把原来六 tick 的意外冻结做成十 tick 的设计。
 *
 * <p>所以回差是单向的：**已经在走就守住，站着就随时可以走。**要跨过的噪声本来也只在
 * 这一侧——坏事是"退到一半被一个瞬时的正号叫停"，而不是"站得好好的忽然跑了"。
 *
 * <p>与 {@link GuardFacingPolicy} 是同一条规矩的两次应用，连要跨过的噪声都是同一个
 * 符号。区别在守住的代价：那边守住只影响脸朝哪，两侧都守也没有代价；这边守住会真的
 * 支使她的腿，所以既要短，又只能守一侧。
 */
public final class WithdrawCommitPolicy {
    /**
     * 姿态成立后至少守多久。
     *
     * <p>半秒，是 {@link GuardFacingPolicy#HOLD_TICKS} 的一半。那边守住不改变她去
     * 哪，多守几 tick 没有代价；这边守住会真的改变她的落脚点，守太久等于让一个已经
     * 过时的判断继续支使她的腿。
     *
     * <p>十 tick 的下界来自要跨过的东西：实测那次冻结连续为正六 tick，而符号翻转的
     * 平均间隔（存活 105 tick 翻 16 次）约六到七 tick。取十是**刚好盖住一个完整的
     * 噪声周期**，不是一个凑出来的整数。
     */
    public static final int HOLD_TICKS = 10;

    public static final WithdrawCommitPolicy INSTANCE = new WithdrawCommitPolicy();

    private WithdrawCommitPolicy() {
    }

    /**
     * 这一 tick 她该不该在走。
     *
     * @param wasLeaving  上一 tick 的姿态
     * @param heldTicks   那个姿态已经守了多久
     * @param rawLeaving  这一 tick 判据自己的答案（软）
     * @param hasRoom     还有没有地方可退（硬事实）
     */
    public boolean leaving(
            boolean wasLeaving,
            int heldTicks,
            boolean rawLeaving,
            boolean hasRoom
    ) {
        if (!hasRoom) {
            // 硬事实：没有落脚点就没有"走"这个选项，守不守都一样。
            return false;
        }
        if (wasLeaving && heldTicks < HOLD_TICKS) {
            return true;
        }
        return rawLeaving;
    }

    /**
     * 这一 tick 之后，姿态守了多久。
     *
     * <p>只在姿态真的翻了才归零。守住期间原样累加，否则计数永远追不上保持期，
     * 姿态等于没守——那是这类回差最容易写错的地方。
     */
    public int nextHeld(boolean wasLeaving, boolean nowLeaving, int heldTicks) {
        return wasLeaving == nowLeaving ? heldTicks + 1 : 0;
    }
}
