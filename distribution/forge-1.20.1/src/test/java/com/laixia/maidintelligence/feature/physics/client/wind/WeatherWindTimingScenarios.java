package com.laixia.maidintelligence.feature.physics.client.wind;

import com.laixia.maidintelligence.feature.atmosphere.application.EnvironmentalWindField;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.EnvironmentalWindVerificationFacade.require;
import static com.laixia.maidintelligence.feature.physics.client.EnvironmentalWindVerificationFacade.requireNear;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.WIND_VECTORS;

public final class WeatherWindTimingScenarios {
    private WeatherWindTimingScenarios() {
    }

    public static void run() {
        verifiesSmoothingAndSoftLimit();
        verifiesWeatherResponsiveTiming();
        verifiesStrongWindAddsHighFrequencyTurbulence();
        verifiesExtremeWindAddsSquallFrequency();
        verifiesFilteredCalmWindRemainsDynamic();
        verifiesFrameRateConsistency();
    }

    private static void verifiesSmoothingAndSoftLimit() {
        Vector3f current = new Vector3f();
        Vector3f target = new Vector3f(
                EnvironmentalWindField.MAX_FORCE,
                0.0F,
                0.0F
        );
        EnvironmentalWindField.smoothInto(
                current,
                target,
                0.0F,
                current,
                WIND_VECTORS
        );
        requireNear(current.x, 0.0F, 0.0F, "dt=0 advanced wind smoothing");

        float previous = 0.0F;
        for (int frame = 0; frame < 180; frame++) {
            EnvironmentalWindField.smoothInto(
                    current,
                    target,
                    1.0F / 60.0F,
                    current,
                    WIND_VECTORS
            );
            require(
                    current.x >= previous
                            && current.x <= EnvironmentalWindField.MAX_FORCE,
                    "Wind smoothing overshot or reversed"
            );
            previous = current.x;
        }
        require(
                current.x > EnvironmentalWindField.MAX_FORCE * 0.98F,
                "Wind smoothing did not converge"
        );

        EnvironmentalWindField.targetInto(
                0.0D, 0.0D, 0.0D, 0, 100.0F,
                target, WIND_VECTORS
        );
        require(
                target.length() <= EnvironmentalWindField.MAX_FORCE
                        + 1.0E-6F,
                "Extreme environment input bypassed wind limiting"
        );
    }

    private static void verifiesWeatherResponsiveTiming() {
        float calmResponse = EnvironmentalWindField.smoothingSeconds(0.03F);
        float stormResponse = EnvironmentalWindField.smoothingSeconds(0.25F);
        float extremeResponse = EnvironmentalWindField.smoothingSeconds(
                EnvironmentalWindField.MAX_FORCE
        );
        require(
                stormResponse < calmResponse
                        && extremeResponse < stormResponse
                        && extremeResponse <= 0.06F,
                "Strong wind did not gain a faster response time"
        );
    }

    private static void verifiesStrongWindAddsHighFrequencyTurbulence() {
        float calmVariation = filteredTurbulenceVariation(0.075F);
        float stormVariation = filteredTurbulenceVariation(0.25F);
        require(
                stormVariation > calmVariation * 1.50F,
                "Strong wind did not add higher-frequency turbulence: "
                        + calmVariation + " / " + stormVariation
        );
    }

    private static void verifiesExtremeWindAddsSquallFrequency() {
        float stormVariation = filteredTurbulenceVariation(0.25F);
        float extremeVariation = filteredTurbulenceVariation(0.44F);
        require(
                extremeVariation > stormVariation * 1.35F,
                "Maximum wind did not enter the rapid squall regime: "
                        + stormVariation + " / " + extremeVariation
        );
    }

    private static float filteredTurbulenceVariation(float strength) {
        Vector3f target = new Vector3f();
        Vector3f filtered = new Vector3f();
        Vector3f normalized = new Vector3f();
        Vector3f previous = new Vector3f();
        Vector3f delta = new Vector3f();
        Vector3f previousDelta = new Vector3f();
        float response = EnvironmentalWindField.smoothingSeconds(strength);
        float variation = 0.0F;
        int warmupFrames = 300;
        int totalFrames = 1_800;
        for (int frame = 0; frame < totalFrames; frame++) {
            EnvironmentalWindField.targetInto(
                    32.0D,
                    -18.0D,
                    48_000.0D + frame / 3.0D,
                    771,
                    strength,
                    target,
                    WIND_VECTORS
            );
            EnvironmentalWindField.smoothInto(
                    filtered,
                    target,
                    1.0F / 60.0F,
                    response,
                    filtered,
                    WIND_VECTORS
            );
            normalized.set(filtered).div(strength);
            if (frame == warmupFrames) {
                previous.set(normalized);
                previousDelta.zero();
            } else if (frame > warmupFrames) {
                delta.set(normalized).sub(previous);
                if (frame > warmupFrames + 1) {
                    variation += delta.distance(previousDelta);
                }
                previousDelta.set(delta);
                previous.set(normalized);
            }
        }
        return variation;
    }

    private static void verifiesFilteredCalmWindRemainsDynamic() {
        Vector3f target = new Vector3f();
        Vector3f filtered = new Vector3f();
        Vector3f previous = new Vector3f();
        float strength = 0.075F;
        float response = EnvironmentalWindField.smoothingSeconds(strength);
        float travelled = 0.0F;
        int warmupFrames = 300;
        int totalFrames = 1_800;
        for (int frame = 0; frame < totalFrames; frame++) {
            EnvironmentalWindField.targetInto(
                    32.0D,
                    -18.0D,
                    48_000.0D + frame / 3.0D,
                    771,
                    strength,
                    target,
                    WIND_VECTORS
            );
            EnvironmentalWindField.smoothInto(
                    filtered,
                    target,
                    1.0F / 60.0F,
                    response,
                    filtered,
                    WIND_VECTORS
            );
            if (frame == warmupFrames) {
                previous.set(filtered);
            } else if (frame > warmupFrames) {
                travelled += filtered.distance(previous);
                previous.set(filtered);
            }
        }
        require(
                travelled > 0.18F,
                "Filtered calm wind collapsed into a static pose: "
                        + travelled
        );
    }

    private static void verifiesFrameRateConsistency() {
        Vector3f atThirty = simulateField(30);
        Vector3f atSixty = simulateField(60);
        Vector3f atOneTwenty = simulateField(120);
        require(
                atThirty.distance(atSixty) < 0.0015F
                        && atOneTwenty.distance(atSixty) < 0.0010F,
                "Continuous wind changed materially with render frame rate: "
                        + atThirty + " / " + atSixty + " / " + atOneTwenty
        );
    }

    private static Vector3f simulateField(int framesPerSecond) {
        Vector3f current = new Vector3f();
        Vector3f target = new Vector3f();
        float dt = 1.0F / framesPerSecond;
        for (int frame = 1; frame <= framesPerSecond * 4; frame++) {
            double worldTicks = frame * 20.0D / framesPerSecond;
            EnvironmentalWindField.targetInto(
                    40.0D,
                    -24.0D,
                    worldTicks,
                    1771,
                    0.06F,
                    target,
                    WIND_VECTORS
            );
            EnvironmentalWindField.smoothInto(
                    current,
                    target,
                    dt,
                    current,
                    WIND_VECTORS
            );
        }
        return current;
    }
}
