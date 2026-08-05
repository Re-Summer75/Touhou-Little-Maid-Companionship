package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.application.forecast.OwnerActivityTracker;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.domain.forecast.CompanionActivity;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentVocabulary;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.fact.FactType;

import java.util.UUID;

/**
 * The forecast as data packs actually meet it: sampled from repeated calls,
 * published as facts, and safe to read before anything has been learned.
 */
public final class OwnerActivityTrackerVerification {
    private static final UUID OWNER = UUID.nameUUIDFromBytes(
            "owner".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    );
    private static final UUID OTHER = UUID.nameUUIDFromBytes(
            "other".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    );

    private OwnerActivityTrackerVerification() {
    }

    public static void main(String[] args) {
        onlyChangesAreRecorded();
        samplingRateDoesNotChangeWhatIsLearned();
        unknownOwnerReadsAsFlatPrior();
        routineIsPredictedAfterRepetition();
        ownersDoNotShareHistory();
        everyActivityIsRegisteredAsANumberFact();
        dayBucketsCoverTheWholeDay();
    }

    /**
     * A transition is a change. Recording every sample would turn "mined for
     * ten minutes" into hundreds of self-transitions and drown the real ones.
     */
    private static void onlyChangesAreRecorded() {
        OwnerActivityTracker tracker = new OwnerActivityTracker();
        require(tracker.observe(OWNER, CompanionActivity.MINING, 0L),
                "First change was not recorded");
        for (int repeat = 0; repeat < 100; repeat++) {
            require(!tracker.observe(OWNER, CompanionActivity.MINING, repeat),
                    "An unchanged sample was recorded as a transition");
        }
        require(tracker.observe(OWNER, CompanionActivity.TRAVELLING, 0L),
                "A genuine change after a run of samples was missed");
        require(tracker.current(OWNER) == CompanionActivity.TRAVELLING,
                "The tracker did not advance to the new activity");
    }

    /**
     * Five maids sampling the same owner must not learn five times as fast as
     * one, and a slow sampler must reach the same table as a fast one.
     */
    private static void samplingRateDoesNotChangeWhatIsLearned() {
        CompanionActivity[] script = {
                CompanionActivity.MINING,
                CompanionActivity.TRAVELLING,
                CompanionActivity.BUILDING,
                CompanionActivity.IDLE
        };
        OwnerActivityTracker sparse = new OwnerActivityTracker();
        OwnerActivityTracker dense = new OwnerActivityTracker();
        long time = 0L;
        for (int round = 0; round < 30; round++) {
            for (CompanionActivity activity : script) {
                sparse.observe(OWNER, activity, time);
                // The same change seen five times, as five maids would.
                for (int maid = 0; maid < 5; maid++) {
                    dense.observe(OWNER, activity, time);
                }
                time += 40L;
            }
        }
        for (CompanionActivity activity : CompanionActivity.values()) {
            double left = sparse.probability(OWNER, activity, time);
            double right = dense.probability(OWNER, activity, time);
            require(Math.abs(left - right) < 1.0E-9D,
                    "Sampling rate changed the forecast for " + activity
                            + ": " + left + " vs " + right);
        }
    }

    private static void unknownOwnerReadsAsFlatPrior() {
        OwnerActivityTracker tracker = new OwnerActivityTracker();
        double expected = 1.0D / CompanionActivity.count();
        for (CompanionActivity activity : CompanionActivity.values()) {
            double value = tracker.probability(OWNER, activity, 0L);
            require(Math.abs(value - expected) < 1.0E-9D,
                    "An unseen owner did not read as a flat prior");
            require(value > 0.0D,
                    "An unseen owner produced a vetoing zero");
        }
        require(tracker.confidence(OWNER) == 0.0D,
                "An unseen owner reported confidence");
    }

    private static void routineIsPredictedAfterRepetition() {
        OwnerActivityTracker tracker = new OwnerActivityTracker();
        long time = 0L;
        for (int round = 0; round < 40; round++) {
            tracker.observe(OWNER, CompanionActivity.MINING, time);
            tracker.observe(OWNER, CompanionActivity.TRAVELLING, time + 20L);
            tracker.observe(OWNER, CompanionActivity.BUILDING, time + 40L);
            time += 60L;
        }
        // Sitting at MINING -> TRAVELLING, building is what came next.
        tracker.observe(OWNER, CompanionActivity.MINING, time);
        tracker.observe(OWNER, CompanionActivity.TRAVELLING, time + 20L);
        double building = tracker.probability(
                OWNER,
                CompanionActivity.BUILDING,
                time + 20L
        );
        double farming = tracker.probability(
                OWNER,
                CompanionActivity.FARMING,
                time + 20L
        );
        require(building > farming,
                "The learned routine did not outrank an unseen activity");
        require(building > 0.5D,
                "Forty repetitions only reached " + building);
        require(tracker.confidence(OWNER) > 0.8D,
                "Confidence stayed low after heavy evidence");
    }

    private static void ownersDoNotShareHistory() {
        OwnerActivityTracker tracker = new OwnerActivityTracker();
        long time = 0L;
        for (int round = 0; round < 40; round++) {
            tracker.observe(OWNER, CompanionActivity.MINING, time);
            tracker.observe(OWNER, CompanionActivity.BUILDING, time + 20L);
            time += 40L;
        }
        require(tracker.confidence(OTHER) == 0.0D,
                "One owner's evidence leaked into another");
        require(tracker.trackedOwners() == 1,
                "Reading an unknown owner created state for it");
        tracker.forget(OWNER);
        require(tracker.trackedOwners() == 0, "Forget did not drop the owner");
    }

    /**
     * A fact the vocabulary does not know cannot be referenced from a data
     * pack, so registration is part of the feature rather than a detail of it.
     */
    private static void everyActivityIsRegisteredAsANumberFact() {
        IntentVocabulary vocabulary = CompanionIntentIds.vocabulary();
        for (CompanionActivity activity : CompanionActivity.values()) {
            OrchestrationId fact = activity.forecastFact();
            require(vocabulary.facts().contains(fact),
                    "Forecast fact " + fact + " is not in the vocabulary");
            require(vocabulary.factTypes().get(fact) == FactType.NUMBER,
                    "Forecast fact " + fact + " is not typed as a number");
        }
    }

    /**
     * Game time is a long that wraps around the day and can be negative in a
     * freshly created world, so bucketing it must not throw or fall outside the
     * histogram.
     */
    private static void dayBucketsCoverTheWholeDay() {
        OwnerActivityTracker tracker = new OwnerActivityTracker();
        long[] times = {
                -24_001L, -500L, -1L, 0L, 1L,
                11_999L, 12_000L, 23_999L, 24_000L, 1_234_567L
        };
        int activity = 0;
        for (long time : times) {
            tracker.observe(
                    OWNER,
                    CompanionActivity.byIndex(
                            activity++ % CompanionActivity.count()
                    ),
                    time
            );
            for (CompanionActivity candidate : CompanionActivity.values()) {
                double value =
                        tracker.probability(OWNER, candidate, time);
                require(value > 0.0D && value < 1.0D,
                        "Forecast left (0, 1) at game time " + time
                                + ": " + value);
            }
        }
        // Two times one whole day apart must land in the same bucket, which is
        // what makes a habit a habit rather than a one-off.
        double now = tracker.probability(OWNER, CompanionActivity.IDLE, 6_000L);
        double tomorrow =
                tracker.probability(OWNER, CompanionActivity.IDLE, 30_000L);
        require(Math.abs(now - tomorrow) < 1.0E-9D,
                "The same hour on two days read differently");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
