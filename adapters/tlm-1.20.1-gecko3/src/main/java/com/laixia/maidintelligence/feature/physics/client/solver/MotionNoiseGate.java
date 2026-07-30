package com.laixia.maidintelligence.feature.physics.client.solver;

import org.joml.Vector3f;

/**
 * Radial soft dead zone plus a continuously differentiable saturation curve.
 */
public final class MotionNoiseGate {
    private static final float EPSILON = 1.0E-6F;

    private MotionNoiseGate() {
    }

    public static Vector3f vector(
            Vector3f input,
            float maximum,
            float noiseFloor,
            float noiseFade
    ) {
        return vectorInto(
                input,
                maximum,
                noiseFloor,
                noiseFade,
                new Vector3f()
        );
    }

    public static Vector3f vectorInto(
            Vector3f input,
            float maximum,
            float noiseFloor,
            float noiseFade,
            Vector3f output
    ) {
        float length = input.length();
        if (length < EPSILON || maximum <= EPSILON) {
            return output.zero();
        }
        float limited = softLimit(length, maximum);
        float gain = deadZoneGain(limited, noiseFloor, noiseFade);
        return output.set(input).mul(limited * gain / length);
    }

    public static float scalar(
            float input,
            float maximum,
            float noiseFloor,
            float noiseFade
    ) {
        float magnitude = Math.abs(input);
        if (magnitude < EPSILON || maximum <= EPSILON) {
            return 0.0F;
        }
        float limited = softLimit(magnitude, maximum);
        return Math.copySign(
                limited * deadZoneGain(limited, noiseFloor, noiseFade),
                input
        );
    }

    private static float softLimit(float value, float maximum) {
        float knee = maximum * 0.70F;
        if (value <= knee) {
            return value;
        }
        float range = maximum - knee;
        return knee + range * (float) Math.tanh((value - knee) / range);
    }

    private static float deadZoneGain(
            float value,
            float noiseFloor,
            float noiseFade
    ) {
        if (value <= noiseFloor) {
            return 0.0F;
        }
        if (noiseFade <= EPSILON || value >= noiseFloor + noiseFade) {
            return 1.0F;
        }
        float ratio = (value - noiseFloor) / noiseFade;
        return ratio * ratio * (3.0F - 2.0F * ratio);
    }
}
