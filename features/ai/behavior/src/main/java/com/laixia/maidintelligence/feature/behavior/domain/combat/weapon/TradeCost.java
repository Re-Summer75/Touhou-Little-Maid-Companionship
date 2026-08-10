package com.laixia.maidintelligence.feature.behavior.domain.combat.weapon;

import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementContext;
import com.laixia.maidintelligence.feature.behavior.domain.combat.SpacingPolicy;

/**
 * What using one particular weapon here would cost her.
 *
 * <p>Cost, not score, and measured in damage she expects to take before the
 * target is down. That currency is deliberate: it is the same one
 * {@link EngagementRiskPolicy} settles "should I fight at all" in, so the two
 * decisions can no longer disagree about the same fight. It also means nothing
 * here is a preference. There is no rule that says a bow beats a sword at eight
 * blocks; there is an arithmetic that happens to come out that way, and comes
 * out the other way when the target is a skeleton.
 *
 * <p>The behaviours that used to be written down as special cases now fall out
 * of three honest observations:
 *
 * <ul>
 *   <li>A swung weapon cannot touch something in the air, so melee against a
 *       flier costs everything and she reaches for the bow without anyone
 *       having named phantoms.</li>
 *   <li>A draw that does not fit between two incoming blows is a draw that
 *       never completes. Against something hitting her once a second she gets
 *       no shots off at all, which is why being pressed means steel — not
 *       because four blocks is a magic number, but because her hands are full
 *       of a bow she never finishes drawing.</li>
 *   <li>Distance is only worth what she can keep. Ground she has to win back
 *       between every shot is ground she pays for twice, so kiting is cheap in
 *       the open and expensive in a corner, against a fast pursuer, or in a
 *       crowd.</li>
 * </ul>
 */
public final class TradeCost {
    /**
     * Blocks a second she nets while backing away from something she outpaces.
     *
     * <p>Not her movement speed: a pursuer is closing while she retreats, so
     * what matters is the difference, and a pursuer gets its own chase bonus on
     * top. Around a block and a half is what that difference comes to against
     * the ordinary walking hostile — enough that retreating is real, far from
     * the free repositioning her raw speed would suggest.
     */
    private static final double GROUND_GAINED_PER_SECOND = 1.5D;

    /**
     * Damage a second of delay is worth.
     *
     * <p>A fight that drags is its own risk: more arrives, her food burns down,
     * and her owner walks off. Without a term for it two options that both take
     * no damage would tie, and she would settle ties on nothing. One damage per
     * second says a fight dragged five seconds longer is worth about one extra
     * hit — enough to break ties, too small to override being hurt.
     */
    public static final double IMPATIENCE_DAMAGE_PER_SECOND = 1.0D;

    /**
     * The share of a cost she keeps for already holding the weapon.
     *
     * <p>A swap costs her a beat and re-picking every tick costs her the fight.
     * Applied to the cost rather than to the power rating so it scales with how
     * much is at stake: a trivial gain never justifies the fumble, a large one
     * always does.
     */
    private static final double IN_HAND_DISCOUNT = 0.15D;

    /**
     * Pairs of arms she will walk into before the walk itself is charged.
     *
     * <p>The same figure {@code MeleeSwing} tolerates before it breaks contact,
     * and it has to be: one of them decides whether closing is worth it and the
     * other decides whether she actually closes, and they cannot disagree about
     * how crowded is too crowded.
     */
    private static final int TOLERATED_ATTACKERS = 1;

    /**
     * Distance she wants for shooting, once she has decided to shoot.
     *
     * <p>Only used to price <em>leaving</em> melee: stepping just outside a
     * zombie's arms is not a shooting position, it is the same fight one block
     * further out. Committing to range means walking back to somewhere she can
     * actually use it, and that walk is what makes her stop dithering on the
     * threshold.
     */
    private final double preferredRange;

    public static final TradeCost INSTANCE = new TradeCost(8.0D);

    public TradeCost(double preferredRange) {
        this.preferredRange = preferredRange;
    }

    /**
     * Damage she expects to eat if she fights this target with this weapon.
     *
     * <p>{@link Double#POSITIVE_INFINITY} means the weapon cannot do the job at
     * all here — not that it is a poor choice. Callers must still be able to
     * pick an infinite option when every option is infinite, because a bad
     * answer beats standing still.
     */
    public double of(WeaponCandidate weapon, EngagementContext context) {
        if (weapon == null || !weapon.usable()) {
            return Double.POSITIVE_INFINITY;
        }
        double cost = weapon.kind().isRanged()
                ? shootingCost(weapon, context)
                : swingingCost(weapon, context);
        if (!Double.isFinite(cost)) {
            return Double.POSITIVE_INFINITY;
        }
        return weapon.inHand() ? cost * (1.0D - IN_HAND_DISCOUNT) : cost;
    }

