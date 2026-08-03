package com.laixia.maidintelligence.feature.ai.domain.arbitration;

/**
 * Per-maid bounded lease that suppresses only native soft behaviors.
 */
public final class OwnerCommandOverrideLease {
    private long recordedAtTick;
    private long expiresAtTick;

    public boolean acquire(long gameTime, int ttlTicks) {
        if (ttlTicks <= 0) {
            return false;
        }
        recordedAtTick = gameTime;
        expiresAtTick = expirationTick(gameTime, ttlTicks);
        return true;
    }

    public boolean isActive(long gameTime) {
        if (expiresAtTick == 0L) {
            return false;
        }
        if (gameTime < recordedAtTick || gameTime >= expiresAtTick) {
            release();
            return false;
        }
        return true;
    }

    public long expiresAtTick() {
        return expiresAtTick;
    }

    public void release() {
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
