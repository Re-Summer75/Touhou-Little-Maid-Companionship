package com.laixia.maidintelligence.feature.behavior.domain.combat;

import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;

import java.util.Objects;

/**
 * Decides whether this fight is worth taking, by comparing two clocks.
 *
 * <p>How long it takes her to end the fight, against how long she lasts while
 * doing it. Both come from what is actually there — her gear and their damage —
 * so no creature is ever "dangerous" in the abstract. The same horde is worth
 * charging in netherite and worth fleeing in an apron, and one rule produces
 * both answers.
 *
 * <p>This is deliberately separate from choosing a weapon. Which sword she
 * draws is a different question from whether she should be drawing one, and
 * folding them together is how a maid ends up picking her best weapon for a
 * fight she cannot win.
 */
public final class EngagementRiskPolicy {
    /**
     * How much of an attacker's output her own swings take back.
     *
     * <p>A landed hit knocks the target away, and the walk back is time it
     * spends not hitting her. She swings about once a second and a zombie
     * attacks about once a second, so the one she is working on loses most of
     * a cycle every time she connects — every attacker used to be priced as
     * though it stood still and swung on schedule, which is precisely what
     * does not happen to something being hit.
     *
     * <p>High, and deliberately so: at half this, two zombies still read as a
     * losing trade to a maid with an iron sword, which is the fight players
     * watched her decline. It buys back only what the knockback really takes,
     * and only from one target — see {@code suppressed}.
     */
    private static final double DEFAULT_MELEE_SUPPRESSION = 0.8D;

    /**
     * How much faster than her own death she must be able to finish.
     *
     * <p>Above one because the estimate is optimistic by construction: it
     * assumes she never misses, is never knocked back, and that nothing else
     * wanders in. A maid who commits to an even trade loses it.
     *
     * <p>It sat at 1.6, which sounds cautious and reads as cowardice: a single
     * zombie only just cleared it, so a second one meant walking away. The
     * point of her being armed is that she deals with what shows up, and the
     * one who most needs her to is standing behind her. Withdrawing is still
     * available — it now costs a genuinely losing fight rather than a merely
     * unflattering one.
     */
    private static final double DEFAULT_SAFETY_MARGIN = 1.2D;

    /**
     * Health fraction under which she stops accepting even a winning trade.
     *
     * <p>Winning with a heart left is not winning — the next hostile that walks
     * in kills her, and she cannot heal on demand.
     */
    private static final double DEFAULT_BAIL_OUT_HEALTH = 0.3D;

    /**
     * The one in force.
     *
     * <p>Two of its numbers describe a single blow rather than a rate. The
     * share of her health a blow may take before it changes the sum is a
     * quarter — ordinary hostiles sit well under it, a zombie takes about a
     * seventh, so the base margin keeps deciding those fights unchanged, which
     * matters because being too shy of zombies is a failure this policy was
     * explicitly corrected for once already. How sharply the demanded margin
     * then climbs is set so that something removing two thirds of her health
     * per hit roughly doubles the safety she wants: enough to turn "the numbers
     * say I win" into "not with those numbers" for a vindicator, without
     * touching anything ordinary. Both are stated in {@link CombatBalance}.
     */
    private static volatile EngagementRiskPolicy instance =
            of(CombatBalance.defaults());

    private final double safetyMargin;
    private final double bailOutHealthFraction;
    private final double meleeSuppression;
    private final double survivableBlowShare;
    private final double blowCaution;

    public EngagementRiskPolicy(
            double safetyMargin,
            double bailOutHealthFraction
    ) {
        this(safetyMargin, bailOutHealthFraction, DEFAULT_MELEE_SUPPRESSION);
    }

    public EngagementRiskPolicy(
            double safetyMargin,
            double bailOutHealthFraction,
            double meleeSuppression
    ) {
        this(
                safetyMargin,
                bailOutHealthFraction,
                meleeSuppression,
                CombatBalance.defaults().survivableBlowShare(),
                CombatBalance.defaults().blowCaution()
        );
    }

    public EngagementRiskPolicy(
            double safetyMargin,
            double bailOutHealthFraction,
            double meleeSuppression,
            double survivableBlowShare,
            double blowCaution
    ) {
        this.safetyMargin = safetyMargin;
        this.bailOutHealthFraction = bailOutHealthFraction;
        this.meleeSuppression = meleeSuppression;
        this.survivableBlowShare = survivableBlowShare;
        this.blowCaution = blowCaution;
    }

    /** Build one from the stated balance. */
    public static EngagementRiskPolicy of(CombatBalance balance) {
        return new EngagementRiskPolicy(
                balance.safetyMargin(),
                balance.bailOutHealth(),
                balance.meleeSuppression(),
                balance.survivableBlowShare(),
                balance.blowCaution()
        );
    }

    /** The policy every caller should be asking. */
    public static EngagementRiskPolicy instance() {
        return instance;
    }

    /**
     * Adopt a new balance.
     *
     * <p>Prefer {@link CombatPolicies#install}: a balance applied to one policy
     * and not the others is a combination nobody chose.
     */
    public static void install(CombatBalance balance) {
        instance = of(balance);
    }

    /**
     * Judge the situation.
     *
     * @param field          the crowd, already aggregated
     * @param capability     what she can bring
     * @param healthFraction her current health over her maximum, in {@code [0,1]}
     * @param canOpenGround  whether she has the room and the legs to give ground;
     *                       without it "keep your distance" is not a plan, it is
     *                       a description of standing still
     */
    public RiskVerdict assess(
            ThreatField field,
            CombatCapability capability,
            double healthFraction,
            boolean canOpenGround
    ) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(capability, "capability");
        if (field.isEmpty()) {
            return RiskVerdict.STAND_DOWN;
        }
        if (!capability.armed()) {
            // Bare hands against anything at all is not a fight she wins.
            return RiskVerdict.WITHDRAW;
        }

