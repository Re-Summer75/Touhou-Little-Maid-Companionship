package com.laixia.maidintelligence.feature.atmosphere.client.wind;

import org.joml.Vector3f;

/**
 * Pure, allocation-free environmental wind math shared by runtime and tests.
 */
public final class EnvironmentalWindField {
    public static final float MAX_FORCE = 0.45F;

    private static final float MIN_ALTITUDE_FACTOR = 0.72F;
    private static final float MAX_ALTITUDE_FACTOR = 1.55F;
    private static final float SHELTERED_AIRFLOW = 0.06F;
    private static final float SUBMERGED_FACTOR = 0.08F;
    private static final float SMOOTHING_SECONDS = 0.38F;
    private static final float STORM_SMOOTHING_SECONDS = 0.14F;
    private static final float EXTREME_SMOOTHING_SECONDS = 0.055F;
    private static final float STORM_REFERENCE_STRENGTH = 0.25F;
    private static final float SQUALL_START_STRENGTH = 0.12F;
    private static final double DIRECTION_LENGTH_SCALE = 220.0D;
    private static final double GUST_LENGTH_SCALE = 58.0D;
    private static final double TURBULENCE_LENGTH_SCALE = 28.0D;
    private static final double STORM_TURBULENCE_LENGTH_SCALE = 24.0D;
    private static final double SQUALL_TURBULENCE_LENGTH_SCALE = 10.0D;
    private static final double WAVE_LENGTH_SCALE = 42.0D;
    private static final double WAVE_WIDTH_SCALE = 160.0D;
    private static final double BASE_ADVECTION_SPEED = 1.40D;
    private static final double RAPID_ADVECTION_SPEED = 8.00D;
    private static final double STORM_ADVECTION_SPEED = 14.0D;
    private static final double SQUALL_ADVECTION_SPEED = 42.0D;
    private static final double WAVE_ADVECTION_SPEED = 10.0D;
    private static final double SQUALL_DIRECTION_COS = 0.9063077870366499D;
    private static final double SQUALL_DIRECTION_SIN = 0.42261826174069944D;
    private static final double TWO_PI = Math.PI * 2.0D;

    private EnvironmentalWindField() {
    }

    public static float strength(
            float dimensionBase,
            float rain,
            float thunder,
            float altitudeAboveSea,
            float exposure,
            boolean submerged
    ) {
        float weather = 1.0F
                + 1.10F * saturate(rain)
                + 1.40F * saturate(thunder);
        float altitude = clamp(
                1.0F + (Float.isFinite(altitudeAboveSea)
                        ? altitudeAboveSea
                        : 0.0F) / 160.0F,
                MIN_ALTITUDE_FACTOR,
                MAX_ALTITUDE_FACTOR
        );
        float shelter = SHELTERED_AIRFLOW
                + (1.0F - SHELTERED_AIRFLOW) * saturate(exposure);
        float medium = submerged ? SUBMERGED_FACTOR : 1.0F;
        return (Float.isFinite(dimensionBase)
                ? Math.max(0.0F, dimensionBase)
                : 0.0F)
                * weather * altitude * shelter * medium;
    }

    /**
     * Samples the fully shared horizontal field used by tools and baseline
     * verification. Runtime callers may use the instance-aware overload.
     */
    public static Vector3f targetInto(
            double worldX,
            double worldZ,
            double worldTimeTicks,
            int dimensionHash,
            float strength,
            Vector3f output
    ) {
        return targetInto(
                worldX,
                worldZ,
                worldTimeTicks,
                dimensionHash,
                0,
                strength,
                output
        );
    }

