package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.forecast.ActivityForecast;
import com.laixia.maidintelligence.feature.behavior.domain.forecast.ActivityTransitionTable;

/**
 * The prediction layer: that it is a probability distribution, that it learns
 * order rather than frequency, and that it says how much it actually knows.
 */
public final class ActivityForecastVerification {
    private static final int MINING = 0;
    private static final int WALKING = 1;
    private static final int SMELTING = 2;
    private static final int FARMING = 3;
    private static final int BUILDING = 4;
    private static final int ACTIVITIES = 5;
    private static final double EPSILON = 1.0E-9D;

    private ActivityForecastVerification() {
    }

    public static void main(String[] args) {
        transitionsFormADistribution();
        unseenTransitionsStayPossible();
        repeatedRoutineBecomesThePrediction();
        theSameSuccessorDependsOnWhatCameBefore();
        confidenceGrowsWithContextEvidence();
        blendFollowsConfidence();
        blendedForecastStillSumsToOne();
        forgettingCapsEvidenceAndKeepsShape();
        identicalHistoriesProduceIdenticalForecasts();
        baseRateIsSmoothedAndNeverZero();
        liftIsCentredOnTheBaseRate();
        liftMeansTheSameThingForRareAndCommonActivities();
        outOfRangeActivitiesAreRejected();
    }

    private static void baseRateIsSmoothedAndNeverZero() {
        ActivityForecast forecast = new ActivityForecast(ACTIVITIES);
        for (int repeat = 0; repeat < 100; repeat++) {
            forecast.observe(MINING, WALKING, SMELTING, 4);
        }
        require(forecast.baseRate(BUILDING) > 0.0D,
                "A never-seen activity got a zero base rate, which would make "
                        + "its lift infinite");
        require(forecast.baseRate(SMELTING) > forecast.baseRate(BUILDING),
                "The observed activity does not have the higher base rate");
        double total = 0.0D;
        for (int activity = 0; activity < ACTIVITIES; activity++) {
            total += forecast.baseRate(activity);
        }
        require(Math.abs(total - 1.0D) < EPSILON,
                "Base rates sum to " + total + ", not one");
    }

    private static void liftIsCentredOnTheBaseRate() {
        ActivityForecast forecast = new ActivityForecast(ACTIVITIES);
        for (int repeat = 0; repeat < 200; repeat++) {
            forecast.observe(MINING, WALKING, SMELTING, 4);
            forecast.observe(WALKING, SMELTING, MINING, 4);
            forecast.observe(SMELTING, MINING, WALKING, 4);
        }
        for (int next = 0; next < ACTIVITIES; next++) {
            double lift = forecast.lift(MINING, WALKING, next);
            require(lift >= 0.0D && lift < 1.0D,
                    "Lift left [0, 1) for activity " + next + ": " + lift);
        }
        double learned = forecast.lift(MINING, WALKING, SMELTING);
        double unrelated = forecast.lift(MINING, WALKING, BUILDING);
        require(learned > 0.5D,
                "A routine successor was not reported as unusually likely");
        require(unrelated < 0.5D,
                "An unrelated activity was not reported as unusually "
                        + "unlikely");
    }

    /**
     * The reason lift exists, stated precisely: it does not make a common and a
     * rare activity produce the same number — a rare successor really is more
     * informative — but it does give them the <em>same neutral point</em>.
     *
     * <p>That is what a data pack threshold needs. Raw probability has no
     * shared landmark at all: {@code 0.4} is below par for an activity that
     * happens half the time and extraordinary for one that happens rarely, so a
     * threshold copied between two activities silently changes meaning. With
     * lift, {@code 0.5} is "as usual" for every activity and a threshold above
     * it means "more than usual" everywhere.
     */
    private static void liftMeansTheSameThingForRareAndCommonActivities() {
        ActivityForecast forecast = new ActivityForecast(ACTIVITIES);
        /*
         * WALKING common but not universal. An activity that happens almost
         * every time cannot be lifted far by any context — there is no surprise
         * left to report — so a scenario that made it near-universal would be
         * testing that ceiling rather than the landmark this is about.
         */
        for (int repeat = 0; repeat < 300; repeat++) {
            forecast.observe(MINING, SMELTING, WALKING, 4);
            forecast.observe(SMELTING, WALKING, MINING, 4);
            forecast.observe(WALKING, MINING, FARMING, 4);
            if (repeat % 20 == 0) {
                forecast.observe(FARMING, MINING, BUILDING, 4);
            }
        }
        double commonBase = forecast.baseRate(WALKING);
        double rareBase = forecast.baseRate(BUILDING);
        require(commonBase > rareBase * 5.0D,
                "The scenario did not produce a common and a rare activity ("
                        + commonBase + " vs " + rareBase + ")");

        /*
         * A context with no evidence either way. Raw probability answers with
         * each activity's own base rate, which is exactly the problem; lift
         * answers 0.5 for both, which is exactly the fix.
         */
        double commonLift = forecast.lift(BUILDING, BUILDING, WALKING);
        double rareLift = forecast.lift(BUILDING, BUILDING, BUILDING);
        require(Math.abs(commonLift - 0.5D) < 1.0E-9D,
                "A common activity in an unseen context read " + commonLift
                        + " instead of the neutral 0.5");
        require(Math.abs(rareLift - 0.5D) < 1.0E-9D,
                "A rare activity in an unseen context read " + rareLift
                        + " instead of the neutral 0.5");

        // Direction is shared too: both rise above the landmark where their own
        // routine holds, and fall below it where it does not.
        require(forecast.lift(MINING, SMELTING, WALKING) > 0.6D,
                "A common activity's own routine did not read above neutral");
        require(forecast.lift(FARMING, MINING, BUILDING) > 0.6D,
                "A rare activity's own routine did not read above neutral");
        require(forecast.lift(MINING, SMELTING, BUILDING) < 0.5D,
                "A successor that never follows did not read below neutral");
    }

