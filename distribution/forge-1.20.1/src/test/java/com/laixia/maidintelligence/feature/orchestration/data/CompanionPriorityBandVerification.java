package com.laixia.maidintelligence.feature.orchestration.data;

import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.utility
        .UtilityAggregation;
import com.laixia.maidintelligence.feature.orchestration.domain.utility
        .UtilityConsideration;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToDoubleFunction;

/**
 * Holds the shipped catalog to the shape that makes its utility layer real.
 *
 * <p>The engine compares {@code interruptPriority} first and only falls through
 * to score when two candidates share one — a property its own pruning test
 * relies on. So a catalog where every intent has a distinct priority is a
 * priority list with utility bolted to the side: the scores are computed, and
 * then never consulted. That was the state of this data pack, and no test would
 * have noticed, because every individual definition was valid.
 *
 * <p>What follows therefore checks the catalog as a whole rather than its
 * entries: intents must be grouped into bands, bands must contain more than one
 * member, and members of a band must be comparable — which is what
 * {@code PRODUCT} buys and {@code SUM} does not, since a sum is unbounded and
 * its base score only means the same thing between intents that happen to
 * declare the same number of terms.
 */
public final class CompanionPriorityBandVerification {
    /**
     * Bands the catalog is allowed to use.
     *
     * <p>Deliberately few and named after what may interrupt what, not after
     * preference: preference is the score's job, and encoding it here is how a
     * priority ladder grows back one intent at a time.
     */
    private static final int SAFETY = 100;
    private static final int OWNER_COMMAND = 80;
    private static final int NEEDS = 50;
    private static final int COMPANIONSHIP = 30;
    private static final int LEISURE = 10;

    private static final Set<Integer> BANDS =
            Set.of(SAFETY, OWNER_COMMAND, NEEDS, COMPANIONSHIP, LEISURE);