    /**
     * Keeps macro-scale wind shared while a stable instance hash decorrelates
     * only rapid flutter. A zero hash preserves the fully shared field.
     */
    public static Vector3f targetInto(
            double worldX,
            double worldZ,
            double worldTimeTicks,
            int dimensionHash,
            int instanceHash,
            float strength,
            Vector3f output
    ) {
        double seconds = worldTimeTicks / 20.0D;
        float safeStrength = Float.isFinite(strength)
                ? Math.max(0.0F, strength)
                : 0.0F;
        if (safeStrength <= 1.0E-8F) {
            return output.zero();
        }
        float stormActivity = saturate(
                safeStrength / STORM_REFERENCE_STRENGTH
        );
        float squallWeight = squallWeight(safeStrength);
        int instanceVariation = mixInstanceHash(instanceHash);
        float flutterScale = instanceFlutterScale(instanceVariation);
        /*
         * Small eddies are spatially decorrelated, so every instance samples
         * the fine layers from its own eddy while the macro field is shared.
         */
        double eddyOffsetX = instanceOffset(instanceVariation, 0x2545F491);
        double eddyOffsetZ = instanceOffset(instanceVariation, 0x9E3779B1);

        double baseAngle = seedAngle(dimensionHash);
        double baseX = Math.cos(baseAngle);
        double baseZ = Math.sin(baseAngle);
        double baseAdvection = seconds * BASE_ADVECTION_SPEED;
        double rapidAdvection = seconds * RAPID_ADVECTION_SPEED;
        double stormAdvection = seconds * STORM_ADVECTION_SPEED;
        double squallAdvection = seconds * SQUALL_ADVECTION_SPEED;
        double advectedX = worldX - baseX * baseAdvection;
        double advectedZ = worldZ - baseZ * baseAdvection;
        double rapidX = worldX - baseX * rapidAdvection + eddyOffsetX;
        double rapidZ = worldZ - baseZ * rapidAdvection + eddyOffsetZ;
        double stormX = worldX - baseX * stormAdvection
                + eddyOffsetX * 0.5D;
        double stormZ = worldZ - baseZ * stormAdvection
                + eddyOffsetZ * 0.5D;
        double squallDirectionX = baseX * SQUALL_DIRECTION_COS
                - baseZ * SQUALL_DIRECTION_SIN;
        double squallDirectionZ = baseZ * SQUALL_DIRECTION_COS
                + baseX * SQUALL_DIRECTION_SIN;
        double squallX = worldX - squallDirectionX * squallAdvection
                - eddyOffsetZ;
        double squallZ = worldZ - squallDirectionZ * squallAdvection
                + eddyOffsetX;

        /*
         * Taylor's frozen-turbulence approximation: a deterministic spatial
         * noise field is carried past the observer by the prevailing wind.
         */
        float directionNoise = AdvectedGradientNoise.fractal(
                advectedX / DIRECTION_LENGTH_SCALE,
                advectedZ / DIRECTION_LENGTH_SCALE,
                dimensionHash ^ 0x632BE5AB,
                2
        );
        double meanAngle = baseAngle
                + directionNoise * 0.85D
                + instanceUnit(instanceVariation, 0x27D4EB2F) * 0.24D
                - 0.12D;
        float meanX = (float) Math.cos(meanAngle);
        float meanZ = (float) Math.sin(meanAngle);

        float gustNoise = AdvectedGradientNoise.fractal(
                advectedX / GUST_LENGTH_SCALE,
                advectedZ / GUST_LENGTH_SCALE,
                dimensionHash ^ 0x85157AF5,
                3
        );
        float turbulenceNoise = AdvectedGradientNoise.fractal(
                advectedX / TURBULENCE_LENGTH_SCALE,
                advectedZ / TURBULENCE_LENGTH_SCALE,
                dimensionHash ^ 0x9E3779B9,
                3
        );
        float rapidNoise = AdvectedGradientNoise.fractal(
                rapidX / TURBULENCE_LENGTH_SCALE,
                rapidZ / TURBULENCE_LENGTH_SCALE,
                dimensionHash ^ 0xC2B2AE35 ^ instanceVariation,
                3
        );
        float stormNoise = AdvectedGradientNoise.fractal(
                stormX / STORM_TURBULENCE_LENGTH_SCALE,
                stormZ / STORM_TURBULENCE_LENGTH_SCALE,
                dimensionHash ^ 0x27D4EB2F,
                3
        );
        float squallNoise = squallWeight > 0.0F
                ? AdvectedGradientNoise.fractal(
                        squallX / SQUALL_TURBULENCE_LENGTH_SCALE,
                        squallZ / SQUALL_TURBULENCE_LENGTH_SCALE,
                        dimensionHash ^ 0xA24BAED5
                                ^ Integer.rotateLeft(instanceVariation, 13),
                        3
                )
                : 0.0F;
        double waveAlong = (
                worldX * baseX + worldZ * baseZ
                        - seconds * WAVE_ADVECTION_SPEED
        ) / WAVE_LENGTH_SCALE;
        double waveAcross = (
                -worldX * baseZ + worldZ * baseX
        ) / WAVE_WIDTH_SCALE;
        float waveNoise = AdvectedGradientNoise.fractal(
                waveAlong,
                waveAcross,
                dimensionHash ^ 0x165667B1,
                2
        );
        float stormWeight = stormActivity * stormActivity;
        // A separate fixed-speed layer avoids weather-dependent phase jumps.
        // Its nonlinear blend reserves rapid flutter for severe wind only.
        float wavePulse = smoothstep01(0.5F + waveNoise * 1.30F);
        float centeredWave = wavePulse * 2.0F - 1.0F;
        float gustEnvelope = smoothstep01(0.5F + gustNoise * 1.15F);
        float forwardGust = clamp(
                0.18F
                        + (0.72F + 0.18F * stormActivity) * gustEnvelope
                        + turbulenceNoise * 0.08F
                        + rapidNoise * 0.22F * flutterScale
                        + stormNoise * 0.34F * stormWeight
                        + squallNoise * 0.24F * squallWeight * flutterScale
                        + centeredWave * (0.24F + 0.38F * stormWeight),
                0.06F,
                1.55F
        );
        float lateralCarrier = turbulenceNoise * 0.45F
                + rapidNoise * 0.72F * flutterScale
                + stormNoise * 1.35F * stormWeight
                + squallNoise * 1.15F * squallWeight * flutterScale;
        float lateralGust = lateralCarrier
                * (0.72F + wavePulse * 0.56F);
        float forward = safeStrength
                * forwardGust;
        float lateral = safeStrength * lateralGust;
        float rawX = meanX * forward - meanZ * lateral;
        float rawZ = meanZ * forward + meanX * lateral;
        float rawMagnitude = (float) Math.sqrt(
                rawX * rawX + rawZ * rawZ
        );
        if (rawMagnitude <= 1.0E-12F) {
            return output.zero();
        }
        float limitedMagnitude = softLimit(rawMagnitude);
        float limitScale = limitedMagnitude / rawMagnitude;
        return output.set(
                rawX * limitScale,
                0.0F,
                rawZ * limitScale
        );
    }