    private static void transitionsFormADistribution() {
        ActivityTransitionTable table =
                new ActivityTransitionTable(ACTIVITIES);
        table.observe(MINING, WALKING, SMELTING);
        table.observe(MINING, WALKING, SMELTING);
        table.observe(MINING, WALKING, FARMING);
        double total = 0.0D;
        for (int next = 0; next < ACTIVITIES; next++) {
            double probability = table.probability(MINING, WALKING, next);
            require(probability > 0.0D && probability < 1.0D,
                    "Transition probability left (0, 1): " + probability);
            total += probability;
        }
        require(Math.abs(total - 1.0D) < EPSILON,
                "Transition probabilities sum to " + total + ", not one");
    }

    /**
     * A zero here would reach a multiplicative consideration as a veto, so one
     * coincidence would permanently rule a behaviour out.
     */
    private static void unseenTransitionsStayPossible() {
        ActivityTransitionTable table =
                new ActivityTransitionTable(ACTIVITIES);
        for (int repeat = 0; repeat < 50; repeat++) {
            table.observe(MINING, WALKING, SMELTING);
        }
        require(table.probability(MINING, WALKING, BUILDING) > 0.0D,
                "An unobserved transition became impossible");
        require(table.probability(FARMING, FARMING, BUILDING) > 0.0D,
                "An unvisited context became impossible");
    }

    private static void repeatedRoutineBecomesThePrediction() {
        ActivityTransitionTable table =
                new ActivityTransitionTable(ACTIVITIES);
        double before = table.probability(MINING, WALKING, SMELTING);
        for (int repeat = 0; repeat < 20; repeat++) {
            table.observe(MINING, WALKING, SMELTING);
        }
        double after = table.probability(MINING, WALKING, SMELTING);
        require(after > before,
                "Repetition did not raise the predicted probability");
        require(after > 0.8D,
                "Twenty consistent observations only reached " + after);
        require(table.mostLikelyNext(MINING, WALKING) == SMELTING,
                "The learned routine is not the most likely successor");
    }

    /**
     * The whole reason for a second-order table. A day histogram sees only that
     * walking is often followed by smelting and often by farming; it cannot see
     * that which one depends on what preceded the walking.
     */
    private static void theSameSuccessorDependsOnWhatCameBefore() {
        ActivityTransitionTable table =
                new ActivityTransitionTable(ACTIVITIES);
        for (int repeat = 0; repeat < 20; repeat++) {
            table.observe(MINING, WALKING, SMELTING);
            table.observe(FARMING, WALKING, BUILDING);
        }
        require(table.mostLikelyNext(MINING, WALKING) == SMELTING,
                "Mining then walking should predict smelting");
        require(table.mostLikelyNext(FARMING, WALKING) == BUILDING,
                "Farming then walking should predict building");
        require(table.probability(MINING, WALKING, SMELTING)
                        > table.probability(FARMING, WALKING, SMELTING) + 0.5D,
                "The two contexts were not told apart");
    }

    private static void confidenceGrowsWithContextEvidence() {
        ActivityForecast forecast = new ActivityForecast(ACTIVITIES);
        require(forecast.confidence(MINING, WALKING) < EPSILON,
                "An empty context started out confident");
        for (int repeat = 0; repeat < 8; repeat++) {
            forecast.observe(MINING, WALKING, SMELTING, 6);
        }
        double half = forecast.confidence(MINING, WALKING);
        require(Math.abs(half - 0.5D) < EPSILON,
                "Eight observations should be the half-way point, got " + half);
        for (int repeat = 0; repeat < 200; repeat++) {
            forecast.observe(MINING, WALKING, SMELTING, 6);
        }
        require(forecast.confidence(MINING, WALKING) > 0.95D,
                "Confidence did not approach one with heavy evidence");
        require(forecast.confidence(FARMING, FARMING) < EPSILON,
                "Evidence for one context leaked into another");
    }

