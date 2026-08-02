package com.laixia.maidintelligence.feature.behavior.api;

import com.laixia.maidintelligence.feature.behavior.domain.GazeRecallPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.HungryOwnerRequestPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerReturnPolicy;

import java.util.Objects;

/**
 * Immutable server tuning shared by original companionship behaviors.
 */
public record BehaviorTuning(
        boolean enabled,
        int gazeRecallHoldTicks,
        double gazeRecallRange,
        HungryRequest hungryRequest,
        OwnerReturn ownerReturn
) {
    public BehaviorTuning {
        requireRange(gazeRecallHoldTicks, 1, 200, "gazeRecallHoldTicks");
        requireFiniteRange(
                gazeRecallRange,
                2.0D,
                32.0D,
                "gazeRecallRange"
        );
        Objects.requireNonNull(hungryRequest, "hungryRequest");
        Objects.requireNonNull(ownerReturn, "ownerReturn");
    }

    public static BehaviorTuning defaults() {
        return new BehaviorTuning(
                true,
                GazeRecallPolicy.DEFAULT_HOLD_TICKS,
                8.0D,
                new HungryRequest(
                        true,
                        HungryOwnerRequestPolicy
                                .DEFAULT_CHECK_INTERVAL_TICKS,
                        new RequestBranch(
                                HungryOwnerRequestPolicy
                                        .DEFAULT_STANDARD_HUNGER_THRESHOLD,
                                HungryOwnerRequestPolicy
                                        .DEFAULT_STANDARD_MINIMUM_FAVORABILITY_LEVEL,
                                HungryOwnerRequestPolicy
                                        .DEFAULT_STANDARD_REQUEST_CHANCE
                        ),
                        new RequestBranch(
                                HungryOwnerRequestPolicy
                                        .DEFAULT_HIGH_TRUST_HUNGER_THRESHOLD,
                                HungryOwnerRequestPolicy
                                        .DEFAULT_HIGH_TRUST_MINIMUM_FAVORABILITY_LEVEL,
                                HungryOwnerRequestPolicy
                                        .DEFAULT_HIGH_TRUST_REQUEST_CHANCE
                        )
                ),
                new OwnerReturn(
                        true,
                        OwnerReturnPolicy.DEFAULT_POST_TASK_SETTLE_TICKS,
                        OwnerReturnPolicy.DEFAULT_POST_TASK_TIMEOUT_TICKS,
                        OwnerReturnPolicy.DEFAULT_POST_TASK_COOLDOWN_TICKS,
                        true,
                        OwnerReturnPolicy.DEFAULT_WANDER_RETURN_CHANCE,
                        OwnerReturnPolicy.DEFAULT_WANDER_COOLDOWN_TICKS,
                        OwnerReturnPolicy.DEFAULT_CLOSE_ENOUGH_DISTANCE
                )
        );
    }

    public record HungryRequest(
            boolean enabled,
            int checkIntervalTicks,
            RequestBranch standard,
            RequestBranch highTrust
    ) {
        public HungryRequest {
            requireRange(
                    checkIntervalTicks,
                    20,
                    1_200,
                    "checkIntervalTicks"
            );
            Objects.requireNonNull(standard, "standard");
            Objects.requireNonNull(highTrust, "highTrust");
        }

        public double chance(
                HungryOwnerRequestPolicy.RequestTier tier
        ) {
            return switch (tier) {
                case STANDARD -> standard.chance();
                case HIGH_TRUST -> highTrust.chance();
                case NONE -> 0.0D;
            };
        }
    }

    public record OwnerReturn(
            boolean postTaskEnabled,
            int postTaskSettleTicks,
            int postTaskTimeoutTicks,
            int postTaskCooldownTicks,
            boolean wanderEnabled,
            double wanderChance,
            int wanderCooldownTicks,
            int closeEnoughDistance
    ) {
        public OwnerReturn {
            requireRange(
                    postTaskSettleTicks,
                    0,
                    200,
                    "postTaskSettleTicks"
            );
            requireRange(
                    postTaskTimeoutTicks,
                    20,
                    1_200,
                    "postTaskTimeoutTicks"
            );
            requireRange(
                    postTaskCooldownTicks,
                    0,
                    12_000,
                    "postTaskCooldownTicks"
            );
            requireFiniteRange(wanderChance, 0.0D, 1.0D, "wanderChance");
            requireRange(
                    wanderCooldownTicks,
                    0,
                    12_000,
                    "wanderCooldownTicks"
            );
            requireRange(
                    closeEnoughDistance,
                    1,
                    8,
                    "closeEnoughDistance"
            );
        }
    }

    public record RequestBranch(
            int hungerThreshold,
            int minimumFavorabilityLevel,
            double chance
    ) {
        public RequestBranch {
            requireRange(hungerThreshold, 0, 100, "hungerThreshold");
            requireRange(
                    minimumFavorabilityLevel,
                    0,
                    3,
                    "minimumFavorabilityLevel"
            );
            requireFiniteRange(chance, 0.0D, 1.0D, "chance");
        }
    }

    private static void requireRange(
            int value,
            int minimum,
            int maximum,
            String name
    ) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be in [" + minimum + ", " + maximum + "]"
            );
        }
    }

    private static void requireFiniteRange(
            double value,
            double minimum,
            double maximum,
            String name
    ) {
        if (!Double.isFinite(value)
                || value < minimum
                || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be finite and in ["
                            + minimum + ", " + maximum + "]"
            );
        }
    }
}
