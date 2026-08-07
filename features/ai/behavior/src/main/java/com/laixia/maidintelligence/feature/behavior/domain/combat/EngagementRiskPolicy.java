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

    public static final EngagementRiskPolicy INSTANCE =
            new EngagementRiskPolicy(
                    DEFAULT_SAFETY_MARGIN,
                    DEFAULT_BAIL_OUT_HEALTH,
                    DEFAULT_MELEE_SUPPRESSION
            );

    private final double safetyMargin;
    private final double bailOutHealthFraction;
    private final double meleeSuppression;

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
        this.safetyMargin = safetyMargin;
        this.bailOutHealthFraction = bailOutHealthFraction;
        this.meleeSuppression = meleeSuppression;
    }

    /**
     * Judge the situation.
     *
     * @param field         the crowd, already aggregated
     * @param capability    what she can bring
     * @param healthFraction her current health over her maximum, in {@code [0,1]}
     */
    public RiskVerdict assess(
            ThreatField field,
            CombatCapability capability,
            double healthFraction
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
        boolean outTrades =
                cost * safetyMargin <= capability.effectiveHealth();
        boolean hurt = healthFraction < bailOutHealthFraction;

        if (outTrades && !hurt) {
            return RiskVerdict.ENGAGE;
        }

        // Losing the straight trade. Distance is only an answer if she can use
        // it: something must be shootable, and they must not be able to shoot
        // back from where she would stand.
        boolean canKeepDistance =
                capability.rangedDps() > 0.0D && !field.anyOutranging();
        return canKeepDistance ? RiskVerdict.SKIRMISH : RiskVerdict.WITHDRAW;
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
