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
    /**
     * Longest step the integrator is asked to take. There is one Verlet step
     * per frame and no substepping, so this is also the largest span any single
     * step covers, and stiffness enters it as {@code k * dt}: at a tenth of a
     * second the restoring pull alone rotates a light part far enough in one
     * step to cross a collider that a shorter step would have stopped at.
     *
     * <p>One game tick is the natural place to put it — the animation timeline
     * this reads advances in ticks, so every frame rate from 20 up is unaffected
     * and only a genuine stall is clamped. A stall then runs slightly slow
     * rather than resolving a large step badly, which is the better failure:
     * time is already being lost, and the alternative shows up as parts jumping
     * through geometry on the frame the game recovers.
     */
    private static final double MAX_DELTA_SECONDS = 1.0D / TICKS_PER_SECOND;
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
