package com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.feature.status.tlm.MaidOffhand;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.items.IItemHandler;

/**
 * Whether the shield is up, and when it should be.
 *
 * <p>The host already owns the mechanics: {@code canUseShield} knows what a
 * shield is and whether it is on cooldown, {@code isBlocking} is overridden so
 * a maid guards the instant she raises it rather than after vanilla's five-tick
 * wind-up, and {@code blockUsingShield} already implements an axe disabling it.
 * None of that is re-implemented here. What the host does <em>not</em> have is a
 * reason to raise it: its own {@code MaidUseShieldTask} holds the shield up for
 * as long as anything hostile stands within eight blocks, with no trade-off at
 * all — and free mode does not register that task anyway.
 *
 * <p>So this is only the decision, and the decision exists because raising the
 * shield is not free. {@code useItem} is a single slot: a raised shield and a
 * drawn bow are the same hand, and so are a raised shield and a mouthful. What
 * it costs is therefore the ranged option and the eating window; what it buys is
 * a blow denied outright, since 1.20.1 zeroes a blocked hit rather than
 * softening it.
 *
 * <p>Swinging is <em>not</em> part of the cost, which is worth stating because
 * it is the natural assumption and it is wrong here. Nothing in the host's melee
 * path consults the use state, so a guarding maid can still swing. She lowers
 * the guard for the blow anyway — see {@link #lowerForSwing} — because a hit
 * landing out of a raised shield reads as blocking and attacking at once, and
 * one tick is a price worth paying to not look like a bug.
 */
public final class ShieldGuard {
    /**
     * 她到底用不用盾。**当前开着。**
     *
     * <p>关在这一处，是因为这一处一关，后面全都自动跟着关：她不再从背包里把盾拿到
     * 副手 → 副手空着 → {@code maid.canUseShield()} 为假 → {@code guardedShare}
     * 归零，交战定价回到"她没有盾"的算法 → {@code raised()} 恒假，举盾后退的转身
     * （{@code MaidMoveControlGuardFacingMixin}）与让开箭时的"盾挡得住"分支也一并
     * 失效。**没有第二处需要改，也不会留下一半开一半关的状态。**
     *
     * <p>留成开关而不是把这套删掉：盾这套东西（承伤定价、破盾折算、倒着走）是量过
     * 的，僵尸局曾把击杀 3.13 → 5.13、零伤害局 3/8 → 7/8。要是它不是问题所在，翻回
     * 来只要改这一个字；确认要长期去掉，再连同定价和那处注入一起删干净。
     */
    private static final boolean USE_SHIELD = true;

    /**
     * 她这一刻在防谁。
     *
     * <p>给 {@code MaidMoveControlGuardFacingMixin} 读。撤退路径上
     * {@code maid.getTarget()} 已经被清空（脱离本来就要放掉目标），而"倒着走
     * 的时候脸朝哪"恰恰是撤退时才要紧的问题，所以防御对象要单独记一份。
     *
     * <p>弱引用：目标死亡或卸载后这里不该留住它。
     */
    private static final java.util.Map<EntityMaid, LivingEntity> GUARDED =
            new java.util.WeakHashMap<>();

    /**
     * 这一只是不是真的在追上她。
     *
     * <p>问的是**相对接近速率**：把双方这一 tick 的实际位移投影到连线上，为正
     * 才说明距离仍在缩短。静止对峙为零，她真的拉开时为负。
     *
     * <p>与 {@code Withdrawal} 判"跑不跑得掉"用的是同一个量，两处必须一致——
     * 一个说"甩不掉、站住打"而另一个说"甩得掉、转身跑"的话，她每 tick 会在
     * 两种步法之间抽搐。
     */
    public static boolean pursuedBy(EntityMaid maid, LivingEntity threat) {
        double gap = Math.sqrt(
                (threat.getX() - maid.getX()) * (threat.getX() - maid.getX())
                        + (threat.getZ() - maid.getZ())
                                * (threat.getZ() - maid.getZ())
        );
        if (gap < 1.0E-4D) {
            return true;
        }
        double toX = (threat.getX() - maid.getX()) / gap;
        double toZ = (threat.getZ() - maid.getZ()) / gap;
        double relX = threat.getDeltaMovement().x - maid.getDeltaMovement().x;
        double relZ = threat.getDeltaMovement().z - maid.getDeltaMovement().z;
        // 沿"她指向它"的方向，它相对她的位移为负 = 它在靠近。
        return -(relX * toX + relZ * toZ) > 0.0D;
    }