    public static Vector3f smoothInto(
            Vector3f current,
            Vector3f target,
            float dt,
            Vector3f output
    ) {
        return smoothInto(
                current,
                target,
                dt,
                SMOOTHING_SECONDS,
                output
        );
    }

    public static Vector3f smoothInto(
            Vector3f current,
            Vector3f target,
            float dt,
            float responseSeconds,
            Vector3f output
    ) {
        if (!Float.isFinite(dt) || dt <= 0.0F) {
            return output.set(current);
        }
        float safeResponse = Float.isFinite(responseSeconds)
                ? clamp(responseSeconds, 0.05F, 2.0F)
                : SMOOTHING_SECONDS;
        float alpha = 1.0F - (float) Math.exp(
                -Math.min(dt, 0.1F) / safeResponse
        );
        return output.set(current).lerp(target, alpha);
    }

    public static float smoothingSeconds(float strength) {
        return smoothingSeconds(strength, 0);
    }

    /**
     * Each body has its own aerodynamic time constant, so instances reach the
     * same gust with slightly different lag instead of moving in lockstep.
     */
    public static float smoothingSeconds(float strength, int instanceHash) {
        float activity = saturate(
                (Float.isFinite(strength) ? strength : 0.0F)
                        / STORM_REFERENCE_STRENGTH
        );
        float stormResponse = SMOOTHING_SECONDS
                + (STORM_SMOOTHING_SECONDS - SMOOTHING_SECONDS)
                * activity;
        float response = stormResponse
                + (EXTREME_SMOOTHING_SECONDS - STORM_SMOOTHING_SECONDS)
                * squallWeight(strength);
        float variation = 0.78F
                + instanceUnit(mixInstanceHash(instanceHash), 0x632BE5AB)
                * 0.44F;
        return response * variation;
    }

    private static float saturate(float value) {
        return Float.isFinite(value) ? clamp(value, 0.0F, 1.0F) : 0.0F;
    }

    private static float softLimit(float value) {
        return MAX_FORCE * (float) Math.tanh(value / MAX_FORCE);
    }

    private static float squallWeight(float strength) {
        float span = MAX_FORCE - SQUALL_START_STRENGTH;
        float activity = smoothstep01(
                ((Float.isFinite(strength) ? strength : 0.0F)
                        - SQUALL_START_STRENGTH) / span
        );
        return activity * activity;
    }

    private static int mixInstanceHash(int value) {
        if (value == 0) {
            return 0;
        }
        int mixed = value;
        mixed ^= mixed >>> 16;
        mixed *= 0x7FEB352D;
        mixed ^= mixed >>> 15;
        mixed *= 0x846CA68B;
        mixed ^= mixed >>> 16;
        return mixed;
    }

    private static float instanceFlutterScale(int mixedHash) {
        return 0.70F + instanceUnit(mixedHash, 0x85157AF5) * 0.62F;
    }

    /**
     * Deterministic per-instance value in {@code [0, 1)}. A zero hash returns
     * the neutral midpoint so the shared field stays unchanged.
     */
    private static float instanceUnit(int mixedHash, int salt) {
        if (mixedHash == 0) {
            return 0.5F;
        }
        int mixed = mixInstanceHash(mixedHash ^ salt);
        return (mixed >>> 8) * (1.0F / 16_777_216.0F);
    }

    private static double instanceOffset(int mixedHash, int salt) {
        if (mixedHash == 0) {
            return 0.0D;
        }
        return (instanceUnit(mixedHash, salt) - 0.5F) * 1_280.0D;
    }

    private static double seedAngle(int seed) {
        int mixed = seed;
        mixed ^= mixed >>> 16;
        mixed *= 0x7FEB352D;
        mixed ^= mixed >>> 15;
        mixed *= 0x846CA68B;
        mixed ^= mixed >>> 16;
        return Integer.toUnsignedLong(mixed)
                * (TWO_PI / 4_294_967_296.0D);
    }

    private static float smoothstep01(float value) {
        float clamped = saturate(value);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
