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
    private static final double IMPATIENCE_DAMAGE_PER_SECOND = 1.0D;

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
        return killSeconds * context.incomingDps()
                + impatience(context, killSeconds + closeSeconds);
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
     * <p>Being hit throws the draw away. If blows land more often than she can
     * finish one, the bow in her hands is scenery — and that, rather than any
     * threshold, is why something standing on her has to be answered with
     * steel. A slow heavy hitter leaves room between blows and she can shoot it
     * point blank; a fast one does not and she cannot.
     */
    private double completedDrawShare(EngagementContext context) {
        double draw = context.drawSeconds();
        if (draw <= 0.0D) {
            // Nothing to interrupt. A weapon that fires the instant it is
            // pointed is not troubled by being in a brawl.
            return 1.0D;
        }
        if (!context.underAttack()) {
            // Not being hit yet — but "yet" is the whole question. Something
            // arriving in half a second ruins a draw that takes a second just
            // as surely as something already swinging, and waiting for it to
            // land before admitting that is how she ends up caught mid-draw
            // every single time. The first shot still goes off, so this is a
            // partial penalty rather than a veto: enough that a sword wins when
            // one arrow is all she would get.
            double until = context.secondsToContact();
            if (!Double.isFinite(until) || until >= draw * 2.0D) {
                return 1.0D;
            }
            return Math.max(0.25D, until / (draw * 2.0D));
        }
        double gap = context.secondsBetweenHits();
        if (!Double.isFinite(gap)) {
            return 1.0D;
        }
        return Math.max(0.0D, 1.0D - draw / gap);
    }

    /**
     * Whether standing off actually keeps anything off her.
     *
     * <p>Three ways it fails: nowhere to go, nothing gained because it reaches
     * as far as she would stand, or too many of them for backing away from one
     * to mean backing away.
     */
    private boolean keepsDistance(EngagementContext context) {
        return context.canOpenGround()
                && context.targetReach() + SpacingPolicy.instance().safeGap() < preferredRange;
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