    private CompanionPriorityBandVerification() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, IntentDefinition> intents = bundledIntents();
        everyIntentIsScoreComparable(intents);
        prioritiesFallIntoDeclaredBands(intents);
        bandsActuallyContest(intents);
        hungerBandPrefersTheCheapestFood(intents);
        beggingReadsWhetherTheOwnerCanHelp(intents);
        followingStaysTheAnswerToDistance(intents);
        everyIntentIsReachable(intents);
        nothingLeansOnADieRoll(intents);
        reportNeedsBand(intents);
    }

    /**
     * An intent whose considerations can never all be satisfied is dead code
     * that no compiler reports.
     *
     * <p>{@code keep_company} was exactly that: its favourability consideration
     * was normalised over 32–128, written as though the fact were the raw point
     * total, while the fact reader publishes
     * {@code getFavorabilityManager().getLevel()} — which tops out at three. It
     * therefore normalised to zero always, and at full weight a product factor
     * of zero is a score of zero. It had never once won a contest.
     *
     * <p>Scored at the best each consideration can honestly reach, so the claim
     * is "there exists a situation where this can win", not "it usually does".
     */
    private static void everyIntentIsReachable(
            Map<String, IntentDefinition> intents
    ) {
        for (Map.Entry<String, IntentDefinition> entry : intents.entrySet()) {
            IntentDefinition intent = entry.getValue();
            double best = intent.baseScore();
            for (UtilityConsideration consideration
                    : intent.considerations()) {
                best = intent.aggregation().combine(
                        best,
                        intent.aggregation().term(
                                consideration,
                                bestCase(consideration)
                        )
                );
            }
            best = intent.aggregation().finish(
                    best, intent.considerations().size()
            );
            require(
                    best > intent.minimumScore(),
                    entry.getKey() + " cannot score above its own minimum "
                            + "even at its best (" + best + "); one of its "
                            + "considerations is normalised over a range the "
                            + "fact never reaches"
            );
        }
    }

    /**
     * Whichever end of a consideration's range scores highest.
     *
     * <p>Read off the curve rather than assumed, so an inverse consideration is
     * given its own best case instead of being judged at the end that is worst
     * for it.
     */
    private static double bestCase(UtilityConsideration consideration) {
        double low = consideration.factor(consideration.minimum());
        double high = consideration.factor(consideration.maximum());
        return high >= low
                ? consideration.maximum()
                : consideration.minimum();
    }

    /**
     * A probability is what a definition uses when it has no consideration to
     * express the thing it actually means.
     *
     * <p>Six intents rolled dice on activation: begging twice, keeping company,
     * turning a wander into a return, and both ways of settling down. Each roll
     * stood in for a judgement — how hungry, how trusted, how far she had
     * drifted — that the facts could already answer. Cooldowns, not chance, are
     * what keep her from repeating herself.
     */
    private static void nothingLeansOnADieRoll(
            Map<String, IntentDefinition> intents
    ) {
        for (Map.Entry<String, IntentDefinition> entry : intents.entrySet()) {
            require(
                    entry.getValue().activationChance() >= 1.0D,
                    entry.getKey() + " still rolls for activation at "
                            + entry.getValue().activationChance()
                            + "; the consideration it stands in for belongs "
                            + "in the utility instead"
            );
        }
    }

    /**
     * A single consideration at full weight scores zero at its own lower bound,
     * because a product factor of {@code 1 - 1.0 * (1 - 0)} is nothing. Under
     * the old sum it merely added nothing to the base and the intent still won.
     *
     * <p>That difference is invisible in a passing catalog and cost
     * {@code follow_owner} every contest in its band: at six blocks it scored
     * zero, and even at eighteen it lost to a returning intent whose base was
     * higher. Following is the band's whole point, so it is pinned here.
     */
    private static void followingStaysTheAnswerToDistance(
            Map<String, IntentDefinition> intents
    ) {
        IntentDefinition following = intents.get("follow_owner");
        for (double distance : new double[]{6.0D, 12.0D, 24.0D}) {
            double follow =
                    score(following, facts(60.0D, 0.0D, 0.0D, distance));
            require(
                    follow > 0.0D,
                    "follow_owner scored zero at " + distance + " blocks; a "
                            + "full-weight lone consideration has collapsed it"
            );
            for (String rival : new String[]{
                    "post_task_return", "wander_return", "keep_company"
            }) {
                double other = score(
                        intents.get(rival),
                        facts(60.0D, 0.0D, 0.0D, distance)
                );
                require(
                        follow >= other,
                        "At " + distance + " blocks " + rival + " (" + other
                                + ") outranks follow_owner (" + follow
                                + "), so walking to her owner is decided by "
                                + "something other than how far away he is"
                );
            }
        }
    }

    /**
     * Print what the needs band actually decides, in two situations.
     *
     * <p>A passing ordering assertion says the ranks are right; it says nothing
     * about whether they are close enough for a change in the world to move
     * them. Printed so a reader can see that the gaps are workable rather than
     * three intents pinned apart by their base scores.
     */
    private static void reportNeedsBand(
            Map<String, IntentDefinition> intents
    ) {
        report(intents, "owner empty-handed", facts(4.0D, 0.0D, 0.0D, 3.0D));
        report(intents, "owner holding food", facts(4.0D, 1.0D, 0.25D, 3.0D));
        for (double distance : new double[]{6.0D, 10.0D, 18.0D}) {
            System.out.println(
                    "companionship band, owner " + (int) distance
                            + " blocks: follow_owner="
                            + String.format("%.3f", score(
                                    intents.get("follow_owner"),
                                    facts(60.0D, 0.0D, 0.0D, distance)
                            ))
                            + " post_task_return="
                            + String.format("%.3f", score(
                                    intents.get("post_task_return"),
                                    facts(60.0D, 0.0D, 0.0D, distance)
                            ))
                            + " wander_return="
                            + String.format("%.3f", score(
                                    intents.get("wander_return"),
                                    facts(60.0D, 0.0D, 0.0D, distance)
                            ))
                            + " keep_company="
                            + String.format("%.3f", score(
                                    intents.get("keep_company"),
                                    facts(60.0D, 0.0D, 0.0D, distance)
                            ))
            );
        }
    }

    private static void report(
            Map<String, IntentDefinition> intents,
            String label,
            Map<OrchestrationId, Double> facts
    ) {
        StringBuilder line = new StringBuilder("needs band, ")
                .append(label)
                .append(':');
        for (String name : new String[]{
                "loose_food_meal",
                "snack_cabinet_meal",
                "hungry_feedback",
                "hungry_high_trust",
                "hungry_standard"
        }) {
            line.append(' ').append(name).append('=')
                    .append(String.format(
                            "%.3f", score(intents.get(name), facts)
                    ));
        }
        System.out.println(line);
    }

    /**
     * A band is only meaningful if its members' scores mean the same thing.
     *
     * <p>{@code SUM} scores are unbounded and grow with the number of terms, so
     * putting a one-term and a three-term intent in one band would rank them by
     * how thoroughly each was described.
     */
    private static void everyIntentIsScoreComparable(
            Map<String, IntentDefinition> intents
    ) {
        for (Map.Entry<String, IntentDefinition> entry : intents.entrySet()) {
            require(
                    entry.getValue().aggregation() == UtilityAggregation.PRODUCT,
                    entry.getKey() + " still aggregates by SUM, so its score "
                            + "cannot be compared with the rest of its band"
            );
        }
    }

    private static void prioritiesFallIntoDeclaredBands(
            Map<String, IntentDefinition> intents
    ) {
        for (Map.Entry<String, IntentDefinition> entry : intents.entrySet()) {
            require(
                    BANDS.contains(entry.getValue().interruptPriority()),
                    entry.getKey() + " uses priority "
                            + entry.getValue().interruptPriority()
                            + ", which is not one of the declared bands "
                            + new TreeSet<>(BANDS)
            );
        }
    }

    /**
     * The point of the whole exercise: at least one band has to contain a real
     * contest, or utility still decides nothing.
     */
    private static void bandsActuallyContest(
            Map<String, IntentDefinition> intents
    ) {
        Map<Integer, Integer> populations = new LinkedHashMap<>();
        for (IntentDefinition intent : intents.values()) {
            populations.merge(intent.interruptPriority(), 1, Integer::sum);
        }
        int contested = 0;
        for (int population : populations.values()) {
            if (population > 1) {
                contested++;
            }
        }
        require(
                contested >= 2,
                "Only " + contested + " band(s) hold more than one intent; "
                        + "with the rest decided on priority alone the score "
                        + "is still ornamental"
        );
        require(
                populations.size() <= BANDS.size(),
                "The catalog uses " + populations.size()
                        + " distinct priorities, which is a ladder again"
        );
    }

    /**
     * Food she can simply pick up should outrank food behind a cabinet door,
     * which should outrank asking someone else for it — at equal hunger.
     *
     * <p>Asserted as an ordering rather than as three numbers, because the
     * numbers are meant to be tuned and the ordering is not.
     */
    private static void hungerBandPrefersTheCheapestFood(
            Map<String, IntentDefinition> intents
    ) {
        ToDoubleFunction<IntentDefinition> starving =
                intent -> score(intent, facts(4.0D, 0.0D, 0.0D, 3.0D));
        double loose = starving.applyAsDouble(intents.get("loose_food_meal"));
        double cabinet =
                starving.applyAsDouble(intents.get("snack_cabinet_meal"));
        double begging =
                starving.applyAsDouble(intents.get("hungry_standard"));
        require(
                loose > cabinet,
                "Food on the floor did not outrank walking to a cabinet ("
                        + loose + " vs " + cabinet + ")"
        );
        require(
                cabinet > begging,
                "Helping herself did not outrank asking her owner ("
                        + cabinet + " vs " + begging + ")"
        );

        /*
         * The two begging branches run the same plan and differ only in what
         * the Soul Lens calls them, so an inversion here is not a wrong action
         * — it is a maid at full trust described as making a plain request.
         * The affectionate branch exists for that moment; if it cannot outrank
         * the plain one at maximum favourability it has no reason to exist.
         */
        double affectionate =
                starving.applyAsDouble(intents.get("hungry_high_trust"));
        require(
                affectionate > begging,
                "At full favourability the affectionate request (" + affectionate
                        + ") did not outrank the plain one (" + begging + ")"
        );
    }

    /**
     * The behaviour that could not be expressed before this change.
     *
     * <p>Whether asking is worth doing depends on whether the person being
     * asked is holding anything. Under a priority ladder that fact could only
     * have been a hard condition — she asks, or she is forbidden to. As a
     * consideration it changes how much she wants to, which is what lets an
     * owner with full hands outrank a cabinet across the room while an
     * empty-handed one does not.
     */
    private static void beggingReadsWhetherTheOwnerCanHelp(
            Map<String, IntentDefinition> intents
    ) {
        IntentDefinition begging = intents.get("hungry_standard");
        double holdingFood = score(begging, facts(4.0D, 1.0D, 0.25D, 3.0D));
        double emptyHanded = score(begging, facts(4.0D, 0.0D, 0.0D, 3.0D));
        require(
                holdingFood > emptyHanded,
                "Begging scored the same whether or not her owner had food ("
                        + holdingFood + " vs " + emptyHanded + "); the owner "
                        + "facts are being computed and ignored again"
        );
    }

    /** One fact vector, named so the scenarios above read as situations. */
    private static Map<OrchestrationId, Double> facts(
            double hunger,
            double ownerHoldingFood,
            double ownerInventoryFood,
            double ownerDistance
    ) {
        Map<OrchestrationId, Double> facts = new LinkedHashMap<>();
        facts.put(id("fact/hunger"), hunger);
        facts.put(id("fact/owner_holding_food"), ownerHoldingFood);
        facts.put(id("fact/owner_inventory_food"), ownerInventoryFood);
        facts.put(id("fact/owner_distance"), ownerDistance);
        // Favourability high enough that keep_company is a genuine rival
        // rather than one that loses on being barely acquainted, and a forecast
        // in the middle of its band. Both are held constant so the scenarios
        // vary only in what they are named after.
        facts.put(id("fact/favorability"), 3.0D);
        facts.put(id("fact/forecast_lift/travelling"), 0.64D);
        return facts;
    }

    /**
     * Score one intent, through the engine's own aggregation.
     *
     * <p>Calling {@link UtilityAggregation} rather than restating the formula
     * matters: a test that reimplemented the arithmetic would agree with itself
     * while disagreeing with the maid.
     */
    private static double score(
            IntentDefinition intent,
            Map<OrchestrationId, Double> facts
    ) {
        UtilityAggregation aggregation = intent.aggregation();
        double accumulated = intent.baseScore();
        for (UtilityConsideration consideration : intent.considerations()) {
            Double actual = facts.get(consideration.fact());
            require(
                    actual != null,
                    "Scenario does not supply " + consideration.fact()
                            + ", which " + intent.id() + " consults"
            );
            accumulated = aggregation.combine(
                    accumulated,
                    aggregation.term(consideration, actual)
            );
        }
        return aggregation.finish(
                accumulated, intent.considerations().size()
        );
    }

    private static Map<String, IntentDefinition> bundledIntents()
            throws IOException {
        Map<String, IntentDefinition> intents = new LinkedHashMap<>();
        for (String name : BundledIntentResources.intentNames()) {
            intents.put(name, BundledIntentResources.intent(name));
        }
        require(
                intents.size() >= 15,
                "Only found " + intents.size() + " bundled intents"
        );
        return intents;
    }

    private static OrchestrationId id(String path) {
        return new OrchestrationId("tlm_companionship", path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
