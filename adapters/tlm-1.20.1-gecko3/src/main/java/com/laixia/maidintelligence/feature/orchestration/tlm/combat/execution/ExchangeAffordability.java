package com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.SpacingPolicy;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.CombatReadiness;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Whether standing and trading is something she can pay for.
 *
 * <p>Lifted out of {@link MeleeSwing} because it is a different kind of
 * question. That class knows how to land a blow and where to stand to do it;
 * this one decides whether the exchange is worth entering at all, which is a
 * judgement about her and the crowd rather than about the swing.
 */
public final class ExchangeAffordability {
    /** Ticks in a second — the unit attack speed is quoted in. */
    private static final double TICKS_PER_SECOND = 20.0D;

    /**
     * The share of her health she will let land during one committed exchange
     * with something she cannot walk away from.
     *
     * <p>This replaces a head count, and the count is what was wrong: it priced
     * two zombies and two vindicators identically, three points a second each
     * against thirteen. One number could not serve both — at one attacker she
     * was needlessly timid with the zombies, and against vindicators the same
     * line meant she never swung at all, two and a half blows in a whole fight
     * with a diamond sword in her hands.
     *
     * <p>A share of her effective health, so it moves with her gear rather than
     * sitting at a constant. Two fifths sat on a knife edge — one vindicator
     * lands 8.125 against an allowance of exactly 8.0 at twenty health — so a
     * half, which leaves margin on both sides.
     */
    private static final double COMMITTED_EXCHANGE_SHARE = 0.5D;

    /**
     * How many of their blows she insists on being able to take before she is
     * willing to stand in them.
     *
     * <p>A rate cannot tell being ground down from being ended: thirteen points
     * a second and three points a quarter second are the same number and not
     * the same risk to twenty health. Pricing this per second repeated the
     * error {@code survivableBlowShare} exists to fix one layer up, and the
     * visible result is a maid who reads a vindicator as affordable and is
     * killed by it in two swings.
     *
     * <p>Three: the exchange, the one after it, and the pathing that goes wrong
     * in between. Against her effective health, so armour and absorption buy
     * their way past it honestly and nothing here knows what a vindicator is.
     */
    private static final double MINIMUM_BLOWS_SURVIVED = 3.0D;

    /**
     * The same, for a crowd she can genuinely leave.
     *
     * <p>Far stricter, because there the alternative to standing really is
     * safety: six zombies cost her one point across a whole fight when she
     * stepped off, and twelve when priced generously enough to wade into four.
     */
    private static final double SHEDDABLE_EXCHANGE_SHARE = 0.15D;

    /**
     * How much faster she must be before backing off counts as escaping.
     *
     * <p>Not {@code RetreatSpace.outpaces}, which asks whether a retreat can
     * begin; this asks whether it accomplishes anything. Fifteen per cent opens
     * ground over a long withdrawal and nothing across one swing recovery.
     * Measured, backing off costs her one point a fight against zombies and
     * twenty-three against vindicators — a zombie at 0.23 against her 0.42 is
     * shed, a vindicator at 0.35 is not.
     */
    private static final double SHEDDING_MARGIN = 1.5D;

    private ExchangeAffordability() {
    }

