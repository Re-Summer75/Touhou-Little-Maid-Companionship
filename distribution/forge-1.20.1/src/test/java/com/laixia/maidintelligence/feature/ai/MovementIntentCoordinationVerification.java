package com.laixia.maidintelligence.feature.ai;

import com.laixia.maidintelligence.feature.ai.api.MovementCoordinationMode;
import com.laixia.maidintelligence.feature.ai.api.MovementCoordinationSnapshot;
import com.laixia.maidintelligence.feature.ai.application.DefaultMaidMovementCoordinationService;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentDecision;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentLease;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.domain.MovementTargetKind;
import com.laixia.maidintelligence.feature.ai.domain.OwnerFollowPolicy;

import java.util.concurrent.atomic.AtomicReference;

public final class MovementIntentCoordinationVerification {
    private MovementIntentCoordinationVerification() {
    }

    public static void main(String[] args) {
        verifiesNativePriorityAndRenewal();
        verifiesCombatPreemptsRoutineMovement();
        verifiesPickupCommitmentDefersFollow();
        verifiesOwnerTeleportBackstopBoundaries();
        verifiesExpiryAndClockRollback();
        verifiesObservationAndOffModes();
        verifiesExternalWriteFailOpen();
        verifiesHardResetAndMetrics();
        System.out.println(
                "Maid movement intent coordination verification passed."
        );
    }

    private static void verifiesNativePriorityAndRenewal() {
        DefaultMaidMovementCoordinationService service = service(
                new AtomicReference<>(
                        MovementCoordinationMode.CONSERVATIVE
                )
        );
        MovementIntentLease lease = new MovementIntentLease();

        require(
                claim(
                        service,
                        lease,
                        10L,
                        MovementIntentSource.BUILT_IN_WORK,
                        100L
                ) == MovementIntentDecision.ACQUIRED,
                "Initial built-in work target was not acquired"
        );
        require(
                claim(
                        service,
                        lease,
                        11L,
                        MovementIntentSource.PICKUP,
                        200L
                ) == MovementIntentDecision.SUPPRESSED,
                "Lower-priority pickup target replaced active work"
        );
        require(
                lease.holder() == MovementIntentSource.BUILT_IN_WORK
                        && lease.targetIdentity() == 100L,
                "Suppression changed the active lease"
        );
        require(
                claim(
                        service,
                        lease,
                        12L,
                        MovementIntentSource.BREATH_AIR,
                        300L
                ) == MovementIntentDecision.PREEMPTED,
                "Breathing did not preempt lower-priority work"
        );
        require(
                claim(
                        service,
                        lease,
                        13L,
                        MovementIntentSource.BREATH_AIR,
                        300L
                ) == MovementIntentDecision.RENEWED,
                "Same-source same-target claim did not renew"
        );
        require(
                claim(
                        service,
                        lease,
                        14L,
                        MovementIntentSource.BREATH_AIR,
                        301L
                ) == MovementIntentDecision.RETARGETED,
                "Same source could not update its own target"
        );
    }

    private static void verifiesCombatPreemptsRoutineMovement() {
        DefaultMaidMovementCoordinationService service = service(
                new AtomicReference<>(
                        MovementCoordinationMode.CONSERVATIVE
                )
        );
        MovementIntentLease lease = new MovementIntentLease();
        claim(
                service,
                lease,
                20L,
                MovementIntentSource.FOLLOW_OWNER,
                100L
        );
        require(
                claim(
                        service,
                        lease,
                        21L,
                        MovementIntentSource.COMBAT,
                        200L
                ) == MovementIntentDecision.PREEMPTED,
                "Combat did not preempt routine following"
        );
        require(
                lease.holder() == MovementIntentSource.COMBAT,
                "Combat preemption did not retain the combat lease"
        );
        require(
                claim(
                        service,
                        lease,
                        22L,
                        MovementIntentSource.BUILT_IN_WORK,
                        300L
                ) == MovementIntentDecision.SUPPRESSED,
                "Routine work replaced an active combat target"
        );
    }

    private static void verifiesPickupCommitmentDefersFollow() {
        DefaultMaidMovementCoordinationService service = service(
                new AtomicReference<>(
                        MovementCoordinationMode.CONSERVATIVE
                )
        );
        MovementIntentLease lease = new MovementIntentLease();
        claim(
                service,
                lease,
                10L,
                MovementIntentSource.PICKUP,
                100L
        );

        require(
                lease.expiresAtTick() == 50L,
                "Pickup did not receive its bounded commitment window"
        );
        require(
                claim(
                        service,
                        lease,
                        20L,
                        MovementIntentSource.FOLLOW_OWNER,
                        200L
                ) == MovementIntentDecision.SUPPRESSED,
                "Normal following interrupted an active pickup"
        );
        require(
                lease.holder() == MovementIntentSource.PICKUP,
                "Suppressed following replaced the pickup commitment"
        );
        require(
                claim(
                        service,
                        lease,
                        21L,
                        MovementIntentSource.HOME_RETURN,
                        300L
                ) == MovementIntentDecision.PREEMPTED,
                "Home return could not preempt a pickup commitment"
        );

        lease.hardReset();
        claim(
                service,
                lease,
                10L,
                MovementIntentSource.PICKUP,
                100L
        );
        require(
                claim(
                        service,
                        lease,
                        50L,
                        MovementIntentSource.FOLLOW_OWNER,
                        200L
                ) == MovementIntentDecision.ACQUIRED,
                "Following remained blocked after pickup commitment expiry"
        );
    }

