package com.laixia.maidintelligence.feature.orchestration.application.claim;

import com.laixia.maidintelligence.feature.orchestration.api.CoordinationClaimService;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaim;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimRequest;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimState;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimToken;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationResourceKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Main-thread atomic resource claims with monotonically increasing fencing.
 */
public final class DefaultCoordinationClaimService
        implements CoordinationClaimService {
    private static final int MAX_RELEASE_HISTORY = 32;

    private final Thread ownerThread = Thread.currentThread();
    private final Map<CoordinationResourceKey, CoordinationClaim> active =
            new HashMap<>();
    private final ArrayDeque<CoordinationClaim> released =
            new ArrayDeque<>();
    private long epoch = 1L;
    private long nextFencingToken = 1L;

    @Override
    public synchronized Optional<CoordinationClaimToken> tryClaim(
            CoordinationClaimRequest request,
            long gameTime
    ) {
        ensureOwnerThread();
        prune(gameTime);
        CoordinationClaim current = active.get(request.resource());
        if (current != null) {
            CoordinationClaimToken token = current.token();
            if (token.holder().equals(request.holder())
                    && token.operationId().equals(
                    request.operationId()
            )) {
                renew(token, gameTime, request.leaseTicks());
                return Optional.of(token);
            }
            return Optional.empty();
        }

        long fencing = nextFencingToken++;
        CoordinationClaimToken token = new CoordinationClaimToken(
                new UUID(epoch, fencing),
                request.resource(),
                request.holder(),
                request.operationId(),
                fencing,
                epoch
        );
        active.put(request.resource(), new CoordinationClaim(
                token,
                CoordinationClaimState.CLAIMED,
                gameTime,
                deadline(gameTime, request.leaseTicks()),
                ""
        ));
        return Optional.of(token);
    }

    @Override
    public synchronized boolean occupy(
            CoordinationClaimToken token,
            long gameTime,
            int leaseTicks
    ) {
        ensureOwnerThread();
        requireLease(leaseTicks);
        CoordinationClaim current = current(token, gameTime);
        if (current == null) {
            return false;
        }
        active.put(token.resource(), new CoordinationClaim(
                token,
                CoordinationClaimState.OCCUPIED,
                current.claimedAtTick(),
                deadline(gameTime, leaseTicks),
                ""
        ));
        return true;
    }

    @Override
    public synchronized boolean renew(
            CoordinationClaimToken token,
            long gameTime,
            int leaseTicks
    ) {
        ensureOwnerThread();
        requireLease(leaseTicks);
        CoordinationClaim current = current(token, gameTime);
        if (current == null) {
            return false;
        }
        active.put(token.resource(), new CoordinationClaim(
                token,
                current.state(),
                current.claimedAtTick(),
                deadline(gameTime, leaseTicks),
                ""
        ));
        return true;
    }

    @Override
    public synchronized boolean owns(
            CoordinationClaimToken token,
            long gameTime
    ) {
        ensureOwnerThread();
        return current(token, gameTime) != null;
    }

    @Override
    public synchronized boolean isClaimed(
            CoordinationResourceKey resource,
            long gameTime
    ) {
        ensureOwnerThread();
        prune(gameTime);
        return active.containsKey(resource);
    }

    @Override
    public synchronized boolean release(
            CoordinationClaimToken token,
            long gameTime,
            String reason
    ) {
        ensureOwnerThread();
        CoordinationClaim current = current(token, gameTime);
        if (current == null) {
            return false;
        }
        releaseCurrent(current, gameTime, reason);
        return true;
    }

    @Override
    public synchronized int releaseHolder(
            UUID holder,
            long gameTime,
            String reason
    ) {
        ensureOwnerThread();
        prune(gameTime);
        List<CoordinationClaim> owned = active.values().stream()
                .filter(claim -> claim.token().holder().equals(holder))
                .toList();
        owned.forEach(claim -> releaseCurrent(claim, gameTime, reason));
        return owned.size();
    }

    @Override
    public synchronized void invalidateEpoch(
            long gameTime,
            String reason
    ) {
        ensureOwnerThread();
        List<CoordinationClaim> claims = List.copyOf(active.values());
        claims.forEach(claim -> releaseCurrent(claim, gameTime, reason));
        epoch = epoch == Long.MAX_VALUE ? 1L : epoch + 1L;
    }

    @Override
    public synchronized long epoch() {
        return epoch;
    }

    @Override
    public synchronized List<CoordinationClaim> activeClaims(
            long gameTime
    ) {
        ensureOwnerThread();
        prune(gameTime);
        List<CoordinationClaim> result =
                new ArrayList<>(active.values());
        result.sort(Comparator.comparing(
                claim -> claim.token().resource()
        ));
        return List.copyOf(result);
    }

    private CoordinationClaim current(
            CoordinationClaimToken token,
            long gameTime
    ) {
        prune(gameTime);
        CoordinationClaim current = active.get(token.resource());
        return current != null
                && current.token().equals(token)
                && current.activeAt(gameTime)
                && token.epoch() == epoch
                ? current
                : null;
    }

    private void prune(long gameTime) {
        List<CoordinationClaim> expired = active.values().stream()
                .filter(claim -> !claim.activeAt(gameTime))
                .toList();
        expired.forEach(claim ->
                releaseCurrent(claim, gameTime, "timeout"));
    }

    private void releaseCurrent(
            CoordinationClaim current,
            long gameTime,
            String reason
    ) {
        active.remove(current.token().resource());
        released.addLast(new CoordinationClaim(
                current.token(),
                CoordinationClaimState.RELEASED,
                current.claimedAtTick(),
                Math.max(current.claimedAtTick() + 1L, gameTime + 1L),
                reason == null ? "" : reason
        ));
        while (released.size() > MAX_RELEASE_HISTORY) {
            released.removeFirst();
        }
    }

    private void ensureOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "Coordination claims must mutate on their owner thread"
            );
        }
    }

    private static void requireLease(int leaseTicks) {
        if (leaseTicks < 1 || leaseTicks > 12_000) {
            throw new IllegalArgumentException(
                    "Claim lease must be in [1, 12000]"
            );
        }
    }

    private static long deadline(long gameTime, long duration) {
        return gameTime > Long.MAX_VALUE - duration
                ? Long.MAX_VALUE
                : gameTime + duration;
    }
}