    /**
     * Whether what can touch her where she stands is worth what it costs.
     *
     * <p>This is the whole tactic against a crowd, stated as one condition, and
     * for a long time it was stated as a head count: at most one pair of arms,
     * back off otherwise. Backing off is not passivity — a pack that follows
     * strings out, because they path at uneven speeds, and the moment the
     * leader is clear of the rest this answers true again and she goes in.
     * Six-on-one becomes six one-on-ones, which is the shape of the fight she
     * can win unhurt, and it falls out of this one line rather than a script.
     *
     * <p>The window is her own swing recovery: the time it costs her to land a
     * blow and be somewhere else, which is exactly what a hostile arriving
     * inside it would close on while she is committed. So a second one counts
     * from the moment it is one exchange away, not from when it arrives —
     * stepping off one recovery early is what turns the separation into
     * something she makes rather than something she waits for. Counting instead
     * whether each had spent its blow was tried and measured worse: it relaxes
     * the condition, so she closes on a momentary lull and arrives after it.
     *
     * <p>What changed is the currency. A count cannot tell two zombies from two
     * vindicators — three points a second each against thirteen — so one number
     * had to serve both and could not: at one she was needlessly timid with the
     * zombies, at two she stood in the vindicators and died faster.
     *
     * <p>So it is asked in the currency the rest of the fight uses, and in two
     * parts, because a rate alone repeats the blindness the risk policy was already corrected for. How much lands during
     * the exchange decides whether she can pay for it; how hard the single
     * heaviest blow hits decides whether she is alive to pay for the next one.
     */
    public static boolean canAfford(
            EntityMaid maid,
            java.util.List<ScannedThreat> pack,
            float speed
    ) {
        double exchange = MeleeSwing.recoveryTicks(maid) / TICKS_PER_SECOND;
        double landing = 0.0D;
        double heaviest = 0.0D;
        boolean sheddable = true;
        for (ScannedThreat threat : pack) {
            double clearance = SpacingPolicy.instance()
                    .clearanceBeyond(threat.sample().reach());
            boolean here = threat.sample().distance() <= clearance;
            boolean arriving = threat.sample().secondsToContact() <= exchange;
            if (here || arriving) {
                landing += threat.sample().damagePerSecond() * exchange;
                heaviest = Math.max(heaviest, threat.sample().strikeDamage());
                sheddable &= outruns(maid, threat.entity(), speed);
            }
        }
        // 盾也算进来。它挡掉的那一份**只在她真的能举起来的时候**存在，而
        // `guardedShare` 已经把"能挡的方向"和"会不会被斧头打掉"都折算过了：没有
        // 盾、盾在冷却、或者对面全是持斧的，它就是零。
        //
        // 不加这一项的后果是量出来的：这套判据当初是**在她背着盾的时候**验证的，
        // 而它自己不知道盾的存在。于是把盾拿走之后她仍然按"扛得住"行事——僵尸局
        // 挨打从加盾前的 1.08 涨到 6.5，比从来没有盾的时候还差三到六倍。她不是
        // 少了一面盾，是**以为自己还有**。
        // 空军械表：这里只要 guardedShare，而它只看她副手那面盾和面前这群人，
        // 与她带了什么武器无关。传武器进来只会让这一处依赖一个它不使用的东西。
        double guarded = CombatReadiness
                .of(maid, java.util.List.of(), pack)
                .guardedShare();
        double mine = CombatReadiness.effectiveHealth(maid);
        // Two tests, because they catch different deaths. The rate says whether
        // she can pay for the exchange; the blow says whether she is still
        // standing to pay for the next one.
        //
        // **盾只折算前者。**`guardedShare` 是一个份额——"飞来的伤害里她平均挡得下
        // 多少"，那是个期望值，用来打折"这段时间累计挨多少"是对的。而 `heaviest`
        // 问的是**最坏的那一下**：盾要么挡下它要么没挡下，没有"挡下四分之一"这回
        // 事。拿份额去打折它，等于让她以为最重的一击变轻了，而那条测试存在的全部
        // 理由就是"她还站不站得住"。
        //
        // 这个错误已经付过一次代价：第一版两条都折，实机表现是**被一群敌人围住
        // 之后她不但不退，反而往里走，直到血打完**——因为下面那个"扛不住就真的
        // 脱离接触"的分支再也不触发，而挥刀窗口一开又把站位距离要成零。
        if (heaviest > 0.0D && mine < heaviest * MINIMUM_BLOWS_SURVIVED) {
            return false;
        }
        double taken = landing * (1.0D - guarded);
        landing = taken;
        double share = sheddable
                ? SHEDDABLE_EXCHANGE_SHARE
                : COMMITTED_EXCHANGE_SHARE;
        return landing <= mine * share;
    }

    /**
     * Whether stepping off this one actually loses it.
     *
     * <p>Zero or missing speed counts as shed — something that does not move
     * cannot follow her, and treating it as a pursuer would have her stand and
     * trade with a target she could simply walk away from.
     */
    private static boolean outruns(
            EntityMaid maid,
            LivingEntity foe,
            float speed
    ) {
        double theirs = foe.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (theirs <= 0.0D) {
            return true;
        }
        double mine = maid.getAttributeValue(Attributes.MOVEMENT_SPEED)
                * speed;
        return mine >= theirs * SHEDDING_MARGIN;
    }
}
