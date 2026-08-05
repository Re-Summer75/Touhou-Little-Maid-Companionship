package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.domain.GazeRecallPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.learning.LearningMode;
import com.laixia.maidintelligence.feature.orchestration.api.IntentRolloutMode;

public final class CustomBehaviorVerification {
    private CustomBehaviorVerification() {
    }

    public static void main(String[] args) {
        verifiesEngineAndSensorTuning();
        verifiesLegacyGazeTimingMigration();
    }

    private static void verifiesEngineAndSensorTuning() {
        BehaviorTuning defaults = BehaviorTuning.defaults();
        require(
                defaults.enabled()
                        && defaults.evaluationIntervalTicks() == 5
                        && defaults.maxCandidateEvaluations() == 64
                        && defaults.diagnosticsEnabled()
                        && defaults.rolloutMode()
                        == IntentRolloutMode.LIVE_ONLY
                        && defaults.learningMode() == LearningMode.SHADOW
                        && defaults.gazeRecallHoldTicks() == 6,
                "Behavior engine safety defaults changed"
        );
    }

    private static void verifiesLegacyGazeTimingMigration() {
        require(
                GazeRecallPolicy.migrateHoldTicks(20, 1) == 6,
                "Legacy one-second gaze default was not migrated"
        );
        require(
                GazeRecallPolicy.migrateHoldTicks(12, 1) == 12,
                "Custom legacy gaze timing was overwritten"
        );
        require(
                GazeRecallPolicy.migrateHoldTicks(2, 2) == 6,
                "The two-tick glance default was not migrated"
        );
        require(
                GazeRecallPolicy.migrateRange(8.0D, 3) == 16.0D,
                "Legacy single-room gaze range was not widened"
        );
        require(
                GazeRecallPolicy.migrateRange(24.0D, 3) == 24.0D,
                "A chosen gaze range was overwritten"
        );
        require(
                GazeRecallPolicy.migrateRange(8.0D, 4) == 8.0D,
                "An already-migrated range was migrated again"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
