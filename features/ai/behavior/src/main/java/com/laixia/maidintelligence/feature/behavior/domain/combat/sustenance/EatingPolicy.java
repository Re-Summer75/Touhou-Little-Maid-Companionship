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
        // Her own state first, hunger last. The order used to be the other way
        // round and it is most of why she was late: a free moment went to the
        // cheapest thing that filled her stomach, so the window that should
        // have bought the apple bought bread, and the apple was still in the
        // pack when the axe arrived.
        if (desperate(healthFraction)) {
            return bestNetGain(larder, field);
        }
        FoodValue topUp = whileNothingCanReachHer(larder, field, capability);
        if (topUp != null) {
            return topUp;
        }
        FoodValue free = duringALull(larder, field, hungerFraction);
        if (free != null) {
            return free;
        }
        return whatTurnsTheFight(
                larder, field, capability, healthFraction, canOpenGround
        );
    }

    /**
     * Putting it on while there is still time to put it on.
     *
     * <p>The rule the other three left a hole where. Being about to die is
     * caught by {@code desperate}, being hungry by the lull, and a fight a
     * mouthful would win by {@code whatTurnsTheFight} — and none of those covers
     * the ordinary case of walking into something worse than she is currently
     * wearing. So a maid whose absorption had run out, who was not hungry, and
     * whose fight no single apple could rescue had no rule that could reach into
     * her own pack. She fought it bare and ate afterwards, if at all.
     *
     * <p>That last one is the sharpest of the three, because it fails backwards:
     * {@code whatTurnsTheFight} spends food only when the food makes the fight
     * winnable, so the harder the fight, the less likely she is to prepare for
     * it. Against four vindicators nothing in her pack moves the verdict, and
     * she was measured going in with ten golden apples and dying with eight.
     *
     * <p>Asked of her own state, which is what makes it fire again when a buff
     * wears off: the scanner prices an effect she is already carrying at zero,
     * so while absorption is up nothing here looks worth eating, and the moment
     * it lapses the same apple is worth its full four points again.
     *
     * <p>Cheapest that helps, not best. Reaching for the most protective thing
     * every time is what made an earlier version of this eat five golden apples
     * in a single fight — and her hands are the only thing in a fight that deals
     * damage, so a mouthful she did not need is a swing she did not take.
     */
    private FoodValue whileNothingCanReachHer(
            Collection<FoodValue> larder,
            ThreatField field,
            CombatCapability capability
    ) {
        if (field.isEmpty() || capability.bestDps() <= 0.0D) {
            return null;
        }
        double clearSeconds =
                field.convergingHealth() / capability.bestDps();
        if (clearSeconds * field.incomingDps()
                <= capability.effectiveHealth()) {
            // She can already pay for what is in front of her.
            return null;
        }
        FoodValue cheapest = null;
        for (FoodValue food : larder) {
            if (field.soonestContact() <= food.secondsToEat()
                    || netGain(food, field) <= 0.0D) {
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
     * About to die, measured the way the risk policy measures it.
     *
     * <p>A share of her maximum, which is the right shape for attrition: many
     * small hits walk her down through this line and it catches her on the way.
     */
    private boolean desperate(double healthFraction) {
        return healthFraction < bailOutHealthFraction;
    }

    // "About to die" was also tried the way the thing in front of her measures
    // it — effective health at or under the heaviest blow in the field, rather
    // than a share of her maximum. The reasoning survives the experiment: three
    // tenths of twenty is six, a vindicator's axe takes thirteen, so against a
    // heavy hitter the share is not a threshold but a band she steps over, and
    // she was measured dying with eight of ten golden apples still in her pack.
    //
    // It still measured worse, and not marginally: over twenty-four trials her
    // damage dealt fell from about fifty-five to forty, kills from 1.4 to 0.8,
    // and the share of the fight spent inside someone's reach rose. The rule
    // fires precisely when she cannot afford it. Being one blow from death and
    // being able to stand still for thirty-two ticks are the same situation
    // described twice, and the mouthful does not outrun the axe that defined
    // it — she ate, was hit anyway, and had spent a second and a half not
    // moving to buy four points of absorption against thirteen.
    //
    // Which leaves the apples genuinely hard to spend, and that is the real
    // finding rather than a failed patch: the moment they would save her is the
    // moment she has no time to eat one. Anything that wants to spend them has
    // to do it earlier, when nothing is in reach — which is the lull rule,
    // already here — or find a way to make the window cost less.

    // A fourth rule was tried here and removed: buffing up in a lull because
    // the fight ahead costs more than she is carrying, rather than because she
    // is hungry. It is the one shape of "spend the apples" that the two failed
    // attempts below and above point at — the window is only cheap when nothing
    // is standing in it, and the two hundred ticks she spends shooting are
    // exactly that.
    //
    // It measured worse than not eating at all: damage dealt fell from about
    // fifty-five to fifty, and the reason is visible in where her hands went.
    // Her time holding a sword collapsed from a hundred and fifty ticks to
    // forty-seven, and her swings with it from 2.5 to 1.5, because a maid who
    // is chewing is a maid holding an apple — the fight leaves the weapon in
    // the pack while she eats, correctly, and she ate five and a half of them.
    //
    // So all three approaches to the larder have now failed, and they failed
    // for one reason rather than three: an apple costs thirty-two ticks of her
    // hands, and her hands are the only thing in this fight that deals damage.
    // Four points of absorption do not buy back thirty-two ticks of not
    // shooting. The apples are not a health pool she is failing to spend —
    // against something that kills her in two blows, they are not worth their
    // window, and the maid declining to eat them was right.

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
            // 会扣她血的那一口不算"便宜"，它是负的。这条规则挑的是最不值钱
            // 的东西，而一旦副作用能把价值压到零以下，"最不值钱"就直接指向
            // 毒马铃薯——排序没错，是"便宜"这个词在有害食物出现之后不再等于
            // "损失最小"。
            //
            // 这条规则在空场（`EatFromPackAction`）下也是唯一会开火的一条，
            // 所以这一句同时意味着：包里只剩有毒的东西时，本模组不去吃它。
            // 那种处境交给宿主自己的进食去管——它按 `isEdible()` 认食物，腐肉
            // 之类照吃不误，饿不死；而"有更好的却挑了毒的"只有这里挡得住。
            if (food.effectiveHealthGain() < 0.0D) {
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
            // 逐项照抄，包括盾牌那一项。这里问的是"多了这口饭之后答案变不变"，
            // 唯一该动的就是有效生命；用短构造会把她举着的盾静默归零，于是
            // 喂饱的她反而比空腹的她更弱，而这条规则正是靠比较两者来决定要不
            // 要吃——被比较的两边必须只差那一口饭。
            CombatCapability fed = new CombatCapability(
                    capability.effectiveHealth() + gain,
                    capability.meleeDps(),
                    capability.rangedDps(),
                    capability.meleeReach(),
                    capability.guardedShare()
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
