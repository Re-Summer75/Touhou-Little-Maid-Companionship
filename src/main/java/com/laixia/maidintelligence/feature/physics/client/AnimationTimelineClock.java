package com.laixia.maidintelligence.feature.physics.client;

/**
 * Advances physics on the same tick-plus-partial timeline used by TLM
 * hardcoded animations.
 *
 * <p>One animation pose can be rendered more than once. Equal or out-of-order
 * samples therefore return a zero step instead of integrating another Verlet
 * substep from a small wall-clock interval.</p>
 */
final class AnimationTimelineClock {
    private static final double TICKS_PER_SECOND = 20.0D;
    private static final double MIN_ADVANCE_TICKS = 1.0E-6D;
    private static final double MAX_DELTA_SECONDS = 0.1D;
    private static final double MAX_GAP_SECONDS = 0.25D;

    private double latestTick = Double.NaN;
    private boolean discontinuous;

    float advance(double animationTick, boolean paused) {
        discontinuous = false;
        if (!Double.isFinite(animationTick)) {
            reset();
            discontinuous = true;
            return 0.0F;
        }
        if (!Double.isFinite(latestTick) || paused) {
            latestTick = animationTick;
            return 0.0F;
        }

        double deltaTicks = animationTick - latestTick;
        if (deltaTicks <= MIN_ADVANCE_TICKS) {
            return 0.0F;
        }
        latestTick = animationTick;
        double seconds = deltaTicks / TICKS_PER_SECOND;
        if (seconds > MAX_GAP_SECONDS) {
            discontinuous = true;
            return 0.0F;
        }
        return (float) Math.min(seconds, MAX_DELTA_SECONDS);
    }

    boolean discontinuous() {
        return discontinuous;
    }

    void reset() {
        latestTick = Double.NaN;
        discontinuous = false;
    }
}
