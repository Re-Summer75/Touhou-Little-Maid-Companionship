package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.ContinuousLookTracker;
import com.laixia.maidintelligence.feature.behavior.domain.GazeRecallPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.HungryOwnerRequestPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerReturnPolicy;

public final class CustomBehaviorVerification {
    private CustomBehaviorVerification() {
    }

    public static void main(String[] args) {
        verifiesContinuousLookThreshold();
        verifiesTargetSwitchAndRearm();
        verifiesGazeRecallEligibility();
        verifiesHungryOwnerRequestPolicy();
        verifiesOwnerReturnPolicy();
        System.out.println("Custom maid behavior verification passed.");
    }

    private static void verifiesContinuousLookThreshold() {
        ContinuousLookTracker tracker = new ContinuousLookTracker();
        for (int tick = 1; tick < 20; tick++) {
            require(
                    !tracker.observe(17, 20),
                    "Gaze recall fired before one second"
            );
        }
        require(
                tracker.observe(17, 20),
                "Gaze recall did not fire on the twentieth tick"
        );
        require(
                !tracker.observe(17, 20) && tracker.triggered(),
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

    private static void verifiesGazeRecallEligibility() {
        GazeRecallPolicy policy = GazeRecallPolicy.defaults();
        require(
                policy.eligible(1, true, true),
                "Favorability level one did not unlock gaze recall"
        );
        require(
                !policy.eligible(0, true, true),
                "Favorability level zero unlocked gaze recall"
        );
        require(
                !policy.eligible(1, false, true),
                "Home mode accepted a follow-only behavior"
        );
        require(
                !policy.eligible(1, true, false),
                "A hard movement state accepted gaze recall"
        );
    }

    private static void verifiesHungryOwnerRequestPolicy() {
        HungryOwnerRequestPolicy policy =
                HungryOwnerRequestPolicy.INSTANCE;
        HungryOwnerRequestPolicy.RequestTier standard = policy.selectTier(
                40,
                0,
                40,
                0,
                20,
                3
        );
        HungryOwnerRequestPolicy.RequestTier highTrust = policy.selectTier(
                20,
                3,
                40,
                0,
                20,
                3
        );
        require(
                standard
                        == HungryOwnerRequestPolicy.RequestTier.STANDARD,
                "Needs-food dialogue threshold did not select standard request"
        );
        require(
                highTrust
                        == HungryOwnerRequestPolicy.RequestTier.HIGH_TRUST,
                "Critical hunger and level three missed high-trust request"
        );
        require(
                policy.selectTier(20, 2, 40, 0, 20, 3)
                        == HungryOwnerRequestPolicy.RequestTier.STANDARD,
                "Low favorability incorrectly entered the high-trust branch"
        );
        require(
                policy.selectTier(41, 3, 40, 0, 20, 3)
                        == HungryOwnerRequestPolicy.RequestTier.NONE,
                "Maid requested food above the dialogue threshold"
        );
        require(
                policy.eligible(standard, true, true, false)
                        && !policy.eligible(
                                highTrust,
                                true,
                                true,
                                true
                        ),
                "Combat was interrupted by a food request"
        );
        require(
                policy.shouldEvaluate(99L, 1, 100),
                "Staggered food-request cadence did not trigger"
        );
        require(
                policy.chancePassed(0.09D, 0.10D)
                        && !policy.chancePassed(0.10D, 0.10D)
                        && policy.chancePassed(0.24D, 0.25D)
                        && !policy.chancePassed(0.25D, 0.25D),
                "Food-request probability boundary changed"
        );
    }

    private static void verifiesOwnerReturnPolicy() {
        OwnerReturnPolicy policy = OwnerReturnPolicy.INSTANCE;
        require(
                !policy.postTaskReady(100L, 119L, 20, 0L)
                        && policy.postTaskReady(100L, 120L, 20, 0L),
                "Post-task return settle boundary changed"
        );
        require(
                !policy.postTaskReady(100L, 120L, 20, 121L)
                        && policy.postTaskExpired(100L, 300L, 200),
                "Post-task cooldown or timeout boundary changed"
        );
        require(
                policy.chancePassed(0.09D, 0.10D)
                        && !policy.chancePassed(0.10D, 0.10D),
                "Wander owner-return probability boundary changed"
        );
        require(
                policy.eligible(true, true, false, false)
                        && !policy.eligible(true, true, true, false)
                        && !policy.eligible(true, true, false, true),
                "Owner-return conflict eligibility changed"
        );
        require(
                policy.closeEnough(4.0D, 2)
                        && !policy.closeEnough(4.01D, 2),
                "Owner-return arrival boundary changed"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
