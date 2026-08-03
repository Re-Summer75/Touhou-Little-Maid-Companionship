package com.laixia.maidintelligence.feature.behavior.domain.learning;

public record OwnerPreference(
        double value,
        int samples,
        long lastUpdatedTick
) {
    private static final double ALPHA = 0.15D;

    public OwnerPreference {
        if (!Double.isFinite(value)
                || value < -1.0D
                || value > 1.0D
                || samples < 0
                || samples > 1_000) {
            throw new IllegalArgumentException("Invalid owner preference");
        }
    }

    public static OwnerPreference initial() {
        return new OwnerPreference(0.0D, 0, 0L);
    }

    public OwnerPreference observe(double target, long gameTime) {
        double bounded = Math.max(-1.0D, Math.min(1.0D, target));
        double next = samples == 0
                ? bounded
                : value + ALPHA * (bounded - value);
        return new OwnerPreference(
                Math.max(-1.0D, Math.min(1.0D, next)),
                Math.min(1_000, samples + 1),
                gameTime
        );
    }
}
