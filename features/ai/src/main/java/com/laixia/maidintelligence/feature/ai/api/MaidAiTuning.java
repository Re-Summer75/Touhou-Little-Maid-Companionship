package com.laixia.maidintelligence.feature.ai.api;

import java.util.Objects;

/**
 * Immutable server tuning consumed by platform-neutral AI policies.
 */
public record MaidAiTuning(
        Performance performance,
        ActivityRadius activityRadius,
        CombatReaction combatReaction
) {
    public MaidAiTuning {
        Objects.requireNonNull(performance, "performance");
        Objects.requireNonNull(activityRadius, "activityRadius");
        Objects.requireNonNull(combatReaction, "combatReaction");
    }

    public static MaidAiTuning defaults() {
        return new MaidAiTuning(
                new Performance(true, 20, false),
                new ActivityRadius(true, 20, 3, 4, 8, 12, 24),
                new CombatReaction(true, 10, 20, 64, 200)
        );
    }

    public record Performance(
            boolean enabled,
            int reachablePathCacheTicks,
            boolean profilingEnabled
    ) {
        public Performance {
            requireRange(
                    reachablePathCacheTicks,
                    0,
                    100,
                    "reachablePathCacheTicks"
            );
        }
    }

    public record ActivityRadius(
            boolean enabled,
            int stationaryConfirmTicks,
            int movingConfirmTicks,
            int idleBonus,
            int workBonus,
            int combatBonus,
            int maximumRadius
    ) {
        public ActivityRadius {
            requireRange(
                    stationaryConfirmTicks,
                    1,
                    200,
                    "stationaryConfirmTicks"
            );
            requireRange(
                    movingConfirmTicks,
                    1,
                    40,
                    "movingConfirmTicks"
            );
            requireRange(idleBonus, 0, 32, "idleBonus");
            requireRange(workBonus, 0, 32, "workBonus");
            requireRange(combatBonus, 0, 32, "combatBonus");
            requireRange(maximumRadius, 3, 64, "maximumRadius");
        }
    }

    public record CombatReaction(
            boolean enabled,
            int stationaryScanInterval,
            int movingScanInterval,
            int candidateLimit,
            int recentThreatTicks
    ) {
        public CombatReaction {
            requireRange(
                    stationaryScanInterval,
                    2,
                    40,
                    "stationaryScanInterval"
            );
            requireRange(
                    movingScanInterval,
                    2,
                    80,
                    "movingScanInterval"
            );
            requireRange(candidateLimit, 8, 256, "candidateLimit");
            requireRange(recentThreatTicks, 20, 600, "recentThreatTicks");
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
}
