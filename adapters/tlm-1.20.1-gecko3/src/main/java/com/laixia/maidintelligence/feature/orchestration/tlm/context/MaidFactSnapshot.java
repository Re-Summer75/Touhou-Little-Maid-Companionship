package com.laixia.maidintelligence.feature.orchestration.tlm.context;

import com.laixia.maidintelligence.feature.behavior.domain.owner.OwnerFacts;

/**
 * One tick's worth of facts about a maid, read once and answered from many
 * times.
 *
 * <p>Lives apart from the reader because it is data and the reader is logic;
 * together they pushed that file past the source-layout limit, and the split
 * that limit asks for is this one rather than an arbitrary halving of the
 * dispatch.
 */
record MaidFactSnapshot(

            boolean ownerValid,
            double ownerDistance,
            int favorability,
            int hunger,
            boolean snackCabinetMealAvailable,
            boolean followMode,
            boolean homeMode,
            boolean orderedSit,
            boolean sittingPose,
            boolean sleeping,
            boolean leashed,
            boolean passenger,
            boolean passiveSeat,
            boolean canMove,
            boolean combatActive,
            double hostilePressure,
            boolean attackTargetPresent,
            boolean panicActive,
            boolean workTargetPresent,
            boolean usingItem,
            int behaviorOccupancyLevel,
            boolean looseFoodAvailable,
            double homeDistance,
            OwnerFacts owner
) {
}
