package com.laixia.maidintelligence.feature.physics.client.wind;

import com.laixia.maidintelligence.feature.atmosphere.application.EnvironmentalWindField;
import com.laixia.maidintelligence.feature.atmosphere.domain.DimensionWindProfile;
import com.laixia.maidintelligence.feature.physics.client.pose.WorldPoseDriverSources;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.EnvironmentalWindVerificationFacade.require;
import static com.laixia.maidintelligence.feature.physics.client.EnvironmentalWindVerificationFacade.requireNear;
import static com.laixia.maidintelligence.feature.physics.client.EnvironmentalWindVerificationFacade.requireVectorNear;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.WIND_VECTORS;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.correlation;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.highPass;

public final class DeterministicWindFieldScenarios {
    private DeterministicWindFieldScenarios() {
    }

    public static void run() {
        verifiesPhysicsFallbackWithoutAtmosphere();
        verifiesEnvironmentStrengthOrdering();
        verifiesCoherentDeterministicField();
        verifiesInstanceFlutterPreservesMacroCoherence();
        verifiesNoiseDoesNotTileOverTime();
        verifiesTurbulenceAlternatesAroundMeanWind();
        verifiesGustFrontsCreatePressureWaves();
        verifiesWeatherChangeDoesNotJumpPhase();
    }

    private static void verifiesPhysicsFallbackWithoutAtmosphere() {
        Vector3f output = new Vector3f(1.0F, 2.0F, 3.0F);
        WorldPoseDriverSources.create().sampleInto(
                null,
                0.0D,
                1.0F / 60.0F,
                false,
                output
        );
        require(
                output.lengthSquared() == 0.0F,
                "Physics core did not fall back to an empty pose driver"
        );
    }

    private static void verifiesEnvironmentStrengthOrdering() {
        float base = DimensionWindProfile.baseStrength(
                "minecraft:overworld"
        );
        float calm = EnvironmentalWindField.strength(
                base, 0.0F, 0.0F, 0.0F, 1.0F, false
        );
        float rain = EnvironmentalWindField.strength(
                base, 1.0F, 0.0F, 0.0F, 1.0F, false
        );
        float thunder = EnvironmentalWindField.strength(
                base, 1.0F, 1.0F, 0.0F, 1.0F, false
        );
        float high = EnvironmentalWindField.strength(
                base, 0.0F, 0.0F, 96.0F, 1.0F, false
        );
        float indoors = EnvironmentalWindField.strength(
                base, 0.0F, 0.0F, 0.0F, 0.0F, false
        );
        float submerged = EnvironmentalWindField.strength(
                base, 0.0F, 0.0F, 0.0F, 1.0F, true
        );
        float nether = DimensionWindProfile.baseStrength(
                "minecraft:the_nether"
        );
        float end = DimensionWindProfile.baseStrength(
                "minecraft:the_end"
        );

        require(
                thunder > 0.20F
                        && thunder > rain
                        && rain > calm
                        && high > calm,
                "Weather or altitude did not strengthen environmental wind"
        );
        require(
                indoors > 0.0F && indoors < calm * 0.10F,
                "Shelter did not retain only weak indoor airflow"
        );
        require(
                submerged > 0.0F && submerged < calm * 0.10F,
                "Submersion did not strongly attenuate wind"
        );
        require(
                nether < base && end > base,
                "Dimension base strengths lost their intended ordering"
        );
    }

    private static void verifiesCoherentDeterministicField() {
        Vector3f first = new Vector3f();
        Vector3f same = new Vector3f();
        Vector3f nearby = new Vector3f();
        EnvironmentalWindField.targetInto(
                120.0D, -80.0D, 24_000.25D, 913, 0.05F,
                first, WIND_VECTORS
        );
        EnvironmentalWindField.targetInto(
                120.0D, -80.0D, 24_000.25D, 913, 0.05F,
                same, WIND_VECTORS
        );
        EnvironmentalWindField.targetInto(
                122.0D, -79.0D, 24_000.25D, 913, 0.05F,
                nearby, WIND_VECTORS
        );

        requireVectorNear(
                same,
                first,
                0.0F,
                "Equal world samples produced different wind"
        );
        require(
                first.distance(nearby) < 0.003F,
                "Nearby entities did not share a coherent wind field"
        );
        requireNear(first.y, 0.0F, 0.0F, "Wind gained a vertical component");
        require(
                first.length() <= EnvironmentalWindField.MAX_FORCE
                        + 1.0E-6F,
                "Wind field exceeded its force limit"
        );
    }

