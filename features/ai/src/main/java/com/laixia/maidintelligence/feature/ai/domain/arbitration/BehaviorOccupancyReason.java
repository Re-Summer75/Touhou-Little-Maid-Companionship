package com.laixia.maidintelligence.feature.ai.domain.arbitration;

/**
 * Stable diagnostic reason for native behavior occupancy.
 */
public enum BehaviorOccupancyReason {
    IDLE(0, BehaviorOccupancyLevel.IDLE),
    LEISURE(10, BehaviorOccupancyLevel.SOFT),
    RANDOM_STROLL(11, BehaviorOccupancyLevel.SOFT),
    BEG(12, BehaviorOccupancyLevel.SOFT),
    FOLLOW_OWNER(13, BehaviorOccupancyLevel.SOFT),
    FOLLOW_OWNER_VEHICLE(14, BehaviorOccupancyLevel.SOFT),
    PASSIVE_SEAT(15, BehaviorOccupancyLevel.SOFT),
    BREATH_AIR(100, BehaviorOccupancyLevel.HARD),
    COMBAT(101, BehaviorOccupancyLevel.HARD),
    PANIC(102, BehaviorOccupancyLevel.HARD),
    HOME_RETURN(103, BehaviorOccupancyLevel.HARD),
    BUILT_IN_WORK(104, BehaviorOccupancyLevel.HARD),
    WORK_TARGET(105, BehaviorOccupancyLevel.HARD),
    PICKUP(106, BehaviorOccupancyLevel.HARD),
    STEAL_EDIBLE(107, BehaviorOccupancyLevel.HARD),
    USING_ITEM(108, BehaviorOccupancyLevel.HARD),
    SLEEPING(109, BehaviorOccupancyLevel.HARD),
    LEASHED(110, BehaviorOccupancyLevel.HARD),
    ORDERED_SIT(111, BehaviorOccupancyLevel.HARD),
    SITTING_POSE(112, BehaviorOccupancyLevel.HARD),
    COMMAND_SEAT(113, BehaviorOccupancyLevel.HARD),
    OTHER_PASSENGER(114, BehaviorOccupancyLevel.HARD),
    UNKNOWN_WRITER(115, BehaviorOccupancyLevel.HARD);

    private final int code;
    private final BehaviorOccupancyLevel level;

    BehaviorOccupancyReason(
            int code,
            BehaviorOccupancyLevel level
    ) {
        this.code = code;
        this.level = level;
    }

    public int code() {
        return code;
    }

    public BehaviorOccupancyLevel level() {
        return level;
    }
}