    private static void verifiesOwnerTeleportBackstopBoundaries() {
        OwnerFollowPolicy policy = OwnerFollowPolicy.INSTANCE;
        double threshold = OwnerFollowPolicy.TELEPORT_DISTANCE;
        require(
                threshold == 24.0D,
                "Owner teleport backstop is no longer the documented 24 blocks"
        );
        require(
                !policy.shouldTeleport(threshold * threshold),
                "Exactly at the backstop counted as stranded"
        );
        require(
                policy.shouldTeleport(threshold * threshold + 1.0D),
                "Past the backstop did not strand her"
        );
        require(
                !policy.shouldTeleport(100.0D),
                "Ten blocks still teleported, so the old radius coupling "
                        + "survived"
        );
        require(
                !policy.shouldTeleport(Double.NaN)
                        && !policy.shouldTeleport(-1.0D),
                "A distance that cannot be measured was treated as far"
        );
    }

    private static void verifiesExpiryAndClockRollback() {
        DefaultMaidMovementCoordinationService service = service(
                new AtomicReference<>(
                        MovementCoordinationMode.CONSERVATIVE
                )
        );
        MovementIntentLease lease = new MovementIntentLease();
        claim(
                service,
                lease,
                20L,
                MovementIntentSource.FOLLOW_OWNER,
                1L
        );

        require(
                lease.hasActiveLease(31L),
                "Movement lease expired before its configured TTL"
        );
        require(
                !lease.hasActiveLease(32L),
                "Movement lease survived its exclusive expiry tick"
        );

        claim(
                service,
                lease,
                50L,
                MovementIntentSource.FOLLOW_OWNER,
                1L
        );
        require(
                !lease.hasActiveLease(49L),
                "Clock rollback retained a future movement lease"
        );
    }

    private static void verifiesObservationAndOffModes() {
        AtomicReference<MovementCoordinationMode> mode =
                new AtomicReference<>(MovementCoordinationMode.OBSERVE);
        DefaultMaidMovementCoordinationService service = service(mode);
        MovementIntentLease lease = new MovementIntentLease();

        claim(
                service,
                lease,
                1L,
                MovementIntentSource.BREATH_AIR,
                10L
        );
        MovementIntentDecision observed = claim(
                service,
                lease,
                2L,
                MovementIntentSource.PICKUP,
                20L
        );
        require(
                observed == MovementIntentDecision.OBSERVED_CONFLICT
                        && observed.writeAllowed(),
                "Observation mode changed movement output"
        );
        require(
                lease.holder() == MovementIntentSource.PICKUP,
                "Observation mode did not follow the vanilla target"
        );
        require(
                claim(
                        service,
                        lease,
                        3L,
                        MovementIntentSource.FOLLOW_OWNER,
                        30L
                ) == MovementIntentDecision.OBSERVED_CONFLICT,
                "Observation mode enforced the pickup commitment"
        );

        mode.set(MovementCoordinationMode.OFF);
        require(
                claim(
                        service,
                        lease,
                        4L,
                        MovementIntentSource.BREATH_AIR,
                        30L
                ) == MovementIntentDecision.PASS_THROUGH,
                "Off mode did not pass through movement"
        );
        require(
                !lease.hasActiveLease(4L),
                "Off mode retained movement coordination state"
        );
    }

    private static void verifiesExternalWriteFailOpen() {
        DefaultMaidMovementCoordinationService service = service(
                new AtomicReference<>(
                        MovementCoordinationMode.CONSERVATIVE
                )
        );
        MovementIntentLease lease = new MovementIntentLease();
        claim(
                service,
                lease,
                100L,
                MovementIntentSource.BUILT_IN_WORK,
                10L
        );

        require(
                service.reconcile(
                        lease,
                        101L,
                        MovementTargetKind.BLOCK,
                        999L
                ),
                "Unknown target replacement did not trigger fail-open"
        );
        require(
                claim(
                        service,
                        lease,
                        102L,
                        MovementIntentSource.BREATH_AIR,
                        20L
                ) == MovementIntentDecision.PASS_THROUGH,
                "Fail-open blocked a known writer"
        );
        require(
                claim(
                        service,
                        lease,
                        141L,
                        MovementIntentSource.BREATH_AIR,
                        20L
                ) == MovementIntentDecision.ACQUIRED,
                "Coordination did not recover after fail-open timeout"
        );
    }

    private static void verifiesHardResetAndMetrics() {
        DefaultMaidMovementCoordinationService service = service(
                new AtomicReference<>(
                        MovementCoordinationMode.CONSERVATIVE
                )
        );
        MovementIntentLease lease = new MovementIntentLease();
        claim(
                service,
                lease,
                1L,
                MovementIntentSource.BUILT_IN_WORK,
                1L
        );
        claim(
                service,
                lease,
                2L,
                MovementIntentSource.PICKUP,
                2L
        );
        service.hardReset(lease);

        require(
                !lease.hasActiveLease(2L) && !lease.isFailOpen(2L),
                "Hard reset retained coordination state"
        );
        MovementCoordinationSnapshot snapshot = service.snapshot();
        require(
                snapshot.claims() == 1L
                        && snapshot.suppressions() == 1L,
                "Movement coordination metrics changed"
        );

        service.resetMetrics();
        require(
                service.snapshot().equals(new MovementCoordinationSnapshot(
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L
                )),
                "Movement coordination metrics were not reset"
        );
    }

    private static DefaultMaidMovementCoordinationService service(
            AtomicReference<MovementCoordinationMode> mode
    ) {
        return new DefaultMaidMovementCoordinationService(
                mode::get,
                () -> 12,
                () -> 40,
                () -> 40
        );
    }

    private static MovementIntentDecision claim(
            DefaultMaidMovementCoordinationService service,
            MovementIntentLease lease,
            long gameTime,
            MovementIntentSource source,
            long targetIdentity
    ) {
        return service.claim(
                lease,
                gameTime,
                source,
                MovementTargetKind.BLOCK,
                targetIdentity
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