    private static void verifiesInstanceFlutterPreservesMacroCoherence() {
        Vector3f first = new Vector3f();
        Vector3f repeated = new Vector3f();
        Vector3f second = new Vector3f();
        Vector3f firstMean = new Vector3f();
        Vector3f secondMean = new Vector3f();
        int samples = 240;
        float[] firstLateral = new float[samples];
        float[] secondLateral = new float[samples];
        float accumulatedDifference = 0.0F;
        for (int sample = 0; sample < samples; sample++) {
            double time = 24_000.25D + sample * 2.0D;
            EnvironmentalWindField.targetInto(
                    120.0D, -80.0D, time, 913, 1171, 0.25F,
                    first, WIND_VECTORS
            );
            EnvironmentalWindField.targetInto(
                    120.0D, -80.0D, time, 913, 3571, 0.25F,
                    second, WIND_VECTORS
            );
            firstLateral[sample] = first.x;
            secondLateral[sample] = second.x;
            firstMean.add(first);
            secondMean.add(second);
            accumulatedDifference += first.distance(second);
        }
        // The macro gust is shared on purpose; only the fast fluctuation
        // around it has to decorrelate between separate bodies.
        float flutterCorrelation = correlation(
                highPass(firstLateral),
                highPass(secondLateral)
        );
        require(
                Math.abs(flutterCorrelation) < 0.60F,
                "Instances stayed correlated inside the same eddy: "
                        + flutterCorrelation
        );
        EnvironmentalWindField.targetInto(
                120.0D, -80.0D, 24_000.25D,
                913, 1171, 0.25F, repeated, WIND_VECTORS
        );
        EnvironmentalWindField.targetInto(
                120.0D, -80.0D, 24_000.25D,
                913, 1171, 0.25F, first, WIND_VECTORS
        );
        requireVectorNear(
                repeated,
                first,
                0.0F,
                "Stable instance hash produced nondeterministic flutter"
        );
        require(
                accumulatedDifference > 1.0F,
                "Different instances remained locked to one wind pose"
        );
        require(
                firstMean.normalize().dot(secondMean.normalize()) > 0.75F,
                "Instance flutter destroyed the shared macro gust direction"
        );

        float sharedResponse = EnvironmentalWindField.smoothingSeconds(0.25F);
        float firstResponse = EnvironmentalWindField.smoothingSeconds(
                0.25F,
                1171
        );
        float secondResponse = EnvironmentalWindField.smoothingSeconds(
                0.25F,
                3571
        );
        require(
                Math.abs(firstResponse - secondResponse) > 0.005F
                        && firstResponse > sharedResponse * 0.70F
                        && secondResponse < sharedResponse * 1.30F,
                "Instances share one aerodynamic response time: "
                        + firstResponse + " / " + secondResponse
        );
    }

    private static void verifiesNoiseDoesNotTileOverTime() {
        Vector3f first = new Vector3f();
        Vector3f later = new Vector3f();
        float accumulatedDifference = 0.0F;
        for (int sample = 0; sample < 32; sample++) {
            double time = 32_000.0D + sample * 20.0D;
            EnvironmentalWindField.targetInto(
                    73.0D, -41.0D, time, 1771, 0.16F,
                    first, WIND_VECTORS
            );
            EnvironmentalWindField.targetInto(
                    73.0D,
                    -41.0D,
                    time + 1_000_003.0D,
                    1771,
                    0.16F,
                    later,
                    WIND_VECTORS
            );
            accumulatedDifference += first.distance(later);
        }
        require(
                accumulatedDifference > 0.10F,
                "Environmental wind repeated a distant temporal window"
        );
    }

    private static void verifiesTurbulenceAlternatesAroundMeanWind() {
        Vector3f mean = new Vector3f();
        Vector3f sample = new Vector3f();
        int samples = 80;
        for (int index = 0; index < samples; index++) {
            EnvironmentalWindField.targetInto(
                    32.0D,
                    -18.0D,
                    index * 2.0D,
                    771,
                    0.25F,
                    sample,
                    WIND_VECTORS
            );
            mean.add(sample);
        }
        mean.normalize();

        float minimumLateral = Float.POSITIVE_INFINITY;
        float maximumLateral = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < samples; index++) {
            EnvironmentalWindField.targetInto(
                    32.0D,
                    -18.0D,
                    index * 2.0D,
                    771,
                    0.25F,
                    sample,
                    WIND_VECTORS
            );
            float lateral = mean.x * sample.z - mean.z * sample.x;
            minimumLateral = Math.min(minimumLateral, lateral);
            maximumLateral = Math.max(maximumLateral, lateral);
        }
        require(
                minimumLateral < -0.02F && maximumLateral > 0.02F,
                "Wind field collapsed into a static one-direction force"
        );
    }

    private static void verifiesGustFrontsCreatePressureWaves() {
        Vector3f target = new Vector3f();
        Vector3f filtered = new Vector3f();
        float strength = 0.16F;
        float response = EnvironmentalWindField.smoothingSeconds(strength);
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (int frame = 0; frame < 1_800; frame++) {
            EnvironmentalWindField.targetInto(
                    12.0D,
                    37.0D,
                    72_000.0D + frame / 3.0D,
                    913,
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
            if (frame >= 300) {
                float pressure = filtered.length();
                minimum = Math.min(minimum, pressure);
                maximum = Math.max(maximum, pressure);
            }
        }
        require(
                maximum - minimum > 0.035F,
                "Advected gust fronts lost their pressure-wave envelope: "
                        + minimum + " / " + maximum
        );
    }

    private static void verifiesWeatherChangeDoesNotJumpPhase() {
        Vector3f before = new Vector3f();
        Vector3f after = new Vector3f();
        EnvironmentalWindField.targetInto(
                18.0D,
                42.0D,
                24_000_000.0D,
                991,
                0.1249F,
                before,
                WIND_VECTORS
        );
        EnvironmentalWindField.targetInto(
                18.0D,
                42.0D,
                24_000_000.0D,
                991,
                0.1251F,
                after,
                WIND_VECTORS
        );
        require(
                before.distance(after) < 0.001F,
                "A small weather change jumped the wind phase"
        );
    }
}