    /**
     * With nothing learned the forecast must be the hour; with a context worn
     * deep it must be the sequence. Anything in between is the blend doing its
     * job rather than a fixed compromise.
     */
    private static void blendFollowsConfidence() {
        ActivityForecast forecast = new ActivityForecast(ACTIVITIES);
        // Build a time-of-day prior without touching the queried context.
        for (int repeat = 0; repeat < 60; repeat++) {
            forecast.observe(FARMING, FARMING, BUILDING, 9);
        }
        double prior = forecast.prior(BUILDING, 9);
        double cold = forecast.probability(MINING, WALKING, BUILDING, 9);
        require(Math.abs(cold - prior) < EPSILON,
                "An unlearned context did not fall back to the hour");

        for (int repeat = 0; repeat < 300; repeat++) {
            forecast.observe(MINING, WALKING, SMELTING, 9);
        }
        double warm = forecast.probability(MINING, WALKING, SMELTING, 9);
        double sequence = forecast.transitions()
                .probability(MINING, WALKING, SMELTING);
        require(Math.abs(warm - sequence) < 0.05D,
                "A well-learned context did not follow the sequence model");
        require(forecast.mostLikelyNext(MINING, WALKING, 9) == SMELTING,
                "The blended forecast ignored a strong routine");
    }

    private static void blendedForecastStillSumsToOne() {
        ActivityForecast forecast = new ActivityForecast(ACTIVITIES);
        for (int repeat = 0; repeat < 30; repeat++) {
            forecast.observe(MINING, WALKING, SMELTING, 3);
            forecast.observe(MINING, WALKING, FARMING, 14);
            forecast.observe(WALKING, MINING, WALKING, 3);
        }
        for (int bucket : new int[]{0, 3, 14, 23}) {
            double total = 0.0D;
            for (int next = 0; next < ACTIVITIES; next++) {
                total += forecast.probability(MINING, WALKING, next, bucket);
            }
            require(Math.abs(total - 1.0D) < 1.0E-9D,
                    "Blended forecast for bucket " + bucket
                            + " sums to " + total);
        }
    }

    /**
     * Forgetting exists so the table follows an owner who changes habits. It
     * has to bound the evidence without inverting what was learned.
     */
    private static void forgettingCapsEvidenceAndKeepsShape() {
        ActivityTransitionTable table =
                new ActivityTransitionTable(ACTIVITIES);
        for (int repeat = 0; repeat < 4_000; repeat++) {
            table.observe(MINING, WALKING, SMELTING);
            if (repeat % 4 == 0) {
                table.observe(MINING, WALKING, FARMING);
            }
        }
        require(table.observations() <= 1_000,
                "Evidence grew past the cap: " + table.observations());
        require(table.mostLikelyNext(MINING, WALKING) == SMELTING,
                "Forgetting inverted the learned preference");
        require(table.probability(MINING, WALKING, SMELTING)
                        > table.probability(MINING, WALKING, FARMING),
                "Forgetting lost the four-to-one ratio");

        // A changed routine must eventually win, which is the point of decaying
        // rather than simply capping.
        for (int repeat = 0; repeat < 4_000; repeat++) {
            table.observe(MINING, WALKING, BUILDING);
        }
        require(table.mostLikelyNext(MINING, WALKING) == BUILDING,
                "The table never adapted to a changed routine");
    }

    private static void identicalHistoriesProduceIdenticalForecasts() {
        ActivityForecast left = new ActivityForecast(ACTIVITIES);
        ActivityForecast right = new ActivityForecast(ACTIVITIES);
        for (int step = 0; step < 500; step++) {
            int previous = step % ACTIVITIES;
            int current = (step * 7) % ACTIVITIES;
            int next = (step * 13) % ACTIVITIES;
            int bucket = step % ActivityForecast.BUCKETS;
            left.observe(previous, current, next, bucket);
            right.observe(previous, current, next, bucket);
        }
        for (int previous = 0; previous < ACTIVITIES; previous++) {
            for (int current = 0; current < ACTIVITIES; current++) {
                for (int next = 0; next < ACTIVITIES; next++) {
                    double a = left.probability(previous, current, next, 5);
                    double b = right.probability(previous, current, next, 5);
                    require(a == b,
                            "Identical histories diverged at " + previous
                                    + "," + current + "," + next);
                }
            }
        }
    }

    private static void outOfRangeActivitiesAreRejected() {
        ActivityTransitionTable table =
                new ActivityTransitionTable(ACTIVITIES);
        expectFailure(() -> table.observe(-1, 0, 0), "negative activity");
        expectFailure(() -> table.observe(0, ACTIVITIES, 0), "activity above range");
        expectFailure(() -> table.probability(0, 0, ACTIVITIES), "query above range");
        ActivityForecast forecast = new ActivityForecast(ACTIVITIES);
        expectFailure(() -> forecast.observe(0, 0, 0, 24), "day bucket above range");
        expectFailure(() -> forecast.observe(0, 0, 0, -1), "negative day bucket");
        expectFailure(() -> new ActivityTransitionTable(1), "degenerate alphabet");
        expectFailure(
                () -> new ActivityTransitionTable(
                        ActivityTransitionTable.MAX_ACTIVITIES + 1
                ),
                "oversized alphabet"
        );
    }

    private static void expectFailure(Runnable operation, String what) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Accepted " + what);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
