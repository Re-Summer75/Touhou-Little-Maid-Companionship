package com.laixia.maidintelligence.feature.behavior.domain.combat.sustenance;

import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatBalance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementRiskPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.RiskVerdict;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.TradeCost;

import java.util.Collection;
import java.util.Objects;

/**
 * Whether to eat, and which one — decided in the fight's own currency.
 *
 * <p>Eating is not a chore here and it is not free. It costs a second and a
 * half of her hands, which with three things on her is a second and a half of
 * being hit for nothing; and what it buys is health she gets to keep. Both
 * sides are already the units the rest of the fight is settled in, so the
 * question needs no new scale: eat when what it buys exceeds what it costs, and
 * when what it buys is <em>needed</em>.
 *
 * <p>That second clause is the one that stops her drinking her stores. A golden
 * apple spent on a fight she was going to win is a golden apple she does not
 * have for the one she was not, and no amount of "it made her safer" recovers
 * it. So the test is not whether the food helps — it always helps — but whether
 * the fight comes out differently for having eaten it.
 *
 * <p>Four situations, in the order they matter:
 *
 * <ul>
 *   <li><b>Nothing can reach her.</b> Then eating costs nothing at all, and the
 *       only question left is whether she is hungry. This is the lull, the
 *       retreat, the moment before they arrive.</li>
 *   <li><b>She is about to die.</b> Below her bail-out she is leaving anyway;
 *       the question is whether she survives the leaving. Food that outruns
 *       what is landing on her buys exactly that.</li>
 *   <li><b>She would lose this fight and would win it fed.</b> The verdict is
 *       re-asked with the food already in her, and only a verdict that changes
 *       is worth a golden apple.</li>
 *   <li><b>Anything else.</b> She keeps it.</li>
 * </ul>
 */
public final class EatingPolicy {
    /**
     * The one in force.
     *
     * <p>Shares {@link CombatBalance}'s bail-out with the risk policy on
     * purpose: "she is about to die" has to mean the same thing to the rule
     * that makes her leave and to the rule that makes her eat, or she will eat
     * at a moment she is not leaving and leave at a moment she is not eating.
     */
    private static volatile EatingPolicy instance = of(CombatBalance.defaults());

    /**
     * How hungry she has to be before a free moment is worth spending.
     *
     * <p>Not zero: topping up from ninety-five is a second of her hands for
     * nothing, and there is always another mouthful of nothing to be had. Two
     * thirds is where the host's own regeneration starts falling off, so it is
     * also the point at which being fed stops being cosmetic.
     */
    private static final double PECKISH = 0.66D;

    private final double bailOutHealthFraction;

    public EatingPolicy(double bailOutHealthFraction) {
        this.bailOutHealthFraction = bailOutHealthFraction;
    }

    public static EatingPolicy of(CombatBalance balance) {
        return new EatingPolicy(balance.bailOutHealth());
    }

    public static EatingPolicy instance() {
        return instance;
    }

    public static void install(CombatBalance balance) {
        instance = of(balance);
    }

    /**
     * What she should eat right now, or {@code null} to eat nothing.
     *
     * @param larder         everything edible she is carrying
     * @param field          the crowd, already aggregated
     * @param capability     what she brings to the fight without eating
     * @param healthFraction her health over her maximum, in {@code [0,1]}
     * @param hungerFraction her hunger over its maximum, in {@code [0,1]}
     * @param canOpenGround  whether backing off is something she could carry out
     */
    public FoodValue choose(
            Collection<FoodValue> larder,
            ThreatField field,
            CombatCapability capability,
            double healthFraction,
            double hungerFraction,
            boolean canOpenGround
    ) {
        Objects.requireNonNull(larder, "larder");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(capability, "capability");
        if (larder.isEmpty()) {
            return null;
        }
        FoodValue free = duringALull(larder, field, hungerFraction);
        if (free != null) {
            return free;
        }
        if (healthFraction < bailOutHealthFraction) {
            return bestNetGain(larder, field);
        }
        return whatTurnsTheFight(
                larder, field, capability, healthFraction, canOpenGround
        );
    }

