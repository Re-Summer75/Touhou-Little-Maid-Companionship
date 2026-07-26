package com.laixia.maidintelligence.feature.physics.client.solver.spring;

/**
 * Per-bone buffeting of a shared atmosphere signal. Neighbouring parts of one
 * body sit in different eddies, so they must not swing in lockstep even when
 * the incoming field is identical.
 *
 * <p>Deterministic, allocation-free, and silent while the drive is zero.
 */
final class SpringMicroTurbulence {
    /** Slow flutter that survives even in nearly calm air. */
    private static final double BASE_FREQUENCY = 0.32D;
    /** Strouhal-like growth: faster shedding in stronger flow. */
    private static final double SPEED_FREQUENCY = 5.50D;
    private static final double MIN_FREQUENCY_JITTER = 0.62D;
    private static final double FREQUENCY_JITTER_SPAN = 0.80D;
    private static final float AMPLITUDE_DEPTH = 0.42F;
    private static final float AZIMUTH_SPREAD = 0.46F;
    private static final double PHASE_SPREAD = 512.0D;

    private SpringMicroTurbulence() {
    }

    static float amplitudeGain(int seed, double phase) {
        return 1.0F + AMPLITUDE_DEPTH * noise(seed ^ 0x9E3779B9, phase);
    }

    static float azimuthOffset(int seed, double phase) {
        return AZIMUTH_SPREAD * noise(seed ^ 0x85157AF5, phase);
    }

    /**
     * Shedding rate for this bone. Phase must be integrated with it, never
     * multiplied by elapsed time, so gust changes cannot jump the phase.
     */
    static double frequency(int seed, float driveMagnitude) {
        double jitter = MIN_FREQUENCY_JITTER
                + unit(seed ^ 0x27D4EB2F) * FREQUENCY_JITTER_SPAN;
        return (BASE_FREQUENCY
                + SPEED_FREQUENCY * Math.max(0.0F, driveMagnitude))
                * jitter;
    }

    /** Keeps bones off a common lattice cell before any time has passed. */
    static double phaseOffset(int seed) {
        return unit(seed ^ 0x165667B1) * PHASE_SPREAD;
    }

    private static float noise(int seed, double phase) {
        long cell = (long) Math.floor(phase);
        double local = phase - cell;
        float start = lattice(seed, cell);
        float end = lattice(seed, cell + 1L);
        double fade = local * local * (3.0D - 2.0D * local);
        return (float) (start + (end - start) * fade);
    }

    private static float lattice(int seed, long cell) {
        return unit(hash(seed, cell)) * 2.0F - 1.0F;
    }

    private static float unit(int value) {
        return (mix(value) >>> 8) * (1.0F / 16_777_216.0F);
    }

    private static int hash(int seed, long cell) {
        long value = cell * 0x9E3779B97F4A7C15L
                ^ Integer.toUnsignedLong(seed) * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        return (int) (value ^ (value >>> 31));
    }

    private static int mix(int value) {
        int mixed = value;
        mixed ^= mixed >>> 16;
        mixed *= 0x7FEB352D;
        mixed ^= mixed >>> 15;
        mixed *= 0x846CA68B;
        mixed ^= mixed >>> 16;
        return mixed;
    }
}
