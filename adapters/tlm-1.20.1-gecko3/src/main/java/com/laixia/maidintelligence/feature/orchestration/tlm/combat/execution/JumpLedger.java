package com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 跳劈的账本：跳了几次、砍了几刀、真的暴击了几次。
 *
 * <p>与 {@link JumpStrike} 分开，是因为那边是判据、这边是观察。判据早就写好了，
 * 而这个特性失败过五轮——**每一轮都只能靠肉眼观察验收**，因为
 * 没有任何一个数说得清她跳了没有、跳成了没有。大基准里的"每刀伤害"分母只有一两
 * 刀，已经量出来是噪音（见 {@code docs/combat/tactics-log.md} 的"规矩"）。
 *
 * <p>数在这三个地方，不数在别处：起跳走 {@link JumpStrike#leap}，暴击走
 * {@link JumpStrike#strike}，挥刀走 {@link JumpStrike#noteSwing}。都是唯一入口。
 *
 * <p>一条踩过的坑值得留着：靶场原先在外面"每 tick 采样 {@code ATTACK_COOLING_DOWN}
 * 的上升沿"来数挥刀，读到 1，而同一局起跳 42 次——两者不可能同时为真。原因是
 * **冷却到期和下一刀常常落在同一个 tick 里**，一 tick 一次的采样永远看不到那个
 * 空隙。**计数要长在被计数的那条路径上，不能在外面采样。**
 */
public final class JumpLedger {
    private static final int LEAPS = 0;

    private static final int CRITS = 1;

    private static final int SWINGS = 2;

    private static final Map<EntityMaid, int[]> TALLY = new WeakHashMap<>();

    private JumpLedger() {
    }

    /** 读数从零开始。 */
    public static void reset(EntityMaid maid) {
        TALLY.put(maid, new int[3]);
    }

    static void noteLeap(EntityMaid maid) {
        of(maid)[LEAPS]++;
    }

    static void noteCrit(EntityMaid maid) {
        of(maid)[CRITS]++;
    }

    static void noteSwing(EntityMaid maid) {
        of(maid)[SWINGS]++;
    }

    /** 她起跳了几次。 */
    public static int leaps(EntityMaid maid) {
        return of(maid)[LEAPS];
    }

    /** 落下来那一刀真的按暴击结算了几次。 */
    public static int crits(EntityMaid maid) {
        return of(maid)[CRITS];
    }

    /** 她一共挥了几刀。 */
    public static int swings(EntityMaid maid) {
        return of(maid)[SWINGS];
    }

    private static int[] of(EntityMaid maid) {
        return TALLY.computeIfAbsent(maid, ignored -> new int[3]);
    }

    /**
     * 落刀那一刻的几何，一行读完——给测试和排错用。
     *
     * <p>值得为它开一个公开口子：这个特性失败过五轮，而每一轮可见的只有"她没跳"，
     * 看不到是哪一道门拦下的，于是每一轮都只能靠猜。判据本身要能自陈。
     */
    public static String phase(EntityMaid maid, ScannedThreat target) {
        int landing = JumpStrike.ticksUntilSwing(maid);
        LivingEntity victim = target.entity();
        double horizontal = Math.hypot(
                victim.getX() - maid.getX(), victim.getZ() - maid.getZ()
        );
        double closes = target.sample().closingSpeed() * landing / 20.0D;
        return String.format(
                "剩%d 站地=%b 冷却中=%b 高%.2f 水平%.2f→%.2f 触及%.2f",
                landing,
                maid.onGround(),
                !MeleeSwing.recovered(maid),
                JumpStrike.heightAtBlow(landing),
                horizontal,
                Math.max(0.0D, horizontal - closes),
                MeleeSwing.reach(maid, victim)
        );
    }
}
