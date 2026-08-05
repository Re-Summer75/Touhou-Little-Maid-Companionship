package com.laixia.maidintelligence.feature.behavior.application.forecast;

import com.laixia.maidintelligence.feature.behavior.domain.forecast.ActivityForecast;
import com.laixia.maidintelligence.feature.behavior.domain.forecast.CompanionActivity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Keeps one {@link ActivityForecast} per owner and turns a stream of samples
 * into transitions.
 *
 * <p>A transition is recorded only when the sampled activity <em>differs</em>
 * from the current one. That is what a Markov chain over "what happens next"
 * actually wants, and it also decouples the statistics from how often anything
 * happens to sample: mining for six hundred ticks is one observation either
 * way, and five maids sharing an owner do not record the same change five
 * times.
 *
 * <p>Every method is main-thread only, like the rest of the companion services.
 * Nothing here allocates per sample once an owner is known.
 */
public final class OwnerActivityTracker {
    /**
     * Owners retained before the least recently sampled is dropped. Generous
     * for a single-player world and still bounded on a busy server; a dropped
     * owner loses its history and starts relearning, which is the mild failure
     * mode of the two available.
     */
    private static final int MAX_OWNERS = 64;

    private static final long DAY_LENGTH = 24_000L;

    private final Map<UUID, OwnerState> owners =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<UUID, OwnerState> eldest
                ) {
                    return size() > MAX_OWNERS;
                }
            };

    /**
     * Records where the owner is now, and a transition if that is somewhere
     * new.
     *
     * @return true when a transition was recorded
     */
    public boolean observe(
            UUID owner,
            CompanionActivity activity,
            long gameTime
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(activity, "activity");
        OwnerState state = owners.computeIfAbsent(
                owner,
                ignored -> new OwnerState()
        );
        return state.observe(activity, dayBucket(gameTime));
    }

    /**
     * Probability that the owner's next activity is {@code activity}.
     *
     * <p>An owner never seen before returns a flat prior rather than zero, so a
     * consideration reading this on a fresh world is uninformative instead of
     * being vetoed.
     */
    public double probability(
            UUID owner,
            CompanionActivity activity,
            long gameTime
    ) {
        OwnerState state = owners.get(owner);
        if (state == null) {
            return 1.0D / CompanionActivity.count();
        }
        return state.probability(activity, dayBucket(gameTime));
    }

    /**
     * How much more likely the activity is here than it is in general, in
     * {@code [0, 1)} with {@code 0.5} meaning "as likely as usual".
     *
     * <p>An owner never seen before reads exactly {@code 0.5}: nothing is
     * unusual yet, which is the honest answer and the one that leaves an
     * anticipation threshold un-triggered.
     */
    public double lift(UUID owner, CompanionActivity activity) {
        OwnerState state = owners.get(owner);
        return state == null ? 0.5D : state.lift(activity);
    }

    /**
     * How much of the answer above came from observed sequence rather than
     * from the time of day, in {@code [0, 1)}.
     */
    public double confidence(UUID owner) {
        OwnerState state = owners.get(owner);
        return state == null ? 0.0D : state.confidence();
    }

    /**
     * Transitions observed from the owner's current context.
     *
     * <p>Reported alongside a forecast so a display can say how much is behind
     * it. A prediction shown without its evidence reads as a fact, and this one
     * is not.
     */
    public int evidence(UUID owner) {
        OwnerState state = owners.get(owner);
        return state == null ? 0 : state.evidence();
    }

    public CompanionActivity current(UUID owner) {
        OwnerState state = owners.get(owner);
        return state == null ? CompanionActivity.IDLE : state.current;
    }

    public void forget(UUID owner) {
        owners.remove(owner);
    }

    public int trackedOwners() {
        return owners.size();
    }

    static int dayBucket(long gameTime) {
        long dayTime = Math.floorMod(gameTime, DAY_LENGTH);
        return (int) (dayTime * ActivityForecast.BUCKETS / DAY_LENGTH);
    }

    private static final class OwnerState {
        private final ActivityForecast forecast =
                new ActivityForecast(CompanionActivity.count());
        private CompanionActivity previous = CompanionActivity.IDLE;
        private CompanionActivity current = CompanionActivity.IDLE;

        private boolean observe(CompanionActivity activity, int dayBucket) {
            if (activity == current) {
                return false;
            }
            forecast.observe(
                    previous.ordinal(),
                    current.ordinal(),
                    activity.ordinal(),
                    dayBucket
            );
            previous = current;
            current = activity;
            return true;
        }

        private double probability(
                CompanionActivity activity,
                int dayBucket
        ) {
            return forecast.probability(
                    previous.ordinal(),
                    current.ordinal(),
                    activity.ordinal(),
                    dayBucket
            );
        }

        private double lift(CompanionActivity activity) {
            return forecast.lift(
                    previous.ordinal(),
                    current.ordinal(),
                    activity.ordinal()
            );
        }

        private double confidence() {
            return forecast.confidence(previous.ordinal(), current.ordinal());
        }

        private int evidence() {
            return forecast.transitions().contextObservations(
                    previous.ordinal(),
                    current.ordinal()
            );
        }
    }
}
