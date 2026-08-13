package com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 盾牌的账本：举了几次、举着多少 tick、为了挥刀放下几次、被斧头打掉几次。
 *
 * <p>与 {@link ShieldGuard} 分开的理由和 {@code JumpLedger} 与 {@code JumpStrike}
 * 分开的理由一样：那边是判据，这边是观察。而观察在这里尤其必要——盾牌的收益是
 * **没有发生的伤害**，它在任何结果指标上都表现为"这一局挨得少一点"，而
 * {@code docs/combat/tactics-log.md} 已经量过：四局的挨打读数跨度能到四十四点。
 * 靠 {@code hurt} 判断盾牌有没有在工作，等于用噪声判断。
 *
 * <p>这四个数分母都够：举盾 tick 是每局几百，破盾次数在卫道士局是每局几次。它们
 * 回答的是**机制问题**——她到底举了没有、举了多久、斧头有没有把它拿走——而不是
 * "这一局打得好不好"。先确认机制在跑，再谈值不值。
 *
 * <p>数在被数的那条路径上：举盾走 {@link ShieldGuard#consider}，放下走
 * {@link ShieldGuard#lowerForSwing}，破盾由 {@link #noteAvailability} 在同一处
 * 检测冷却的上升沿。**不在外面按 tick 采样**——那正是靶场早先把每局四十二次起跳
 * 读成一次挥刀的原因。
 */
public final class ShieldLedger {
    private static final int RAISED = 0;

    private static final int HELD_TICKS = 1;

    private static final int LOWERED = 2;

    private static final int DISABLED = 3;

    /** 手被别的东西占着（拉弓、进食），所以这一 tick 没得举。 */
    private static final int BUSY = 4;

    /** 手是空的，但这一刻不值得举。 */
    private static final int DECLINED = 5;

    /** 副手压根没有能举的东西，或者盾正在冷却里。 */
    private static final int UNAVAILABLE = 6;

    /** 从背包里把盾拿到副手的次数。 */
    private static final int EQUIPPED = 7;

    /**
     * 举着盾并且**身体正对目标**的 tick 数。
     *
     * <p>与 {@code HELD} 分开，因为原版只挡正面来的伤害（{@code
     * isDamageSourceBlocked} 拿入射方向和 {@code getYRot} 做点积），而她一移动，
     * 本体的 {@code MaidMoveControl} 就把身体扭向行进方向。于是"举着盾"和
     * "挡得住"是两个数：只看前者会把一整局背对着敌人举盾读成防御生效。
     */
    private static final int FACING = 8;

    /**
     * 真正挡下来的次数，以及挡掉的伤害（取整）。
     *
     * <p>前面每一列量的都是**前提**——盾在不在、举没举、朝没朝对——而这两列量的
     * 是**事件**。两者不能互相代替：整局举着盾、全程正对，仍然可能一次都没挡到
     * （对方绕后、伤害绕过盾、盾在冷却），而那种情况在所有前提列上都长得像成功。
     *
     * <p>数在 Forge 的 {@code ShieldBlockEvent} 上，那是原版判定"这一下被挡住了"
     * 之后唯一会走的地方，不是按 tick 采样出来的。
     */
    private static final int BLOCKED = 9;

    private static final int DENIED = 10;

    private static final int COLUMNS = 11;

    private static final Map<EntityMaid, int[]> TALLY = new WeakHashMap<>();

    /** 上一次看到的"盾可用"状态，用来只数冷却的上升沿。 */
    private static final Map<EntityMaid, Boolean> AVAILABLE =
            new WeakHashMap<>();

    /**
     * 盾不可用时副手里究竟是什么。
     *
     * <p>诊断用的一列，不是产出。加它是因为"不可用"有两种完全不同的原因——
     * 副手空了，或者盾在冷却里——而计数对两者一视同仁。第一轮读数就卡在这里：
     * `unavailable=268` 同时兼容"盾被谁拿走了"和"斧头把它敲进冷却了"，而这
     * 两条要改的地方毫不相干。
     */
    private static final Map<EntityMaid, String> OFFHAND = new WeakHashMap<>();

    private ShieldLedger() {
    }

    /** 读数从零开始。 */
    public static void reset(EntityMaid maid) {
        TALLY.put(maid, new int[COLUMNS]);
        AVAILABLE.remove(maid);
    }

    /** 她把盾举了起来。 */
    public static void noteRaised(EntityMaid maid) {
        bump(maid, RAISED);
    }

    /** 这一 tick 盾是举着的。 */
    public static void noteHeld(EntityMaid maid) {
        bump(maid, HELD_TICKS);
    }

    /** 为了挥这一刀把盾放下了。 */
    public static void noteLowered(EntityMaid maid) {
        bump(maid, LOWERED);
    }

    /**
     * 记录这一 tick 盾还能不能用，并在"能→不能"的那一次上计一笔破盾。
     *
     * <p>只认上升沿。冷却是一百 tick，按 tick 数会把一次破盾读成一百次，而这个数
     * 存在的意义正是回答"卫道士的斧头一局能把盾拿走几次"。
     */
    public static void noteAvailability(EntityMaid maid, boolean usable) {
        Boolean previous = AVAILABLE.put(maid, usable);
        if (previous != null && previous && !usable) {
            bump(maid, DISABLED);
        }
    }

    public static int raised(EntityMaid maid) {
        return read(maid, RAISED);
    }

    public static int heldTicks(EntityMaid maid) {
        return read(maid, HELD_TICKS);
    }

    public static int lowered(EntityMaid maid) {
        return read(maid, LOWERED);
    }

    public static int disabled(EntityMaid maid) {
        return read(maid, DISABLED);
    }

    /** 这一 tick 没举，原因分三类记账。 */
    public static void noteBusy(EntityMaid maid) {
        bump(maid, BUSY);
    }

    public static void noteDeclined(EntityMaid maid) {
        bump(maid, DECLINED);
    }

    /** 她从背包里取出了盾。 */
    public static void noteEquipped(EntityMaid maid) {
        bump(maid, EQUIPPED);
    }

    /** 这一 tick 盾举着，而且身体正对目标。 */
    public static void noteFacing(EntityMaid maid) {
        bump(maid, FACING);
    }

    /** 原版判定这一下被盾挡住了。 */
    public static void noteBlocked(EntityMaid maid, float denied) {
        bump(maid, BLOCKED);
        int[] tally = TALLY.computeIfAbsent(maid, key -> new int[COLUMNS]);
        tally[DENIED] += Math.round(denied);
    }

    public static int blocked(EntityMaid maid) {
        return read(maid, BLOCKED);
    }

    public static void noteUnavailable(EntityMaid maid) {
        bump(maid, UNAVAILABLE);
        OFFHAND.put(
                maid,
                maid.getOffhandItem().isEmpty()
                        ? "empty"
                        : maid.getOffhandItem().getItem().toString()
                                + (maid.getCooldowns().isOnCooldown(
                                        maid.getOffhandItem().getItem())
                                        ? "(cooling)" : "(ready?)")
        );
    }

    /**
     * 一行读数，给基准局打印。
     *
     * <p>ASCII 前缀是有意的：日志编码在这台机器上会把中文打成乱码，而这一行
     * 是要拿来 grep 的。三个"没举"的原因分开列，因为"她不肯举"和"她没手举"
     * 要改的地方完全不同——第一版只打了"举盾 0 次"，那个零同时兼容这两种解释，
     * 于是它什么都没说。
     */
    public static String summary(EntityMaid maid) {
        return "GUARD equipped=" + read(maid, EQUIPPED)
                + " raised=" + raised(maid)
                + " held=" + heldTicks(maid) + "t"
                + " facing=" + read(maid, FACING) + "t"
                + " BLOCKED=" + read(maid, BLOCKED)
                + " denied=" + read(maid, DENIED)
                + " lowered=" + lowered(maid)
                + " disabled=" + disabled(maid)
                + " busy=" + read(maid, BUSY)
                + " declined=" + read(maid, DECLINED)
                + " unavailable=" + read(maid, UNAVAILABLE)
                + " offhand=" + OFFHAND.getOrDefault(maid, "-");
    }

    private static void bump(EntityMaid maid, int column) {
        TALLY.computeIfAbsent(maid, key -> new int[COLUMNS])[column]++;
    }

    private static int read(EntityMaid maid, int column) {
        int[] tally = TALLY.get(maid);
        return tally == null ? 0 : tally[column];
    }
}