    /** 她正对着防的那一个，没有则为 null。 */
    public static LivingEntity guardedAgainst(EntityMaid maid) {
        LivingEntity threat = GUARDED.get(maid);
        return threat != null && threat.isAlive() ? threat : null;
    }

    /**
     * How close something has to be before a guard is worth the hand.
     *
     * <p>Seconds until it can strike her, not blocks. A shield denies a blow, so
     * what matters is whether a blow is coming — something walking in from
     * twelve blocks is not a reason to put down a bow, and something a step away
     * is a reason even if it happens to be standing still.
     *
     * <p>Two seconds is one exchange, the same window the threat aggregate plans
     * over. Shorter and she raises it after the first hit lands; longer and she
     * is guarding against things that have not decided to come.
     */
    private static final double GUARD_HORIZON_SECONDS = 2.0D;

    private ShieldGuard() {
    }

    /** Whether she is holding a shield up right now. */
    public static boolean raised(EntityMaid maid) {
        return maid.isUsingItem()
                && maid.getUsedItemHand() == InteractionHand.OFF_HAND
                && maid.getUseItem().canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    /** Whether the off hand holds something that could be raised at all. */
    public static boolean available(EntityMaid maid) {
        return maid.canUseShield();
    }

    /**
     * Raise or hold the guard when this tick has a hand free for it.
     *
     * <p>Called after the strike rather than before, so it sees what the swing
     * decided. Asked first it would raise the shield on the same tick the sword
     * comes down, and {@link #lowerForSwing} would immediately undo it.
     *
     * @param handsBusy whether something else already owns the use slot — a draw
     *                  in progress or a mouthful. Both are worth more than the
     *                  guard: the draw because abandoning it produces exactly
     *                  zero arrows, the mouthful because it is the only thing
     *                  that restores her.
     */
    public static void consider(
            EntityMaid maid,
            ScannedThreat target,
            ThreatField field,
            boolean handsBusy
    ) {
        if (!USE_SHIELD) {
            return;
        }
        equipFromPack(maid);
        boolean usable = maid.canUseShield();
        // Recorded every tick she is in a fight, not only when she wants it:
        // the reading this feeds is "how often did an axe take it away", and a
        // disable that happens while her hands are full still took it away.
        ShieldLedger.noteAvailability(maid, usable);
        if (!usable) {
            ShieldLedger.noteUnavailable(maid);
            return;
        }
        if (handsBusy) {
            ShieldLedger.noteBusy(maid);
            return;
        }
        if (raised(maid)) {
            ShieldLedger.noteHeld(maid);
            return;
        }
        if (!worthRaising(maid, target, field)) {
            ShieldLedger.noteDeclined(maid);
            return;
        }
        maid.startUsingItem(InteractionHand.OFF_HAND);
        ShieldLedger.noteRaised(maid);
        ShieldLedger.noteHeld(maid);
    }

    /**
     * Point her body at what she is guarding against.
     *
     * <p>Vanilla only blocks what arrives from in front: {@code
     * isDamageSourceBlocked} takes the dot product of the incoming direction
     * with {@code getViewVector}, and that view vector comes from {@code
     * getYRot} — her <em>body</em>, not her head. Facing therefore is not
     * cosmetic, it is the whole difference between a shield that works and one
     * she is merely holding.
     *
     * <p>{@code CombatMovement.face} was never enough for this. It writes
     * {@code LOOK_TARGET}, which the look control turns into head rotation, and
     * a head is clamped to seventy-five degrees off the body anyway.
     *
     * <p>What this cannot do is make her backpedal. The host's
     * {@code MaidMoveControl} rewrites {@code yRot} and {@code yBodyRot} to the
     * direction of travel every tick, and it runs <em>after</em> the brain — so
     * anything set here is overwritten the moment she is actually walking
     * somewhere. Standing still is the case it holds for, and standing still is
     * what a guard is for: a maid who cannot outrun her pursuer gains nothing by
     * turning her back on it, which is the measured shape of the vindicator
     * fight.
     */
    public static void faceThreat(EntityMaid maid, ScannedThreat target) {
        if (!raised(maid)) {
            return;
        }
        double dx = target.entity().getX() - maid.getX();
        double dz = target.entity().getZ() - maid.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        GUARDED.put(maid, target.entity());
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        maid.setYRot(yaw);
        maid.yBodyRot = yaw;
        maid.yHeadRot = yaw;
        ShieldLedger.noteFacing(maid);
    }

    /**
     * Drop the guard for one blow.
     *
     * <p>Only when it is actually up, so this cannot disturb a draw or a
     * mouthful — those are somebody else's use of the same slot and have their
     * own rules about being interrupted.
     */
    public static void lowerForSwing(EntityMaid maid) {
        if (!raised(maid)) {
            return;
        }
        maid.stopUsingItem();
        ShieldLedger.noteLowered(maid);
    }

    /**
     * Put a shield from the pack into her off hand when it is free.
     *
     * <p>Without this the tactic depends on the shield already being in the off
     * hand and staying there, and it does not stay there. The host overrides
     * {@code completeUsingItem} to call {@code backCurrentHandItemStack}, which
     * empties the off hand into the pack on <em>any</em> completed use — so the
     * first mouthful she takes in a fight quietly disarms her guard, and every
     * tick after that reads as "no shield" rather than as a bug. Measured: one
     * thirty-two tick apple, then {@code offhand=empty} for the remaining two
     * hundred and sixty-eight.
     *
     * <p>Taking it back out is also the right behaviour on its own terms. She
     * pulls a sword from the pack when a fight starts; a shield sitting in the
     * pack should be no different, and requiring a player to pre-place it in the
     * off hand would be a rule nobody could guess.
     *
     * <p>副手里剩着一口没吃完的饭时，先把它收进背包再拿盾。这一条曾经写成"副手
     * 非空就放弃"，理由是不去动玩家特意放的东西。那个理由对了一半：副手在本体那
     * 边没有任何一条取出的路，所以对**不是玩家放的**那些东西，"不去动"的实际含义
     * 是那一格从此作废——战斗打断进食会把半块面包永久留在她副手，她整局举不起盾，
     * 而每一 tick 都如实报告"她没有盾"。
     *
     * <p>只动食物。武器由 {@code WeaponSwap.unpark} 在更上游收走了，所以能走到这
     * 里的非盾物品要么是那口饭，要么就真是玩家放的——图腾、火把、地图不动。副手
     * 那个图腾是她少死一次的全部原因，为了一面盾把它收进背包是净亏。
     *
     * <p>收不进背包就什么都不做。宁可这一局没有盾，也不产生掉落物——
     * {@link MaidOffhand} 存在的全部理由就是这一句。
     */
    private static void equipFromPack(EntityMaid maid) {
        // 先把上一顿被打断的饭扣在隐藏槽里的东西要回来。那件东西经常**就是这面
        // 盾**——她吃饭时盾被存进去，战斗打断了进食，于是盾再也没还回来，而背包
        // 里也找不到它。不先做这一步，下面整个循环会诚实地报告"她没有盾"。
        MaidOffhand.recoverStranded(maid);
        ItemStack held = maid.getOffhandItem();
        if (!held.isEmpty()
                && (held.canPerformAction(ToolActions.SHIELD_BLOCK)
                        || held.getFoodProperties(maid) == null)) {
            return;
        }
        IItemHandler pack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < pack.getSlots(); slot++) {
            ItemStack candidate = pack.getStackInSlot(slot);
            if (candidate.isEmpty()
                    || !candidate.canPerformAction(ToolActions.SHIELD_BLOCK)) {
                continue;
            }
            // 先取盾再腾手。反过来写会在**背包塞满时永远拿不到盾**：腾手要往背
            // 包里放东西，而这面盾占着的那一格往往就是唯一的空位。盾不可堆叠，
            // 取一件必定空出一格，所以这个顺序总是成立。
            ItemStack taken = pack.extractItem(slot, 1, false);
            if (taken.isEmpty()) {
                return;
            }
            if (!MaidOffhand.vacate(maid)) {
                // 原样放回。取出来之后才发现腾不出手，那面盾就悬在半空，谁都不
                // 持有它——刚从这里拿走的，一定放得回去。
                pack.insertItem(slot, taken, false);
                return;
            }
            maid.setItemSlot(EquipmentSlot.OFFHAND, taken);
            ShieldLedger.noteEquipped(maid);
            return;
        }
    }

    /**
     * Whether a blow is close enough to be worth denying.
     *
     * <p>The arrival clock rather than a distance, and the field's rather than
     * the target's: something on her flank hits her just as hard as the one she
     * picked, and it is the first arrival her off hand has to be ready for.
     */
    private static boolean worthRaising(
            EntityMaid maid,
            ScannedThreat target,
            ThreatField field
    ) {
        if (field.converging() > 0 || field.pressing() > 0) {
            return true;
        }
        double until = Math.min(
                field.soonestContact(), target.sample().secondsToContact()
        );
        return until <= GUARD_HORIZON_SECONDS;
    }
}
