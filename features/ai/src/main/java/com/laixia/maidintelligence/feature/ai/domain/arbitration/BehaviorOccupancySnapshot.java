package com.laixia.maidintelligence.feature.ai.domain.arbitration;

import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;

import java.util.Objects;

/**
 * Immutable native-behavior compatibility snapshot for one decision read.
 */
public record BehaviorOccupancySnapshot(
        BehaviorOccupancyLevel level,
        BehaviorOccupancyReason reason,
        MovementIntentSource movementSource,
        boolean movementFailOpen,
        boolean ownerCommandOverrideActive
) {
    public BehaviorOccupancySnapshot {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(reason, "reason");
        if (reason.level() != level) {
            throw new IllegalArgumentException(
                    "Occupancy reason and level do not match"
            );
        }
    }

    public static BehaviorOccupancySnapshot idle(
            boolean ownerCommandOverrideActive
    ) {
        return new BehaviorOccupancySnapshot(
                BehaviorOccupancyLevel.IDLE,
                BehaviorOccupancyReason.IDLE,
                null,
                false,
                ownerCommandOverrideActive
        );
    }

    public boolean allowsOwnerCommand() {
        return level != BehaviorOccupancyLevel.HARD;
    }

    public boolean allowsPassiveCompanion() {
        return level == BehaviorOccupancyLevel.IDLE;
    }
}