        if (field.convergingHealth() <= 0.0D) {
            // Visible, but none of them arrives inside the window being planned
            // over. There is no exchange to price, and pricing it anyway would
            // have her act on a fight that is not happening.
            return RiskVerdict.STAND_DOWN;
        }
        // Price the whole fight, not the first kill. This once cost only the
        // nearest one's health, on the reasoning that putting it down already
        // changes things — but that gives ten zombies and one zombie the same
        // entry price, while the damage arrives ten at a time. It is the exact
        // shape of standing in a crowd swinging until she dies.
        double clearSeconds =
                field.convergingHealth() / capability.bestDps();
        double sustained = suppressed(field, capability)
                * attritionShare(field.converging());
        double cost = clearSeconds * sustained;
        boolean outTrades = cost * marginAgainst(field, capability)
                <= capability.effectiveHealth();
        boolean hurt = healthFraction < bailOutHealthFraction;

        if (outTrades && !hurt) {
            return RiskVerdict.ENGAGE;
        }

        if (hurt) {
            // The bail-out is a floor, not a tactic. Below it she leaves.
            return RiskVerdict.WITHDRAW;
        }

        // Losing the straight trade, which is not the same as losing the fight.
        // Standing in the middle of them swinging is one way to fight and it is
        // the only one the arithmetic above prices — it assumes every one of
        // them is hitting her for the whole time it takes to clear the lot.
        //
        // Given room and the legs to use it, that assumption is simply false.
        // She backs off, they string out behind her because they path at
        // different speeds, and she fights the one at the front while the rest
        // are still walking. What she pays is the leader's damage, not the
        // crowd's, and a six-on-one becomes six one-on-ones.
        //
        // {@code canOpenGround} is exactly that precondition: the caller
        // answers it with both a terrain probe and a speed comparison, so it
        // already means "there is somewhere to go and she is faster than what
        // follows". Where it holds, a fighting retreat strictly dominates a
        // pure one — the pure retreat never kills anything, and they follow.
        // Measured against six zombies, the pure retreat spent the last seven
        // seconds of every run walking in circles with a drawn sword.
        //
        // What changed here is only that it no longer requires a bow. It used
        // to, and that is why she stopped fighting the moment her quiver ran
        // dry: a fighting retreat with a blade is the same tactic, and the
        // ground is what makes it work, not the weapon.
        //
        // Something that outranges her still rules it out, and that condition
        // is untouched. Backing away from a skeleton is not keeping her
        // distance, it is being shot at from further away.
        if (canOpenGround && !field.anyOutranging()) {
            return RiskVerdict.SKIRMISH;
        }

        // Nowhere to give. Distance is not available, so it cannot be the plan.
        // A maid pinned against a wall was once told to skirmish anyway, held
        // her ground because there was no ground, and was eaten where she
        // stood; "keep your distance" with no distance is a long way of saying
        // "stay here".
        return RiskVerdict.WITHDRAW;
    }

    /**
     * Incoming damage once her own swings are accounted for.
     *
     * <p>A swing is single target, so only one attacker is being knocked about
     * at any moment and the rest keep their full output. Spreading the relief
     * across the crowd is what keeps a swarm genuinely dangerous while a duel
     * is not — the same reason this cannot simply be a smaller safety margin.
     */
    /**
     * The fraction of the opening damage rate she pays on average.
     *
     * <p>Not a tuning knob — arithmetic. Each kill removes one attacker, so the
     * incoming rate falls linearly from all of them to none, and the mean over
     * that is {@code (n+1)/2n}: the whole rate against a single opponent, and
     * approaching half of it against a crowd. Charging her the opening rate for
     * the entire fight would have her flee from things she beats comfortably;
     * charging her one attacker's worth is what let her die in a mob.
     */
    /**
     * How much better than break-even she needs, given how hard they hit.
     *
     * <p>Everything above this line is an expected value, and an expected value
     * cannot see the difference between losing slowly and dying suddenly. Three
     * damage every half second and thirteen every two seconds are the same rate;
     * against twenty health the first is a fight and the second is two mistakes
     * from over. She was being told to skirmish with vindicators on exactly that
     * reasoning, and the measured outcome was a dead maid on the runs where the
     * pathing went badly for a second.
     *
     * <p>So the margin widens with the share of her health one blow removes.
     * Below a quarter it does not move at all — ordinary hostiles are what the
     * base margin was tuned against, and making her flinch at zombies is the
     * failure this policy already went out of its way to avoid.
     */
    private double marginAgainst(
            ThreatField field,
            CombatCapability capability
    ) {
        if (capability.effectiveHealth() <= 0.0D) {
            return safetyMargin;
        }
        double blowShare =
                field.heaviestBlow() / capability.effectiveHealth();
        double excess = Math.max(0.0D, blowShare - survivableBlowShare);
        return safetyMargin + excess * blowCaution;
    }

    private double attritionShare(int converging) {
        if (converging <= 1) {
            return 1.0D;
        }
        return (converging + 1.0D) / (2.0D * converging);
    }

    private double suppressed(
            ThreatField field,
            CombatCapability capability
    ) {
        if (capability.meleeDps() <= 0.0D || field.converging() <= 0) {
            return field.incomingDps();
        }
        return field.incomingDps()
                * (1.0D - meleeSuppression / field.converging());
    }
}
