package com.laixia.maidintelligence.feature.atmosphere.client.wind;

/**
 * Deterministic allocation-free gradient noise for an advected wind field.
 */
final class AdvectedGradientNoise {
    private static final double DIAGONAL = 0.7071067811865476D;
    private static final double OUTPUT_SCALE = 1.4142135623730951D;
    private static final double OCTAVE_X = 1.9238795325112867D;
    private static final double OCTAVE_Z = 0.5132096121052549D;
    // 2^(-1/3) approximates velocity amplitude across Kolmogorov octaves.
    private static final float PERSISTENCE = 0.7937005F;

    private AdvectedGradientNoise() {
    }

    static float fractal(
            double x,
            double z,
            int seed,
            int octaves
    ) {
        double value = 0.0D;
        double normalization = 0.0D;
        double amplitude = 1.0D;
        double sampleX = x;
        double sampleZ = z;
        int safeOctaves = Math.max(1, Math.min(4, octaves));
        for (int octave = 0; octave < safeOctaves; octave++) {
            value += sample(sampleX, sampleZ, seed + octave * 0x6D2B79F5)
                    * amplitude;
            normalization += amplitude;

            double nextX = sampleX * OCTAVE_X
                    + sampleZ * OCTAVE_Z + 19.19D;
            sampleZ = sampleZ * OCTAVE_X
                    - sampleX * OCTAVE_Z - 7.73D;
            sampleX = nextX;
            amplitude *= PERSISTENCE;
        }
        return clamp((float) (value / normalization));
    }

    private static double sample(double x, double z, int seed) {
        long x0 = floor(x);
        long z0 = floor(z);
        double localX = x - x0;
        double localZ = z - z0;
        double fadeX = fade(localX);
        double fadeZ = fade(localZ);

        double nearNear = gradient(
                hash(x0, z0, seed),
                localX,
                localZ
        );
        double farNear = gradient(
                hash(x0 + 1L, z0, seed),
                localX - 1.0D,
                localZ
        );
        double nearFar = gradient(
                hash(x0, z0 + 1L, seed),
                localX,
                localZ - 1.0D
        );
        double farFar = gradient(
                hash(x0 + 1L, z0 + 1L, seed),
                localX - 1.0D,
                localZ - 1.0D
        );
        double near = lerp(nearNear, farNear, fadeX);
        double far = lerp(nearFar, farFar, fadeX);
        return lerp(near, far, fadeZ) * OUTPUT_SCALE;
    }

    private static double gradient(int hash, double x, double z) {
        return switch (hash & 7) {
            case 0 -> x;
            case 1 -> -x;
            case 2 -> z;
            case 3 -> -z;
            case 4 -> (x + z) * DIAGONAL;
            case 5 -> (-x + z) * DIAGONAL;
            case 6 -> (x - z) * DIAGONAL;
            default -> (-x - z) * DIAGONAL;
        };
    }

    private static int hash(long x, long z, int seed) {
        long value = x * 0x9E3779B97F4A7C15L
                ^ z * 0xC2B2AE3D27D4EB4FL
                ^ Integer.toUnsignedLong(seed) * 0x165667B19E3779F9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (int) value;
    }

    private static long floor(double value) {
        long truncated = (long) value;
        return value < truncated ? truncated - 1L : truncated;
    }

    private static double fade(double value) {
        return value * value * value
                * (value * (value * 6.0D - 15.0D) + 10.0D);
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static float clamp(float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }
}
