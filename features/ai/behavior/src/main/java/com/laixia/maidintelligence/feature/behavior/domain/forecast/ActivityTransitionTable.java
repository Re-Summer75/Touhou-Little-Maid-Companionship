package com.laixia.maidintelligence.feature.behavior.domain.forecast;

/**
 * Second-order transition counts over owner activities:
 * {@code P(next | previous, current)}.
 *
 * <p>{@code HabitForecast} already answers "what does this owner usually do at
 * this hour". It cannot answer "what does this owner usually do <em>next</em>",
 * because a histogram over the day has no notion of order — mining at dusk and
 * smelting at dusk are two independent tallies in it, never a sequence. This
 * table is the missing axis.
 *
 * <p>Second order rather than first because the useful patterns are two steps
 * long. "Walking" follows everything and predicts nothing; "mining then
 * walking" predicts smelting. Third order was not attempted: the table grows
 * with the cube already, and evidence per context thins out faster than the
 * extra context earns back.
 *
 * <p>Storage is a fixed {@code short} array sized at construction, so observing
 * and querying allocate nothing and the whole structure is a few kilobytes per
 * owner. Every count is an integer and every query is arithmetic over those
 * counts, so two clients holding the same observations compute the same
 * prediction.
 */
public final class ActivityTransitionTable {
    /**
     * Bound on the activity alphabet. Sixteen keeps the cube at 4096 shorts
     * (8 KiB) which is small enough to hold per owner without thought; it is
     * also well past the number of activities that can be told apart from the
     * outside.
     */
    public static final int MAX_ACTIVITIES = 16;

    /**
     * Total observations before every count is halved. Halving is what makes
     * the table follow an owner whose habits change instead of averaging their
     * whole history: recent evidence keeps full weight while old evidence
     * decays geometrically. It matches how {@code AffordanceReliability} and
     * {@code HabitForecast} already forget.
     */
    private static final int MAX_EVIDENCE = 1_000;

    private final int activityCount;
    private final short[] counts;
    private final int[] contextTotals;
    private int observations;

    public ActivityTransitionTable(int activityCount) {
        if (activityCount < 2 || activityCount > MAX_ACTIVITIES) {
            throw new IllegalArgumentException(
                    "Activity count must be in [2, " + MAX_ACTIVITIES + "]"
            );
        }
        this.activityCount = activityCount;
        this.counts = new short[activityCount * activityCount * activityCount];
        this.contextTotals = new int[activityCount * activityCount];
    }

    public int activityCount() {
        return activityCount;
    }

    public int observations() {
        return observations;
    }

    /**
     * Evidence behind one context, which is what decides how far the fused
     * forecast is allowed to trust this table over a time-of-day prior.
     */
    public int contextObservations(int previous, int current) {
        requireActivity(previous);
        requireActivity(current);
        return contextTotals[context(previous, current)];
    }

    /**
     * Raw count, before any smoothing. Exposed for
     * {@link ActivityForecast#lift}, which has to smooth towards the marginal
     * distribution rather than towards uniform and therefore cannot reuse
     * {@link #probability}.
     */
    public int count(int previous, int current, int next) {
        requireActivity(previous);
        requireActivity(current);
        requireActivity(next);
        return counts[index(previous, current, next)];
    }

    public void observe(int previous, int current, int next) {
        requireActivity(previous);
        requireActivity(current);
        requireActivity(next);
        if (observations >= MAX_EVIDENCE) {
            halve();
        }
        counts[index(previous, current, next)]++;
        contextTotals[context(previous, current)]++;
        observations++;
    }

    /**
     * Laplace-smoothed probability, so an unseen transition is unlikely rather
     * than impossible.
     *
     * <p>Without the smoothing the first observation of a context would report
     * every other outcome at exactly zero, and a zero here becomes a veto once
     * it reaches a multiplicative consideration — one coincidence would
     * permanently rule out a behaviour.
     */
    public double probability(int previous, int current, int next) {
        requireActivity(previous);
        requireActivity(current);
        requireActivity(next);
        int total = contextTotals[context(previous, current)];
        return (counts[index(previous, current, next)] + 1.0D)
                / (total + activityCount);
    }

    /**
     * Most likely successor, ties broken by the lower activity index so two
     * clients with identical evidence agree.
     */
    public int mostLikelyNext(int previous, int current) {
        requireActivity(previous);
        requireActivity(current);
        int best = 0;
        short bestCount = -1;
        int base = context(previous, current) * activityCount;
        for (int next = 0; next < activityCount; next++) {
            short count = counts[base + next];
            if (count > bestCount) {
                bestCount = count;
                best = next;
            }
        }
        return best;
    }

    public void reset() {
        java.util.Arrays.fill(counts, (short) 0);
        java.util.Arrays.fill(contextTotals, 0);
        observations = 0;
    }

    private void halve() {
        java.util.Arrays.fill(contextTotals, 0);
        observations = 0;
        for (int index = 0; index < counts.length; index++) {
            short decayed = (short) (counts[index] / 2);
            counts[index] = decayed;
            contextTotals[index / activityCount] += decayed;
            observations += decayed;
        }
    }

    private int index(int previous, int current, int next) {
        return context(previous, current) * activityCount + next;
    }

    private int context(int previous, int current) {
        return previous * activityCount + current;
    }

    private void requireActivity(int activity) {
        if (activity < 0 || activity >= activityCount) {
            throw new IllegalArgumentException(
                    "Activity " + activity + " is outside [0, "
                            + activityCount + ")"
            );
        }
    }
}
