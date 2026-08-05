package com.laixia.maidintelligence.feature.behavior.domain.forecast;

/**
 * Predicts the owner's next activity by blending what usually follows the last
 * two with what usually happens at this hour.
 *
 * <p>Neither source is sufficient alone. The sequence model is sharp but starts
 * empty and stays empty for contexts the owner rarely enters. The time-of-day
 * prior is available immediately but cannot distinguish "came back from mining"
 * from "just woke up" when both happen at dusk. So the blend is weighted by how
 * much sequence evidence exists <em>for the context being asked about</em>,
 * not by total evidence: a well-worn routine is predicted from the routine,
 * while an unfamiliar one falls back to the hour without being dragged by
 * confident predictions made elsewhere.
 *
 * <p>The output is a probability in {@code [0, 1]}, which is what makes it
 * publishable as a {@code fact/forecast/*} and usable by a data pack
 * consideration with no further scaling.
 *
 * <p>Everything here is counting and arithmetic — no model, no training step,
 * no inference cost. The whole per-owner state is a few kilobytes and survives
 * a save as plain integers.
 */
public final class ActivityForecast {
    /**
     * Day buckets, matching the existing habit histogram so the two describe
     * time the same way.
     */
    public static final int BUCKETS = 24;

    /**
     * Context observations at which the sequence model is trusted half way. Low
     * enough that a routine repeated a handful of times starts to show, high
     * enough that a single coincidence cannot override the hour.
     */
    private static final double CONFIDENCE_HALF_LIFE = 8.0D;

    /**
     * Marginal evidence before the per-activity counts are halved. Independent
     * of the other two decays because it answers a different question — how
     * often this activity happens at all — and tying it to either would make a
     * base rate jump for reasons unrelated to the base rate.
     */
    private static final int MAX_MARGINAL_EVIDENCE = 4_000;

    /**
     * Pseudo-observations of the owner's own base rate mixed into every
     * context before lift is computed. Eight matches
     * {@link #CONFIDENCE_HALF_LIFE}, so a context reaches half its final lift
     * at the same point it starts being trusted for the raw forecast.
     */
    private static final double SMOOTHING_STRENGTH = 8.0D;

    private final ActivityTransitionTable transitions;
    private final short[] bucketCounts;
    private final int[] bucketTotals;
    private final int[] activityTotals;
    private int marginalObservations;

    public ActivityForecast(int activityCount) {
        this.transitions = new ActivityTransitionTable(activityCount);
        this.bucketCounts = new short[BUCKETS * activityCount];
        this.bucketTotals = new int[BUCKETS];
        this.activityTotals = new int[activityCount];
    }

    public ActivityTransitionTable transitions() {
        return transitions;
    }

    public void observe(
            int previous,
            int current,
            int next,
            int dayBucket
    ) {
        requireBucket(dayBucket);
        transitions.observe(previous, current, next);
        int index = dayBucket * transitions.activityCount() + next;
        if (bucketTotals[dayBucket] >= Short.MAX_VALUE / 2) {
            halveBucket(dayBucket);
        }
        bucketCounts[index]++;
        bucketTotals[dayBucket]++;
        if (marginalObservations >= MAX_MARGINAL_EVIDENCE) {
            halveMarginals();
        }
        activityTotals[next]++;
        marginalObservations++;
    }

    /**
     * How often this activity happens at all, ignoring context and time.
     *
     * <p>The denominator for {@link #lift(int, int, int, int)}. Laplace
     * smoothed like everything else, so a never-seen activity has a small base
     * rate rather than a zero one — dividing by zero would report infinite
     * surprise for something that has simply never been observed.
     */
    public double baseRate(int activity) {
        return (activityTotals[activity] + 1.0D)
                / (marginalObservations + activityTotals.length);
    }