    /**
     * The mouthful she can take for nothing.
     *
     * <p>Free means nothing arrives before she has swallowed — measured against
     * the food's own eating time rather than a fixed pause, so a cake and an
     * apple are not treated as the same interruption. Anything nourishing will
     * do here; this is about hunger, not about the fight, and the cheapest thing
     * that feeds her is the right thing to spend.
     *
     * <p>This is also the rule that covers running away. A retreat she is
     * winning is a stretch of seconds in which nothing can reach her, and it is
     * the best moment in the whole fight to be eating.
     */
    private FoodValue duringALull(
            Collection<FoodValue> larder,
            ThreatField field,
            double hungerFraction
    ) {
        if (hungerFraction > PECKISH) {
            return null;
        }
        FoodValue cheapest = null;
        for (FoodValue food : larder) {
            if (!food.nourishing()
                    || field.soonestContact() <= food.secondsToEat()) {
                continue;
            }
            // Least restorative first: a lull is the wrong moment to spend the
            // apple, and hunger does not care which item filled it.
            if (cheapest == null
                    || food.effectiveHealthGain()
                            < cheapest.effectiveHealthGain()) {
                cheapest = food;
            }
        }
        return cheapest;
    }

    /**
     * The most health she can put on before the next blow lands.
     *
     * <p>Net of what eating costs her, which is what makes this a decision
     * rather than a reflex: a mouthful that heals two while four arrive during
     * the chewing has made her worse, and reaching for it because she is dying
     * is how being nearly dead becomes being dead.
     */
    private FoodValue bestNetGain(
            Collection<FoodValue> larder,
            ThreatField field
    ) {
        FoodValue best = null;
        double bestGain = 0.0D;
        for (FoodValue food : larder) {
            double gain = netGain(food, field);
            if (gain > bestGain) {
                bestGain = gain;
                best = food;
            }
        }
        return best;
    }

    /**
     * The cheapest thing that changes the answer, or nothing.
     *
     * <p>The verdict is asked twice — once as she stands, once with the food
     * already in her — and only a food that moves it to {@link
     * RiskVerdict#ENGAGE} is spent. A fight she already wins takes nothing from
     * her stores, and a fight she loses either way takes nothing either, because
     * eating would only mean losing it slightly later.
     *
     * <p>Cheapest rather than best, deliberately. Once the answer has changed
     * there is nothing further to buy, and the difference between the bread that
     * was enough and the apple that was more than enough is an apple.
     */
    private FoodValue whatTurnsTheFight(
            Collection<FoodValue> larder,
            ThreatField field,
            CombatCapability capability,
            double healthFraction,
            boolean canOpenGround
    ) {
        EngagementRiskPolicy risk = EngagementRiskPolicy.instance();
        if (risk.assess(field, capability, healthFraction, canOpenGround)
                == RiskVerdict.ENGAGE) {
            return null;
        }
        FoodValue cheapest = null;
        for (FoodValue food : larder) {
            double gain = netGain(food, field);
            if (gain <= 0.0D) {
                continue;
            }
            CombatCapability fed = new CombatCapability(
                    capability.effectiveHealth() + gain,
                    capability.meleeDps(),
                    capability.rangedDps(),
                    capability.meleeReach()
            );
            if (risk.assess(field, fed, healthFraction, canOpenGround)
                    != RiskVerdict.ENGAGE) {
                continue;
            }
            if (cheapest == null
                    || food.effectiveHealthGain()
                            < cheapest.effectiveHealthGain()) {
                cheapest = food;
            }
        }
        return cheapest;
    }

    /**
     * What eating it is worth after paying for the time it takes.
     *
     * <p>The subtraction is the whole of "is this a good moment", and what is
     * subtracted took two goes to get right. It was the incoming rate — the
     * damage arriving while she chews — and that is not a cost of eating. It
     * arrives whether she eats or not. She is standing in it either way; the
     * mouthful does not summon it.
     *
     * <p>Charged that way she would not eat under fire at all: four crossbowmen
     * put twenty damage a second into her, which made every apple in her pack
     * look like a losing trade, and she died with five of them measured on her.
     *
     * <p>What eating actually costs is the seconds she is not fighting in — and
     * a second of a fight is already priced, per body, by the same figure the
     * weapon choice uses for dithering.
     *
     * <p>Per body <em>that can already touch her</em>, which is the difference
     * between a window she opens standing in a huddle and one she opens while
     * backing away. Retreating and eating are not exclusive: the seconds she
     * spends chewing are seconds anything that has to close on her spends
     * walking, so the window is genuinely cheaper — and against something that
     * reaches her from where it stands, it is not cheaper at all, which is the
     * same arithmetic saying so. Charged over the whole field instead, a maid
     * kiting six zombies priced a mouthful as though all six were on her.
     *
     * <p>The window itself comes from the food. A snatched bite and a slow meal
     * are different sizes of risk and the item is the only thing that knows
     * which this is.
     */
    private double netGain(FoodValue food, ThreatField field) {
        double exposed = Math.max(1.0D, field.pressing());
        return food.effectiveHealthGain()
                - TradeCost.IMPATIENCE_DAMAGE_PER_SECOND
                        * exposed * food.secondsToEat();
    }
}
