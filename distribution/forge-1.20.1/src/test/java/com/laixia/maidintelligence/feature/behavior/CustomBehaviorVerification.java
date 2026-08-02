package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.domain.ContinuousLookTracker;
import com.laixia.maidintelligence.feature.behavior.domain.GazeRecallPolicy;

public final class CustomBehaviorVerification {
    private CustomBehaviorVerification() {
    }

    public static void main(String[] args) {
        verifiesContinuousLookThreshold();
        verifiesTargetSwitchAndRearm();
        verifiesEngineAndSensorTuning();
        verifiesLegacyGazeTimingMigration();
    }

    private static void verifiesContinuousLookThreshold() {
        ContinuousLookTracker tracker = new ContinuousLookTracker();
        require(
                !tracker.observe(17, 2),
                "Gaze recall fired before two continuous ticks"
        );
        require(
                tracker.observe(17, 2),
                "Gaze recall did not fire at 0.1 seconds"
        );
        require(
                !tracker.observe(17, 2) && tracker.triggered(),
                "Held gaze retriggered without looking away"
        );
    }

    private static void verifiesTargetSwitchAndRearm() {
        ContinuousLookTracker tracker = new ContinuousLookTracker();
        for (int tick = 0; tick < 10; tick++) {
            tracker.observe(1, 20);
        }
        tracker.observe(2, 20);
        require(
                tracker.targetId() == 2 && tracker.heldTicks() == 1,
                "Switching maids retained the previous hold duration"
        );
        tracker.observe(-1, 20);
        require(
                tracker.targetId() == Integer.MIN_VALUE
                        && tracker.heldTicks() == 0
                        && !tracker.triggered(),
                "Looking away did not rearm the gesture"
        );
    }

    private static void verifiesEngineAndSensorTuning() {
        BehaviorTuning defaults = BehaviorTuning.defaults();
        require(
                defaults.enabled()
                        && defaults.evaluationIntervalTicks() == 5
                        && defaults.maxCandidateEvaluations() == 64
                        && defaults.diagnosticsEnabled()
                        && defaults.gazeRecallHoldTicks() == 2,
                "Behavior engine safety defaults changed"
        );
    }

    private static void verifiesLegacyGazeTimingMigration() {
        require(
                GazeRecallPolicy.migrateHoldTicks(20, 1) == 2,
                "Legacy one-second gaze default was not migrated"
        );
        require(
                GazeRecallPolicy.migrateHoldTicks(12, 1) == 12,
                "Custom legacy gaze timing was overwritten"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