    /**
     * How much more likely this activity is here than it is in general,
     * squashed into {@code [0, 1)}.
     *
     * <p>A raw probability is not comparable across activities: travelling is
     * close to half of all transitions, so {@code P = 0.4} means "less likely
     * than usual" for travelling and "extremely likely" for resting. A data
     * pack author reading a raw forecast has no way to know which, and a
     * threshold written against one activity means something different against
     * the next.
     *
     * <p>This reports the ratio to the activity's own base rate instead, so
     * {@code 0.5} means "exactly as likely as usual" for every activity, above
     * means unusually likely, below means unusually unlikely. Squashing keeps
     * it inside the unit interval, where product aggregation and the response
     * curves already live.
     */
    public double lift(int previous, int current, int next) {
        double base = baseRate(next);
        if (base <= 0.0D) {
            return 0.5D;
        }
        /*
         * Smoothed towards the marginal, not towards uniform. Reusing
         * `probability` here read badly wrong for anything never observed: its
         * Laplace floor is one over the context's total, while the base rate's
         * floor is one over every observation ever made, and dividing the two
         * reported a never-seen successor as three times likelier than usual
         * purely because the denominators differ.
         *
         * <p>Smoothing towards `base` makes the two agree by construction. No
         * evidence for a context gives exactly the base rate, so lift is 0.5
         * and says nothing; evidence that the successor never follows pulls it
         * below; evidence that it always does pulls it above.
         */
        int contextTotal =
                transitions.contextObservations(previous, current);
        double smoothed = (transitions.count(previous, current, next)
                + SMOOTHING_STRENGTH * base)
                / (contextTotal + SMOOTHING_STRENGTH);
        double ratio = smoothed / base;
        return ratio / (ratio + 1.0D);
    }

    /**
     * Time-of-day probability alone, Laplace smoothed exactly as the sequence
     * model is so the two are on the same footing before they are mixed.
     */
    public double prior(int activity, int dayBucket) {
        requireBucket(dayBucket);
        int activityCount = transitions.activityCount();
        int index = dayBucket * activityCount + activity;
        return (bucketCounts[index] + 1.0D)
                / (bucketTotals[dayBucket] + activityCount);
    }

    /**
     * Weight given to the sequence model for this context, in {@code [0, 1)}.
     *
     * <p>Exposed because it is the honest part of an explanation: a forecast
     * reported without saying how much of it came from evidence is not much
     * better than a guess presented confidently.
     */
    public double confidence(int previous, int current) {
        int evidence = transitions.contextObservations(previous, current);
        return evidence / (evidence + CONFIDENCE_HALF_LIFE);
    }

    public double probability(
            int previous,
            int current,
            int next,
            int dayBucket
    ) {
        double weight = confidence(previous, current);
        return weight * transitions.probability(previous, current, next)
                + (1.0D - weight) * prior(next, dayBucket);
    }

    /**
     * Most likely successor under the blended forecast, ties broken by the
     * lower activity index so two clients agree.
     */
    public int mostLikelyNext(int previous, int current, int dayBucket) {
        int best = 0;
        double bestProbability = -1.0D;
        for (int next = 0; next < transitions.activityCount(); next++) {
            double candidate =
                    probability(previous, current, next, dayBucket);
            if (candidate > bestProbability) {
                bestProbability = candidate;
                best = next;
            }
        }
        return best;
    }

    public void reset() {
        transitions.reset();
        java.util.Arrays.fill(bucketCounts, (short) 0);
        java.util.Arrays.fill(bucketTotals, 0);
        java.util.Arrays.fill(activityTotals, 0);
        marginalObservations = 0;
    }

    private void halveMarginals() {
        marginalObservations = 0;
        for (int index = 0; index < activityTotals.length; index++) {
            activityTotals[index] /= 2;
            marginalObservations += activityTotals[index];
        }
    }

    /**
     * Buckets forget independently. A bucket the owner is awake for fills far
     * faster than one they sleep through, and halving the whole histogram
     * whenever the busiest bucket filled would keep discarding the little
     * evidence the quiet ones had managed to collect.
     */
    private void halveBucket(int dayBucket) {
        int activityCount = transitions.activityCount();
        int base = dayBucket * activityCount;
        int total = 0;
        for (int offset = 0; offset < activityCount; offset++) {
            short decayed = (short) (bucketCounts[base + offset] / 2);
            bucketCounts[base + offset] = decayed;
            total += decayed;
        }
        bucketTotals[dayBucket] = total;
    }

    private static void requireBucket(int dayBucket) {
        if (dayBucket < 0 || dayBucket >= BUCKETS) {
            throw new IllegalArgumentException(
                    "Day bucket must be in [0, " + BUCKETS + ")"
            );
        }
    }
}
