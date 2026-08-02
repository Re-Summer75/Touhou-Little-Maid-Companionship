package com.laixia.maidintelligence.feature.ai.domain;

import java.util.Objects;

/**
 * Per-maid lease that stabilizes known movement writers for a short window.
 *
 * <p>The lease never owns game objects. Adapter code reconciles its primitive
 * target identity with the live Brain memory and drops into fail-open whenever
 * an unknown writer replaces that target.</p>
 */
public final class MovementIntentLease {
    private MovementIntentSource holder;
    private MovementTargetKind targetKind = MovementTargetKind.NONE;
    private long targetIdentity;
    private long recordedAtTick;
    private long expiresAtTick;
    private long failOpenRecordedAtTick;
    private long failOpenUntilTick;

    public MovementIntentDecision claim(
            long gameTime,
            MovementIntentSource source,
            MovementTargetKind incomingTargetKind,
            long incomingTargetIdentity,
            int ttlTicks,
            boolean enforce
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(incomingTargetKind, "incomingTargetKind");
        if (incomingTargetKind == MovementTargetKind.NONE
                || incomingTargetKind == MovementTargetKind.UNMANAGED
                || ttlTicks <= 0) {
            return MovementIntentDecision.PASS_THROUGH;
        }

        normalize(gameTime);
        if (isFailOpen(gameTime)) {
            return MovementIntentDecision.PASS_THROUGH;
        }
        if (holder == null) {
            replace(
                    gameTime,
                    source,
                    incomingTargetKind,
                    incomingTargetIdentity,
                    ttlTicks
            );
            return MovementIntentDecision.ACQUIRED;
        }
        if (holder == source) {
            boolean sameTarget = targetKind == incomingTargetKind
                    && targetIdentity == incomingTargetIdentity;
            replace(
                    gameTime,
                    source,
                    incomingTargetKind,
                    incomingTargetIdentity,
                    ttlTicks
            );
            return sameTarget
                    ? MovementIntentDecision.RENEWED
                    : MovementIntentDecision.RETARGETED;
        }
        if (holder == MovementIntentSource.PICKUP
                && (source == MovementIntentSource.FOLLOW_OWNER
                || source == MovementIntentSource.COMPANION)) {
            if (enforce) {
                return MovementIntentDecision.SUPPRESSED;
            }
            // Observation mode follows vanilla while reporting the conflict
            // that conservative mode would defer.
            replace(
                    gameTime,
                    source,
                    incomingTargetKind,
                    incomingTargetIdentity,
                    ttlTicks
            );
            return MovementIntentDecision.OBSERVED_CONFLICT;
        }
        if (source.priority() < holder.priority()) {
            replace(
                    gameTime,
                    source,
                    incomingTargetKind,
                    incomingTargetIdentity,
                    ttlTicks
            );
            return MovementIntentDecision.PREEMPTED;
        }
        if (enforce) {
            return MovementIntentDecision.SUPPRESSED;
        }

        // Observation mode follows the target vanilla actually wrote while
        // retaining a count of conflicts conservative mode would suppress.
        replace(
                gameTime,
                source,
                incomingTargetKind,
                incomingTargetIdentity,
                ttlTicks
        );
        return MovementIntentDecision.OBSERVED_CONFLICT;
    }

    /**
     * Reconciles the lease with current WALK_TARGET memory.
     *
     * @return true when a different, externally-owned target enabled fail-open
     */
    public boolean reconcile(
            long gameTime,
            MovementTargetKind observedTargetKind,
            long observedTargetIdentity,
            int failOpenTicks
    ) {
        Objects.requireNonNull(observedTargetKind, "observedTargetKind");
        normalize(gameTime);
        if (holder == null) {
            return false;
        }
        if (observedTargetKind == MovementTargetKind.NONE) {
            clearLease();
            return false;
        }
        if (targetKind == observedTargetKind
                && targetIdentity == observedTargetIdentity) {
            return false;
        }

        clearLease();
        if (failOpenTicks <= 0) {
            return false;
        }
        failOpenRecordedAtTick = gameTime;
        failOpenUntilTick = expirationTick(gameTime, failOpenTicks);
        return true;
    }

    public void hardReset() {
        clearLease();
        failOpenRecordedAtTick = 0L;
        failOpenUntilTick = 0L;
    }

    public boolean hasActiveLease(long gameTime) {
        normalize(gameTime);
        return holder != null;
    }

    public boolean isFailOpen(long gameTime) {
        if (failOpenUntilTick == 0L) {
            return false;
        }
        if (gameTime < failOpenRecordedAtTick
                || gameTime >= failOpenUntilTick) {
            failOpenRecordedAtTick = 0L;
            failOpenUntilTick = 0L;
            return false;
        }
        return true;
    }

    public MovementIntentSource holder() {
        return holder;
    }

    public MovementTargetKind targetKind() {
        return targetKind;
    }

    public long targetIdentity() {
        return targetIdentity;
    }

    public long expiresAtTick() {
        return expiresAtTick;
    }

    private void normalize(long gameTime) {
        if (holder != null && (gameTime < recordedAtTick
                || gameTime >= expiresAtTick)) {
            clearLease();
        }
        isFailOpen(gameTime);
    }

    private void replace(
            long gameTime,
            MovementIntentSource source,
            MovementTargetKind incomingTargetKind,
            long incomingTargetIdentity,
            int ttlTicks
    ) {
        holder = source;
        targetKind = incomingTargetKind;
        targetIdentity = incomingTargetIdentity;
        recordedAtTick = gameTime;
        expiresAtTick = expirationTick(gameTime, ttlTicks);
    }

    private void clearLease() {
        holder = null;
        targetKind = MovementTargetKind.NONE;
        targetIdentity = 0L;
        recordedAtTick = 0L;
        expiresAtTick = 0L;
    }

    private static long expirationTick(long gameTime, int ttlTicks) {
        long ttl = ttlTicks;
        return gameTime > Long.MAX_VALUE - ttl
                ? Long.MAX_VALUE
                : gameTime + ttl;
    }
}