    /**
     * Walk in, then stand in its reach until it falls.
     *
     * <p>The approach is charged as time but not as damage: while she is
     * closing, whatever she is closing on generally cannot reach her either.
     * Anything that <em>can</em> hit her at that distance is already priced,
     * because the incoming rate is measured over the field as it stands.
     */
    private double swingingCost(
            WeaponCandidate weapon,
            EngagementContext context
    ) {
        if (context.targetAirborne()) {
            // Nothing to swing at. Stated as impossible rather than as
            // expensive, so no amount of sword quality can buy its way back in.
            return Double.POSITIVE_INFINITY;
        }
        double cadence = swingsPerSecond(weapon, context);
        double dps = weapon.damagePerSecond(cadence);
        if (dps <= 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        double killSeconds =
                context.targetHealth() / (dps + arcDps(weapon, context));
        double closeSeconds = approachSeconds(weapon, context);
        return (killSeconds + exposedApproach(context, closeSeconds))
                * context.incomingDps()
                + impatience(context, killSeconds + closeSeconds);
    }

    /**
     * How much of the walk in she actually pays for in blood.
     *
     * <p>None of it, while the thing she is closing on is the only thing that
     * could answer: it has to walk in too, so the approach is a race neither of
     * them is landing blows during. That was stated as a blanket assumption and
     * it is only true one-on-one.
     *
     * <p>With somebody else already inside a step of her, the walk is not a race
     * — it is a walk into a second pair of arms, and the blow she went in for is
     * paid for whether or not it lands. This is the condition that keeps a
     * one-blow kill from looking free in the middle of a crowd: the kill costs
     * no recovery, and going to collect it still costs her the approach.
     */
    private double exposedApproach(
            EngagementContext context,
            double closeSeconds
    ) {
        return context.hostilesPressing() > TOLERATED_ATTACKERS
                ? closeSeconds
                : 0.0D;
    }

    /**
     * Hold what distance she can, and shoot into it.
     *
     * <p>Two things can go wrong and they are priced separately. She may be
     * unable to keep the distance, in which case shooting is simply melee with
     * a worse weapon; and she may be unable to finish a draw, in which case it
     * is not a weapon at the moment at all.
     */
    private double shootingCost(
            WeaponCandidate weapon,
            EngagementContext context
    ) {
        double rate = context.rangedUsesPerSecond()
                * completedDrawShare(context);
        // Kept as a factor rather than folded away so the one real
        // interruption stays visible: she cannot draw and swing in the same
        // tick, so a decision that flips mid-draw throws it away. What is *not*
        // an interruption is being hit — see below.
        double dps = weapon.damagePerSecond(rate);
        if (dps <= 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        double reopenSeconds = reopenSeconds(context);
        double cycleSeconds = reopenSeconds + context.drawSeconds();
        if (!Double.isFinite(cycleSeconds) || cycleSeconds <= 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        // Ground retaken between shots is time not spent shooting, so it slows
        // the kill and exposes her, both in the same proportion.
        double dutyCycle = context.drawSeconds() / cycleSeconds;
        double killSeconds = context.targetHealth() / (dps * dutyCycle);
        double exposedShare = keepsDistance(context)
                ? reopenSeconds / cycleSeconds
                : 1.0D;
        // The shortfall scales the whole option, not only its delay. A quiver
        // that cannot cover the field does not merely postpone the fight, it
        // buys a worse version of it: the same crowd, closer, and now with
        // nothing left to shoot. Applied to the delay alone this was a factor
        // of one and a half against six zombies, which lost to the discount for
        // already holding the bow.
        return shortfall(weapon, context) * (
                killSeconds * exposedShare * context.incomingDps()
                        + impatience(context, killSeconds)
        );
    }

    /**
     * Damage a second the arc puts into everything that is not the target.
     *
     * <p>A sweeping weapon is paid once per body it reaches, so against a crowd
     * a sword does several times the work its single-target rating admits to.
     * That was missing entirely: melee was costed on what it does to the one
     * she aimed at, which makes six zombies pressed together look like six
     * separate problems instead of the one situation steel is best at.
     *
     * <p>Credited towards clearing the target rather than tracked separately,
     * which is a deliberate simplification and slightly generous — the arc does
     * not actually kill <em>this</em> one faster. What it does is end the fight
     * sooner, and the fight ending sooner is what the cost is trying to
     * express. Stated the honest way round: this is how much faster the
     * problem in front of her goes away, not how fast one zombie dies.
     *
     * <p>Zero for anything that does not sweep, and zero when she is facing one
     * thing — in both cases the arithmetic is exactly what it was.
     */
    /**
     * What the time an option takes is worth, beyond the damage it eats.
     *
     * <p>Charged once per body still on the field. A flat rate says a second
     * spent in front of six zombies costs what a second in front of one costs,
     * and it does not: at the end of it all six are still alive and all six are
     * closer. That flat rate is what put a bow in her hands against a pack —
     * ten arrows spent two hundred ticks to deliver forty damage, and shooting
     * priced cheaper only because it took no damage <em>while she was doing
     * it</em>.
     *
     * <p>Counted, not taken from the incoming rate. A successful kite eats none
     * of that damage and {@link #shootingCost} already discounts it by
     * exposure, so charging it again here would be double counting — and it
     * made a single zombie five blocks away enough to talk her out of the bow
     * she should obviously be using. Against one of anything this is exactly
     * the old flat rate.
     */
    private double impatience(EngagementContext context, double seconds) {
        double crowd = Math.max(1.0D, context.crowding());
        return seconds * IMPATIENCE_DAMAGE_PER_SECOND * crowd;
    }

    /**
     * How much worse the delay is for a weapon that cannot finish the fight.
     *
     * <p>One when the ammunition covers the field, and it grows in proportion
     * to how little of the field it covers. Ten arrows against six zombies can
     * deliver perhaps seventy of the hundred and twenty blocks of health in
     * front of her, so the quiver does not end the fight — it postpones the
     * melee by ten seconds and arrives there with the same crowd, closer.
     *
     * <p>Charged against the delay rather than against the damage on purpose.
     * Running dry is not dangerous in itself; what it costs is the time, and
     * time is what the delay term already measures. Applied to the damage it
     * would double-count exposure the exposure share already handles.
     *
     * <p>Melee reports unlimited and is unaffected, which is the whole point:
     * this is the term that tells a bow from a blade when the fight is bigger
     * than the quiver.
     */
    private double shortfall(WeaponCandidate weapon, EngagementContext context) {
        double deliverable = weapon.deliverableDamage();
        double field = context.fieldHealth();
        if (!Double.isFinite(deliverable) || deliverable <= 0.0D
                || field <= deliverable) {
            return 1.0D;
        }
        return field / deliverable;
    }

    /**
     * The share of each blow that lands on somebody other than the target.
     *
     * <p>Used to shorten the fight rather than to kill this one faster, which is
     * the honest way round: the arc does not take health off the body she aimed
     * at, it takes health off the ones she has not got to yet. Expressed as a
     * share so it composes with a blow count — half the swing landing elsewhere
     * means the fight is half again as short — where the old rate form had to be
     * added to a damage-per-second that no longer exists here.
     *
     * <p>Zero for anything that does not sweep, and zero against a single
     * hostile, so in both cases the arithmetic is exactly the blow count.
     */
    private double arcDps(WeaponCandidate weapon, EngagementContext context) {
        double others = Math.max(0.0D, context.crowding() - 1.0D);
        if (others <= 0.0D || weapon.arcDamage() <= 0.0D) {
            return 0.0D;
        }
        return weapon.arcDamage() * others * swingsPerSecond(weapon, context);
    }

    /**
     * How fast this weapon swings, asked of the weapon.
     *
     * <p>Her attack-speed attribute describes the item <em>in her hand</em>,
     * and a bow applies no modifier to it — so while she held one, every melee
     * candidate in her pack was priced at her bare 4.0 swings a second against
     * an iron sword's real 1.6. Melee output was overstated by two and a half
     * times, and every downstream comparison inherited it.
     *
     * <p>The domain suites alongside this one already state 1.6, so the two
     * halves of the codebase disagreed about the same sword: the arithmetic was
     * verified against the true rate and fed the inflated one.
     *
     * <p>It is also the only way an axe and a sword can be told apart. They
     * differ almost entirely in this number — 0.9 against 1.6, paid for with
     * damage — so a cadence taken from anywhere but the item makes one of them
     * a mispriced copy of the other.
     */
    private double swingsPerSecond(
            WeaponCandidate weapon,
            EngagementContext context
    ) {
        return weapon.usesPerSecond() > 0.0D
                ? weapon.usesPerSecond()
                : context.meleeUsesPerSecond();
    }

    /**
     * The fraction of her draws that survive to become a shot.
     *
     * <p>This used to say that being hit throws the draw away, and to charge
     * the bow accordingly — down to nothing when blows landed faster than she
     * could finish one. Nothing in this mod or in the game does that. Every
     * place a draw is abandoned is one of ours abandoning it on purpose: a
     * weapon swap, a mouthful, the fight ending. Damage does not touch it.
     *
     * <p>Left in place because the veto it produced was not harmless. Four
     * crossbowmen fire often enough between them that the gap between incoming
     * bolts is a quarter of a second, so the share came out zero, so her bow
     * priced at infinity, so she walked at them with a sword across their whole
     * killing ground — measured at ten arrows unfired in eleven runs out of
     * twelve, and not one of the four ever killed.
     *
     * <p>What was true about it is already priced elsewhere and better: being
     * unable to hold distance is what makes shooting expensive, and {@link
     * #shootingCost} charges the whole engagement's damage for it through the
     * exposure share. That is a cost rather than an impossibility, which is the
     * honest shape — a bow at arm's length is a bad weapon, not a missing one.
     */
    private double completedDrawShare(EngagementContext context) {
        double draw = context.drawSeconds();
        if (draw <= 0.0D) {
            // A weapon that fires the instant it is pointed spends no time
            // being anything other than ready.
            return 1.0D;
        }
        if (!mustCloseToHurtHer(context)) {
            // Nothing to anticipate: it reaches further than any distance she
            // would hold, so there is no moment at which it "arrives" and no
            // moment at which drawing stops being the right idea. A crossbowman
            // is as much in contact at fifteen blocks as at eight.
            //
            // This gate is the whole difference between the rule and the veto
            // it replaced. Asked the other way round — whether anything can hit
            // her at all — four pillagers priced her bow at infinity and she
            // walked into their killing ground with a sword, ten arrows unfired
            // in eleven runs out of twelve.
            return 1.0D;
        }
        // Something that has to close on her, and is about to. The shot still
        // goes off — nothing interrupts a draw — but it is the last one before
        // this becomes a melee, and a bow she has to put away again was worth
        // one arrow. A partial penalty rather than a veto, which is what makes
        // a sword win when one arrow is all she would get.
        double until = context.secondsToContact();
        if (!Double.isFinite(until) || until >= draw * 2.0D) {
            return 1.0D;
        }
        return Math.max(0.25D, until / (draw * 2.0D));
    }

    /**
     * Whether standing off actually keeps anything off her.
     *
     * <p>Three ways it fails: nowhere to go, nothing gained because it reaches
     * as far as she would stand, or too many of them for backing away from one
     * to mean backing away.
     */
    private boolean keepsDistance(EngagementContext context) {
        return context.canOpenGround() && mustCloseToHurtHer(context);
    }

    /**
     * Whether it has to come to her before it can do anything.
     *
     * <p>Half of {@link #keepsDistance}, and the half that is about the target
     * rather than about the ground she is standing on. The two were one
     * predicate and had to be split: being cornered is a reason shooting is
     * expensive, and it is not a reason to stop expecting the thing to arrive.
     * Conflated, a wall behind her made drawing a bow look <em>better</em>,
     * because losing the room to retreat also switched off the anticipation
     * that would have had her draw steel.
     */
    private boolean mustCloseToHurtHer(EngagementContext context) {
        return context.targetReach() + SpacingPolicy.instance().safeGap()
                < preferredRange;
    }

    /**
     * Seconds of walking she owes before the next shot is a safe one.
     *
     * <p>Zero when she already stands clear, which is what makes shooting free
     * in the open. The asymmetry that keeps her from dithering lives here: once
     * she is committed to melee, leaving costs the walk back to a shooting
     * distance rather than the single step that would put her outside its arms.
     * Nothing else needs a hysteresis constant.
     */
    private double reopenSeconds(EngagementContext context) {
        if (!keepsDistance(context)) {
            return 0.0D;
        }
        double wanted = context.holdingMelee()
                ? preferredRange
                : context.targetReach() + SpacingPolicy.instance().safeGap();
        double gap = wanted - context.distance();
        if (gap <= 0.0D) {
            return 0.0D;
        }
        return gap / GROUND_GAINED_PER_SECOND;
    }

    /** Seconds spent walking into her own reach. */
    private double approachSeconds(
            WeaponCandidate weapon,
            EngagementContext context
    ) {
        // How far she has to walk to be able to swing, which is her own reach
        // with this weapon — not the target's. Those differ, and the direction
        // they differ in is the whole reason a long weapon is worth carrying:
        // it stops her sooner, outside what is swinging back. Reading the
        // target's reach here priced every blade as though she had to close to
        // exactly the distance that lets it hit her.
        double strike = weapon.reach() > 0.0D
                ? weapon.reach()
                : context.targetReach();
        double gap = context.distance() - strike;
        if (gap <= 0.0D) {
            return 0.0D;
        }
        // Both of them are closing, so the gap shuts at about the rate the
        // threat aggregate already assumes hostiles travel at.
        return gap / 4.0D;
    }
}
