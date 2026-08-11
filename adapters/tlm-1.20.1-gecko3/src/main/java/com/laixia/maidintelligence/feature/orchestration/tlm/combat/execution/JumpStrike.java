package com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.CombatReadiness;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Leaving the ground: when it is worth it, and what it buys.
 *
 * <p>Her reach is a sphere, so something hovering just above her head is out of
 * it for the same reason something across the room is — the distance simply is
 * not the shape she can cover. Standing under a vex swinging at nothing is what
 * that looks like from outside, and it is not a targeting bug; she genuinely
 * cannot touch it from where she is.
 *
 * <p>A jump is about a block and a quarter of reach she is not otherwise using,
 * and it costs a moment of not being able to change direction. So it is worth
 * spending exactly when it closes a gap that nothing else will, and worth
 * refusing when the target is already reachable — hopping at a zombie standing
 * in front of her would only spend her footing.
 *
 * <p>The blow on the way down is a critical, for the same reason the sword arc
 * had to be rebuilt in {@link MeleeSwing}: it lives in {@code Player#attack} and
 * nowhere else, so a maid falling onto something got the geometry of a jump
 * attack and none of its payoff.
 *
 * <p>The other reason to leave the ground is the player technique: leaping so
 * that the swing already on cooldown arrives on the way down. That one is
 * entirely a question of phase, and four measured failures came from getting the
 * phase backwards rather than from the idea being wrong. The arc below is the
 * whole argument — she is <em>rising</em> for six ticks and falling for five, and
 * a blow struck while rising collects neither the critical (vanilla wants
 * {@code fallDistance > 0}) nor the sword arc (which wants both feet down). The
 * old window fired at "swing returns within seven ticks", which put six leaps out
 * of every seven on the way up: two payoffs forfeited, none collected. See
 * docs/combat/tactics-log.md, R-18.
 */
public final class JumpStrike {
    /**
     * How much height a jump is assumed to buy her.
     *
     * <p>Vanilla's jump arc peaks a little above a block and a quarter; this
     * sits under that on purpose. Overestimating spends a swing window on a
     * leap that falls short, which is the expensive mistake — underestimating
     * only means she waits for a better moment.
     */
    private static final double APEX_GAIN = 1.15D;

    /** Extra height each level of jump boost is worth, kept conservative. */
    private static final double BOOST_PER_LEVEL = 0.6D;

    /**
     * 起跳之后每一 tick 她离地多高，下标就是第几 tick。
     *
     * <p>不是估的，是把原版那三行算出来的：起跳速度 0.42，每 tick 先按当前速度
     * 位移、再 {@code v = (v - 0.08) * 0.98}。
     *
     * <pre>
     * t     1     2     3     4     5     6  |  7     8     9    10    11
     * 高 0.42  0.75  1.00  1.17  1.25  1.25  | 1.18  1.02  0.80  0.50  0.12
     *        ——————— 上升 ———————            |  ————— 下落，暴击只在这里 —————
     * </pre>
     *
     * <p>这张表是整件事的判据本身，所以留在这儿而不是化成一个公式：要问的从来
     * 不是"跳多高"，而是"落刀那一 tick 她在多高"——她的触及是个球，那点高度是
     * 从水平距离里扣掉的。
     */
    private static final double[] ARC = {
            0.00D, 0.42D, 0.75D, 1.00D, 1.17D, 1.25D,
            1.25D, 1.18D, 1.02D, 0.80D, 0.50D, 0.12D
    };

    /**
     * 外推对方靠近速度时的上限，格/秒。
     *
     * <p>玩家走路约 4.3 格/秒，绝大多数敌对生物在这个量级。它不是"对方有多快"的
     * 真值，是**一个 tick 的采样能被信任到多远**的上限。
     */
    private static final double WALKING_PACE = 4.3D;

    /**
     * 落刀点再往里收多少，只为不掐在边界上。
     *
     * <p>与 {@code MeleeSwing.OWN_REACH_MARGIN} 同一个道理、同一个数：目标在她
     * 决定与落刀之间会挪动，正好站在极限上就是一次落空。
     */
    private static final double LANDING_MARGIN = 0.25D;

    /** 第几 tick 起 {@code fallDistance} 开始累积——也就是暴击从第几 tick 起算。 */
    private static final int FALL_BEGINS_AT = 7;

    /** 第几 tick 落地。之后既没有暴击也没有离地，跳这一下就白花了。 */
    private static final int LANDS_AT = 11;

    /**
     * 还挨得起几下最重的，才敢把这十一 tick 交出去。
     *
     * <p>一下半：扛得住最重的一击，再留半下的余地。离地期间她退不开，这个代价只
     * 在血线低时才兑现，所以门槛问的是"还站不站得住"，不是整笔交战划不划算。
     *
     * <p>先定的两下，实测太紧：卫道士一斧约 13 点、她有效生命 20 出头，
     * {@code 20 < 26} 恒成立，于是**在最需要输出的那一局里跳劈整个是关着的**。
     * 八局对照（同一轮，只翻这个常量）：跳劈率 0% → 18%、起跳 0.1 → 3.4 次，
     * 而**存活一样是 7/8**——放松这半下没有花掉任何命。
     */
    private static final double BLOWS_LEFT_TO_COMMIT = 1.5D;

    /** 原版横扫的判定半径平方——一跳要放弃的正是这个弧里的东西。 */
    private static final double SWEEP_ARC_SQR = 9.0D;

    /** 她上一次挥击是第几 tick，用来算冷却还剩多久。 */
    private static final Map<EntityMaid, Integer> LAST_SWING =
            new WeakHashMap<>();

    /**
     * 这一跳是为哪一刀准备的——同一个冷却周期只许跳一次。
     *
     * <p>判据描述的是一个**时刻**，而它每 tick 都会被问一次；没有这道闸，落地那
     * 一 tick 条件往往又成立，她就再跳一次，如此循环。实测：起跳 37 次／局、六百
     * tick 里四百多 tick 在空中，挥刀反而从 32 掉到 18——蹦跶了一整局。
     */
    private static final Map<EntityMaid, Integer> LEAPT_FOR =
            new WeakHashMap<>();

    private JumpStrike() {
    }

    /**
     * Whether leaving the ground brings this target inside her reach.
     *
     * <p>Both halves matter. Out of reach now, or the jump buys nothing that
     * standing still would not; inside reach from the apex, or it is a hop that
     * ends the same way it started, having spent her footing for nothing.
     *
     * <p>Measured against her own reach rather than a fixed height, so a maid
     * whose favour has stretched her arm needs to jump for fewer things, and
     * needs no separate rule to know it.
     */
    public static boolean worthLeavingTheGround(
            EntityMaid maid,
            LivingEntity victim
    ) {
        if (!maid.onGround() || maid.isInWater() || maid.isPassenger()) {
            return false;
        }
        double reach = MeleeSwing.reach(maid, victim);
        if (maid.distanceToSqr(victim) <= reach * reach) {
            // Already hers. A jump here is a swing spent going up and down.
            return false;
        }
        // Only upwards: a target level with her or below is a walking problem,
        // and jumping at it gives away the footing that walking needs.
        if (victim.getY() <= maid.getY()) {
            return false;
        }
        double horizontal = Math.hypot(
                victim.getX() - maid.getX(), victim.getZ() - maid.getZ()
        );
        double rise = victim.getY() - maid.getY() - apexGain(maid);
        return Math.hypot(horizontal, Math.max(0.0D, rise)) <= reach;
    }


    /**
     * 现在起跳，落下来那一刀能不能真的砍中并且吃到暴击。
     *
     * <p>问的是**落刀那一 tick**的事，不是此刻的事。这是之前四轮全错的地方：判据
     * 写的是"她现在够不够得着"，而这一跳要花十一 tick，十一 tick 里对方会走、她会
     * 升高又落下——拿此刻的几何去决定一件十个 tick 之后才发生的事，本来就不成立。
     *
     * <p>要提前，但只能提前一跳那么多。挥刀是即时动作、起跳是延迟动作，所以等到
     * "她现在够得着了"才决定，即时的那个永远先赢——她于是永远先挥后跳。可"提前"
     * 一旦没有界，就变成朝四格外正在冲过来的东西起跳，落进包围里：实测持剑 83t 掉
     * 到 4t。**界必须来自她自己一跳能挪多远，不能来自对方冲多快。**
     *
     * <p>于是判据是触及外面的一圈——{@link AirControl#driftOver} 算出的 0.99 格：
     *
     * <pre>
     *          ┌── 触及 2.0 ──┐┌─ 预判圈 0.99 ─┐
     *   她 ●───────────────────┼───────────────┼──────→
     *      够得着，该走进去砍    正是这一圈里才跳    太远，跳了也空砍
     *                           ↑
     *                     恢复期她就站在这儿（触及外约 0.7）
     * </pre>
     *
     * <p>圈里这一格是她**本来就砍不到**的地方，所以这一跳没有推迟任何东西；而她
     * 冷却期的站位（{@code holdDistance} 给的 `meleeHold`）恒定落在圈里，这才是这
     * 条规则真正会触发的场景。上一版要求"起跳时已在触及之内"，等于把她每一次真实
     * 处境都排除掉，所以一次也没触发——实机表现为这条规则从不生效。
     *
     * <p>落刀那一 tick 的几何照算：她那时多高（{@link #ARC}，触及是球，高度要从
     * 水平距离里扣）、对方走到哪（{@code closingSpeed} 外推）、以及她自己在空中挪
     * 了多少（{@code driftOver}）。窗口是 {@code [7, 11]}：早于 7 她还在上升，暴击
     * 和横扫两头落空；晚于 11 已经落地。旧代码写的是 {@code <= 7}，正好是反的。
     */
    public static boolean worthCrittingNow(
            EntityMaid maid,
            ScannedThreat target,
            java.util.List<ScannedThreat> pack
    ) {
        if (!maid.onGround() || maid.isInWater() || maid.isPassenger()) {
            return false;
        }
        // 弧里还站着第二个的时候不跳：横扫要两只脚落地，所以每一跳都在拿整道弧
        // 换一个人身上的倍率，而人多的时候弧更值钱。
        if (crowded(maid, pack)) {
            return false;
        }
        // 时机先于距离。冷却剩 7–11 tick 就意味着她此刻**无论站在哪都砍不了**，
        // 这一跳因此不推迟任何东西；负数或零表示刀已就绪，那时候该砍不该跳。
        if (tooFrailToCommit(maid, pack)) {
            return false;
        }
        // 这一刀会落在第几 tick：冷却没走完就听冷却的，冷却是空的就听脚的。
        //
        // 只问冷却的话，**开局第一刀永远没有跳劈**——她还没挥过，ticksUntilSwing
        // 是 MAX_VALUE，判据够不着。而第一刀的时机本来就不由冷却决定，由"什么时候
        // 走到"决定。
        // 只听冷却。冷却是空的时候她既能挥、又能退，这十一 tick 有更好的用法——
        // 所以**开局第一刀是平砍，不是缺陷**：那正是她马上就能出手的时刻。试过改
        // 成"冷却空了就按走到的时间起跳"，四局实测存活 4/4 → 2/4、挨打 19.5 →
        // 30.2、贴身 38t → 54t：剑伤是高了，代价是她在该挥或该退的时候待在空中。
        int landing = ticksUntilSwing(maid);
        if (arcAt(landing) < FALL_BEGINS_AT || arcAt(landing) > LANDS_AT) {
            return false;
        }
        // 一个冷却周期只跳一次。见 LEAPT_FOR：少了这道闸她会落地就再跳。
        Integer swung = LAST_SWING.get(maid);
        if (swung != null && swung.equals(LEAPT_FOR.get(maid))) {
            return false;
        }
        if (!withinLeapReach(maid, target.entity())) {
            return false;
        }
        return within(maid, target, landing, ARC[arcAt(landing)]);
    }

    /**
     * 冷却还剩 {@code landing} tick 时起跳，那一刀会落在弧线的第几 tick 上。
     *
     * <p>是 {@code landing + 1}，不是 {@code landing}。{@link #leap} 在 brain 里
     * 调用，而原版的跳跃结算在同一 tick 的 {@code aiStep} 里就完成了——**起跳那一
     * tick 她已经离地 0.42 格**，弧线从 1 开始而不是 0。
     *
     * <p>差这一个 tick 的代价是这个特性最后一次归零的全部原因：判据从 13 往下数，
     * 第一个为真的是剩 11，下标 12——**那时她已经落地**。靶场读到挥刀 37.8、起跳
     * 37.5（一比一，闸全对），暴击 0。每一次都恰好晚一 tick。
     */
    private static int arcAt(int landing) {
        return landing + 1;
    }

    /**
     * 这一跳是为暴击起的、人还在空中：把她推向目标。
     *
     * <p>{@link #within} 的预测里算进了"她在空中会朝目标挪 0.99 格"
     * （{@link AirControl#driftOver}），而那 0.99 格**原本从不发生**：
     * {@code AirControl.steer} 只在有人要她靠近时才推，而冷却期
     * {@code keepRange} 要求的恰恰是保持退让间距。预测于是整整乐观一格——正好是
     * 落刀时差的那一截。这里把预测里假设过的那件事真的做出来。
     *
     * <p>只在她自己为暴击起的那一跳上生效：够高处而跳、被击退、走下台阶都不算。
     */
    public static void rideTheLeap(EntityMaid maid, LivingEntity victim) {
        if (maid.onGround()) {
            return;
        }
        Integer swung = LAST_SWING.get(maid);
        if (swung == null || !swung.equals(LEAPT_FOR.get(maid))) {
            return;
        }
        AirControl.steer(maid, victim.position(), landingSpot(maid, victim));
    }

    /**
     * 这一跳该在离目标多远的地方收住脚。
     *
     * <p>不是"贴到脸上"。之前写的是触及的一半，实机表现即为**她直接跳到敌人面前**，
     * 落地时站在对方斧子的正中央，暴击是拿她的位置换的。
     *
     * <p>要的距离由几何本身给出：触及是球，落刀那一 tick 她还悬着 {@code ARC} 那么
     * 高，所以水平方向只要满足 {@code hypot(水平, 高) <= 触及} 就够。取下落段最高
     * 的那一 tick（{@code ARC[FALL_BEGINS_AT]}）算，整个下落段就都成立——越往后她
     * 越低，越容易够到。
     *
     * <pre>
     *        触及 ───────────────╮
     *   她 ●╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌●  ← 收在这儿：刀正好够着，人不在斧子底下
     *        └ sqrt(触及² − 高²) ┘
     * </pre>
     */
    private static double landingSpot(EntityMaid maid, LivingEntity victim) {
        double reach = MeleeSwing.reach(maid, victim);
        double lift = ARC[FALL_BEGINS_AT];
        double flat = reach * reach - lift * lift;
        if (flat <= 0.0D) {
            // 触及比一次跳跃的高度还短：那就只能贴上去，否则这一跳落空。
            return 0.0D;
        }
        return Math.max(0.0D, Math.sqrt(flat) - LANDING_MARGIN);
    }

    /**
     * 目标近到一次滞空还够得着——预判圈的外沿。
     *
     * <p>宽度是 {@link AirControl#driftOver}：一跳她自己能横向挪 0.99 格。再远就是
     * 拿整个接近过程去赌一次暴击，而她在空中根本走不了那段路（R-20，持剑 83t→4t）。
     * 界来自**她自己**能挪多远，不来自对方冲多快——那正是那次灾难的解药。
     *
     * <p>这里**没有内沿**。曾经有过一道"够得着就别跳"，理由是"走进去砍更快"——
     * 靶场把它证伪了：冷却=13t、窗口=[7,11]，而她站在触及外那些 tick 上读到的剩余
     * 恒定是 [0,6]。也就是说**该起跳的那半个冷却里她还贴在触及之内**，等退到圈里
     * 窗口早过去了。内沿挡掉的正是她每一次真实的机会。而"走进去砍更快"本身就不
     * 成立：冷却期她站在哪都砍不了，这一刀是被冷却推迟的，不是被这一跳。
     */
    private static boolean withinLeapReach(
            EntityMaid maid,
            LivingEntity victim
    ) {
        double horizontal = Math.hypot(
                victim.getX() - maid.getX(), victim.getZ() - maid.getZ()
        );
        // 界只用她**站着**能挪的那 0.99 格，不加她当下的冲刺动量。
        //
        // 试过加：`max(driftOver, carried × 11)`，走路速度下就是 2.2 格，理由是
        // "跑着起跳原版保留水平分量，一跳能覆盖更远"。物理上没错，代价却是把 R-20
        // 那个坑原样放了回来——她开始边跑边跳撞进包围。实测（四局，只翻这一条）：
        // 僵尸局挨打 0.50 → 7.25，卫道士局输出 76.8 → 55.2、贴身 28t → 52t。
        //
        // 所以这条界不是"她一跳最远能到哪"，是"**不靠寻路她敢走多远**"。
        return horizontal
                <= MeleeSwing.reach(maid, victim)
                        + AirControl.driftOver(LANDS_AT);
    }


    /**
     * 第 {@code delay} tick、她在 {@code lift} 高处时，这一刀够不够得着。
     *
     * <p>水平距离两头一起收：对方按量出来的闭合速度走过来，她自己按空中转向挪过
     * 去。少算后面那一项，判据就会以为她在空中是块石头，于是圈里那一格永远算不成
     * 够得着——而那一格正是这条规则唯一会触发的地方。
     *
     * <p>闭合速度是负的（对方在走开）时同样成立，那就是往外推，判据自然会拒绝——
     * 不需要为"它在逃"单写一条。
     */
    private static boolean within(
            EntityMaid maid,
            ScannedThreat target,
            int delay,
            double lift
    ) {
        LivingEntity victim = target.entity();
        double horizontal = Math.hypot(
                victim.getX() - maid.getX(), victim.getZ() - maid.getZ()
        );
        // 对方那一项只取"走过来"的一半，而且按走路速度封顶。
        //
        // closingSpeed 是**一个 tick** 的相对速度采样，拿它外推半秒本来就不成立：
        // 挥完刀那几 tick 正是击退与退步同时发生的时候，采样读到强烈的负值，外推
        // 出去就成了"她落刀时会在六格外"。实测那一行原样是：剩8 高1.02
        // 水平 2.90 → 6.43 触及 2.00 ——判据当然拒绝，而且是每一个周期都拒绝。
        //
        // 击退是会衰减的瞬态，她随后就走回去；所以负值当零，正值也不许超过走路
        // 速度。宁可低估对方的靠近——低估只让她少跳一次，高估会让她跳空。
        double pace = Math.min(
                Math.max(target.sample().closingSpeed(), 0.0D), WALKING_PACE
        );
        double closes = pace * delay / 20.0D + AirControl.driftOver(delay);
        double then = Math.max(0.0D, horizontal - closes);
        double reach = MeleeSwing.reach(maid, victim);
        return Math.hypot(then, lift) <= reach;
    }


    /**
     * 血少到经不起这十一 tick 的时候，不跳。
     *
     * <p>离地期间她退不开——这才是这一跳真正的代价，而它只在血线低时兑现。所以
     * 门槛问的是"最重的一下还能挨几次"，不是整笔交战划不划算。
     *
     * <p>用整套 {@link ExchangeAffordability#canAfford}（以 0 速度表示"甩不掉"）
     * 试过，实测更差：四局输出 76.8 → 47.8。原因是**这一跳本身就是让交战划算的
     * 东西**——它把剑伤翻了一倍，仗结束得更快，她反而挨得更少（17.0 对 21.2）。
     * 拿"交战不划算"去否掉正在让交战变划算的那件事，是把因果倒过来用。
     *
     * <p>只数落地时够得到她的那些：一跳之外的东西在这十一 tick 里管不着她。
     */
    private static boolean tooFrailToCommit(
            EntityMaid maid,
            java.util.List<ScannedThreat> pack
    ) {
        double heaviest = 0.0D;
        for (ScannedThreat threat : pack) {
            if (maid.distanceToSqr(threat.entity()) <= SWEEP_ARC_SQR) {
                heaviest = Math.max(heaviest, threat.sample().strikeDamage());
            }
        }
        return heaviest > 0.0D
                && CombatReadiness.effectiveHealth(maid)
                        < heaviest * BLOWS_LEFT_TO_COMMIT;
    }

    /**
     * 挥这一刀时，横扫的弧里还会不会扫到别人。
     *
     * <p>用的是原版横扫的判定半径（三格），因为要问的正是"这一跳会放弃多少"。
     */
    private static boolean crowded(
            EntityMaid maid,
            java.util.List<ScannedThreat> pack
    ) {
        int inArc = 0;
        for (ScannedThreat threat : pack) {
            if (threat.entity().isAlive()
                    && maid.distanceToSqr(threat.entity()) <= SWEEP_ARC_SQR) {
                inArc++;
            }
        }
        return inArc > 1;
    }

    /**
     * 她还在往上走吗——上升段挥刀是两头落空，所以这一刀该等。
     *
     * <p>判竖直速度而不是 {@code fallDistance}：{@code fallDistance} 要等她真的
     * 掉了才开始累积，最高点那一 tick 还是零，而最高点已经不该再等了。
     */
    public static boolean climbing(EntityMaid maid) {
        return !maid.onGround()
                && !maid.isInWater()
                && !maid.onClimbable()
                && !maid.isPassenger()
                && maid.getDeltaMovement().y > 0.0D;
    }

    /**
     * 冷却还剩几 tick，也就是这一刀会落在起跳后的第几 tick。
     *
     * <p>没挥过就是 {@link Integer#MAX_VALUE}，在每一处都读作"还早"，不用另立一条。
     *
     * <p>公开是因为这是唯一能看清"她为什么跳/为什么不跳"的口子。冷却记忆只回答
     * "能不能挥"，而这里整件事问的是"还有多久能挥"——之前每一次猜错都源于问不到它。
     */
    public static int ticksUntilSwing(EntityMaid maid) {
        Integer swung = LAST_SWING.get(maid);
        if (swung == null) {
            return Integer.MAX_VALUE;
        }
        return MeleeSwing.recoveryTicks(maid) - (maid.tickCount - swung);
    }

    /** 落刀那一 tick 她离地多高——{@link JumpLedger#phase} 的诊断要用。 */
    public static double heightAtBlow(int landing) {
        int arc = arcAt(landing);
        return arc >= 0 && arc < ARC.length ? ARC[arc] : -1.0D;
    }

    /** 下落段的第一 tick 与最后一 tick，测试和调用方都按它对表。 */
    public static int fallBeginsAt() {
        return FALL_BEGINS_AT;
    }

    /** 见 {@link #fallBeginsAt()}。 */
    public static int landsAt() {
        return LANDS_AT;
    }

    /** 记下她这一刀挥在第几 tick，好让起跳按它对表。 */
    public static void noteSwing(EntityMaid maid) {
        LAST_SWING.put(maid, maid.tickCount);
        JumpLedger.noteSwing(maid);
    }

    /** Ask her legs for the jump; the mob's own controller spends it. */
    public static void leap(EntityMaid maid) {
        JumpLedger.noteLeap(maid);
        Integer swung = LAST_SWING.get(maid);
        if (swung != null) {
            LEAPT_FOR.put(maid, swung);
        }
        maid.getJumpControl().jump();
    }

    private static double apexGain(EntityMaid maid) {
        MobEffectInstance boost = maid.getEffect(MobEffects.JUMP);
        int level = boost == null ? 0 : boost.getAmplifier() + 1;
        return APEX_GAIN + level * BOOST_PER_LEVEL;
    }
}
