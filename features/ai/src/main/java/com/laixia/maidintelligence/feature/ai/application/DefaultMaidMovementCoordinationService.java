package com.laixia.maidintelligence.feature.ai.application;

import com.laixia.maidintelligence.feature.ai.api.MaidMovementCoordinationApi;
import com.laixia.maidintelligence.feature.ai.api.MovementCoordinationMode;
import com.laixia.maidintelligence.feature.ai.api.MovementCoordinationSnapshot;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentDecision;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentLease;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.domain.MovementTargetKind;

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class DefaultMaidMovementCoordinationService
        implements MaidMovementCoordinationApi {
    private final Supplier<MovementCoordinationMode> mode;
    private final IntSupplier leaseTicks;
    private final IntSupplier pickupCommitmentTicks;
    private final IntSupplier failOpenTicks;
    private final LongAdder claims = new LongAdder();
    private final LongAdder renewals = new LongAdder();
    private final LongAdder retargets = new LongAdder();
    private final LongAdder preemptions = new LongAdder();
    private final LongAdder suppressions = new LongAdder();
    private final LongAdder observedConflicts = new LongAdder();
    private final LongAdder failOpenTransitions = new LongAdder();

    public DefaultMaidMovementCoordinationService(
            Supplier<MovementCoordinationMode> mode,
            IntSupplier leaseTicks,
            IntSupplier pickupCommitmentTicks,
            IntSupplier failOpenTicks
    ) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.leaseTicks = Objects.requireNonNull(leaseTicks, "leaseTicks");
        this.pickupCommitmentTicks = Objects.requireNonNull(
                pickupCommitmentTicks,
                "pickupCommitmentTicks"
        );
        this.failOpenTicks = Objects.requireNonNull(
                failOpenTicks,
                "failOpenTicks"
        );
    }

    @Override
    public MovementCoordinationMode mode() {
        return Objects.requireNonNull(mode.get(), "movement mode");
    }

    @Override
    public int leaseTicks() {
        return Math.max(0, leaseTicks.getAsInt());
    }

    @Override
    public int pickupCommitmentTicks() {
        return Math.max(0, pickupCommitmentTicks.getAsInt());
    }

    @Override
    public int failOpenTicks() {
        return Math.max(0, failOpenTicks.getAsInt());
    }

    @Override
    public MovementIntentDecision claim(
            MovementIntentLease lease,
            long gameTime,
            MovementIntentSource source,
            MovementTargetKind targetKind,
            long targetIdentity
    ) {
        Objects.requireNonNull(lease, "lease");
        MovementCoordinationMode currentMode = mode();
        if (!currentMode.trackingEnabled()) {
            lease.hardReset();
            return MovementIntentDecision.PASS_THROUGH;
        }

        MovementIntentDecision decision = lease.claim(
                gameTime,
                source,
                targetKind,
                targetIdentity,
                source == MovementIntentSource.PICKUP
                        ? pickupCommitmentTicks()
                        : leaseTicks(),
                currentMode.enforcementEnabled()
        );
        record(decision);
        return decision;
    }

    @Override
    public boolean reconcile(
            MovementIntentLease lease,
            long gameTime,
            MovementTargetKind observedTargetKind,
            long observedTargetIdentity
    ) {
        Objects.requireNonNull(lease, "lease");
        if (!mode().trackingEnabled()) {
            lease.hardReset();
            return false;
        }
        boolean enteredFailOpen = lease.reconcile(
                gameTime,
                observedTargetKind,
                observedTargetIdentity,
                failOpenTicks()
        );
        if (enteredFailOpen) {
            failOpenTransitions.increment();
        }
        return enteredFailOpen;
    }

    @Override
    public void hardReset(MovementIntentLease lease) {
        Objects.requireNonNull(lease, "lease").hardReset();
    }

    @Override
    public MovementCoordinationSnapshot snapshot() {
        return new MovementCoordinationSnapshot(
                claims.sum(),
                renewals.sum(),
                retargets.sum(),
                preemptions.sum(),
                suppressions.sum(),
                observedConflicts.sum(),
                failOpenTransitions.sum()
        );
    }

    @Override
    public void resetMetrics() {
        claims.reset();
        renewals.reset();
        retargets.reset();
        preemptions.reset();
        suppressions.reset();
        observedConflicts.reset();
        failOpenTransitions.reset();
    }

    private void record(MovementIntentDecision decision) {
        switch (decision) {
            case ACQUIRED -> claims.increment();
            case RENEWED -> renewals.increment();
            case RETARGETED -> {
                claims.increment();
                retargets.increment();
            }
            case PREEMPTED -> {
                claims.increment();
                preemptions.increment();
            }
            case SUPPRESSED -> suppressions.increment();
            case OBSERVED_CONFLICT -> observedConflicts.increment();
            case PASS_THROUGH -> {
                // Disabled or fail-open decisions intentionally stay invisible.
            }
        }
    }
}
