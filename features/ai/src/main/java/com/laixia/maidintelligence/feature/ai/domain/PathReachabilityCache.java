package com.laixia.maidintelligence.feature.ai.domain;

/**
 * Small per-maid cache for successful reachability checks.
 *
 * <p>Only positive results belong here: a cached failure could hide a newly
 * opened route. Entries also bind to an adapter-owned navigation identity so
 * switching between land and water navigation cannot reuse incompatible
 * results.</p>
 */
public final class PathReachabilityCache {
    private static final int CAPACITY = 4;

    private final long[] originPositions = new long[CAPACITY];
    private final long[] targetPositions = new long[CAPACITY];
    private final long[] targetIdentities = new long[CAPACITY];
    private final long[] recordedAtTicks = new long[CAPACITY];
    private final long[] expiresAtTicks = new long[CAPACITY];
    private final int[] navigationIdentities = new int[CAPACITY];
    private final boolean[] occupied = new boolean[CAPACITY];
    private int replacementIndex;

    public boolean containsReachable(
            long gameTime,
            long originPosition,
            long targetPosition,
            long targetIdentity,
            int navigationIdentity
    ) {
        for (int index = 0; index < CAPACITY; index++) {
            if (!occupied[index]) {
                continue;
            }
            if (gameTime < recordedAtTicks[index]
                    || gameTime >= expiresAtTicks[index]) {
                discard(index);
                continue;
            }
            if (originPositions[index] == originPosition
                    && targetPositions[index] == targetPosition
                    && targetIdentities[index] == targetIdentity
                    && navigationIdentities[index] == navigationIdentity) {
                return true;
            }
        }
        return false;
    }

    public void rememberReachable(
            long gameTime,
            int ttlTicks,
            long originPosition,
            long targetPosition,
            long targetIdentity,
            int navigationIdentity
    ) {
        if (ttlTicks <= 0) {
            return;
        }
        int matchingIndex = findMatching(
                originPosition,
                targetPosition,
                targetIdentity,
                navigationIdentity
        );
        int index = matchingIndex >= 0 ? matchingIndex : nextReplacementIndex();
        occupied[index] = true;
        originPositions[index] = originPosition;
        targetPositions[index] = targetPosition;
        targetIdentities[index] = targetIdentity;
        recordedAtTicks[index] = gameTime;
        expiresAtTicks[index] = expirationTick(gameTime, ttlTicks);
        navigationIdentities[index] = navigationIdentity;
    }

    public void clear() {
        for (int index = 0; index < CAPACITY; index++) {
            discard(index);
        }
        replacementIndex = 0;
    }

    private int findMatching(
            long originPosition,
            long targetPosition,
            long targetIdentity,
            int navigationIdentity
    ) {
        for (int index = 0; index < CAPACITY; index++) {
            if (occupied[index]
                    && originPositions[index] == originPosition
                    && targetPositions[index] == targetPosition
                    && targetIdentities[index] == targetIdentity
                    && navigationIdentities[index] == navigationIdentity) {
                return index;
            }
        }
        return -1;
    }

    private int nextReplacementIndex() {
        int index = replacementIndex;
        replacementIndex = (replacementIndex + 1) % CAPACITY;
        return index;
    }

    private void discard(int index) {
        occupied[index] = false;
    }

    private static long expirationTick(long gameTime, int ttlTicks) {
        long ttl = ttlTicks;
        return gameTime > Long.MAX_VALUE - ttl
                ? Long.MAX_VALUE
                : gameTime + ttl;
    }
}
