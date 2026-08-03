package com.laixia.maidintelligence.feature.ai;

import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyLevel;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyReason;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancySnapshot;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.MovementIntentAuthority;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentDecision;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentLease;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.domain.MovementTargetKind;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.OwnerCommandOverrideLease;

public final class BehaviorOccupancyVerification {
    private BehaviorOccupancyVerification() {
    }

    public static void main(String[] args) {
        authorityMatrixOrdersEmergencyCommitmentCommandAndSoft();
        occupancyReasonsMatchLevels();
        ownerCommandLeaseHonorsTtlAndClockRollback();
        snapshotHelpersEncodePassiveAndOwnerCommandGates();
        System.out.println(
                "Behavior occupancy verification passed."
        );
    }

    private static void authorityMatrixOrdersEmergencyCommitmentCommandAndSoft() {
        MovementIntentLease lease = new MovementIntentLease();
        require(
                lease.claim(
                        1L,
                        MovementIntentSource.LEISURE,
                        MovementTargetKind.BLOCK,
                        10L,
                        20,
                        true
                ) == MovementIntentDecision.ACQUIRED,
                "Leisure was not acquired"
        );
        require(
                lease.claim(
                        2L,
                        MovementIntentSource.OWNER_COMMAND,
                        MovementTargetKind.ENTITY,
                        20L,
                        20,
                        true
                ) == MovementIntentDecision.PREEMPTED,
                "Owner command did not preempt soft leisure"
        );
        require(
                lease.claim(
                        3L,
                        MovementIntentSource.PICKUP,
                        MovementTargetKind.BLOCK,
                        30L,
                        20,
                        true
                ) == MovementIntentDecision.PREEMPTED,
                "Native commitment did not preempt owner command"
        );
        require(
                lease.claim(
                        4L,
                        MovementIntentSource.OWNER_COMMAND,
                        MovementTargetKind.ENTITY,
                        40L,
                        20,
                        true
                ) == MovementIntentDecision.SUPPRESSED,
                "Owner command preempted pickup commitment"
        );
        require(
                lease.claim(
                        5L,
                        MovementIntentSource.COMBAT,
                        MovementTargetKind.ENTITY,
                        50L,
                        20,
                        true
                ) == MovementIntentDecision.PREEMPTED,
                "Combat did not preempt native commitment"
        );
        require(
                lease.claim(
                        6L,
                        MovementIntentSource.COMPANION,
                        MovementTargetKind.ENTITY,
                        60L,
                        20,
                        true
                ) == MovementIntentDecision.SUPPRESSED,
                "Passive companion replaced combat"
        );
        require(
                MovementIntentSource.FOLLOW_OWNER.authority()
                        == MovementIntentAuthority.NATIVE_SOFT
                        && MovementIntentSource.OWNER_COMMAND.authority()
                        == MovementIntentAuthority.OWNER_COMMAND
                        && MovementIntentSource.PICKUP.authority()
                        == MovementIntentAuthority.NATIVE_COMMITMENT
                        && MovementIntentSource.BREATH_AIR.authority()
                        == MovementIntentAuthority.EMERGENCY,
                "Authority ranks diverged from the compatibility matrix"
        );
    }

    private static void occupancyReasonsMatchLevels() {
        require(
                BehaviorOccupancyReason.IDLE.level()
                        == BehaviorOccupancyLevel.IDLE
                        && BehaviorOccupancyReason.LEISURE.level()
                        == BehaviorOccupancyLevel.SOFT
                        && BehaviorOccupancyReason.RANDOM_STROLL.level()
                        == BehaviorOccupancyLevel.SOFT
                        && BehaviorOccupancyReason.PICKUP.level()
                        == BehaviorOccupancyLevel.HARD
                        && BehaviorOccupancyReason.BUILT_IN_WORK.level()
                        == BehaviorOccupancyLevel.HARD
                        && BehaviorOccupancyReason.UNKNOWN_WRITER.level()
                        == BehaviorOccupancyLevel.HARD,
                "Occupancy reasons do not map to expected levels"
        );
        require(
                BehaviorOccupancyLevel.IDLE.code() == 0
                        && BehaviorOccupancyLevel.SOFT.code() == 1
                        && BehaviorOccupancyLevel.HARD.code() == 2,
                "Occupancy level codes are unstable"
        );
    }

    private static void ownerCommandLeaseHonorsTtlAndClockRollback() {
        OwnerCommandOverrideLease lease = new OwnerCommandOverrideLease();
        require(lease.acquire(10L, 5), "Owner command lease was rejected");
        require(lease.isActive(14L), "Owner command expired too early");
        require(!lease.isActive(15L), "Owner command survived exclusive expiry");
        require(lease.acquire(20L, 5), "Owner command could not renew");
        require(
                !lease.isActive(19L),
                "Clock rollback retained a future owner-command lease"
        );
        lease.acquire(30L, 5);
        lease.release();
        require(
                !lease.isActive(31L),
                "Released owner-command lease remained active"
        );
    }

    private static void snapshotHelpersEncodePassiveAndOwnerCommandGates() {
        BehaviorOccupancySnapshot idle =
                BehaviorOccupancySnapshot.idle(false);
        require(
                idle.allowsPassiveCompanion() && idle.allowsOwnerCommand(),
                "Idle occupancy blocked companion or owner command"
        );
        BehaviorOccupancySnapshot soft = new BehaviorOccupancySnapshot(
                BehaviorOccupancyLevel.SOFT,
                BehaviorOccupancyReason.LEISURE,
                MovementIntentSource.LEISURE,
                false,
                true
        );
        require(
                !soft.allowsPassiveCompanion() && soft.allowsOwnerCommand(),
                "Soft leisure gate was inverted"
        );
        BehaviorOccupancySnapshot hard = new BehaviorOccupancySnapshot(
                BehaviorOccupancyLevel.HARD,
                BehaviorOccupancyReason.PICKUP,
                MovementIntentSource.PICKUP,
                false,
                false
        );
        require(
                !hard.allowsPassiveCompanion() && !hard.allowsOwnerCommand(),
                "Hard pickup allowed companion or owner command"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
