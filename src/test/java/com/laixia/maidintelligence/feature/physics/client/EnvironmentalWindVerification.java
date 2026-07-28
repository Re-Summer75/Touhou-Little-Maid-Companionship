package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.atmosphere.client.wind.EnvironmentalWindField;
import com.laixia.maidintelligence.feature.atmosphere.client.wind.EnvironmentalWindSampler;
import com.laixia.maidintelligence.feature.physics.client.pose.PoseDriveGustFilter;
import com.laixia.maidintelligence.feature.physics.client.pose.WorldPoseDriverSources;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SpringBoneSolver;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireNear;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireVectorNear;

final class EnvironmentalWindVerification {
    private static final Vector3f ZERO = new Vector3f();

    private EnvironmentalWindVerification() {
    }

    static void run() {
        verifiesPhysicsFallbackWithoutAtmosphere();
        verifiesEnvironmentStrengthOrdering();
        verifiesCoherentDeterministicField();
        verifiesInstanceFlutterPreservesMacroCoherence();
        verifiesNoiseDoesNotTileOverTime();
        verifiesTurbulenceAlternatesAroundMeanWind();
        verifiesGustFrontsCreatePressureWaves();
        verifiesWeatherChangeDoesNotJumpPhase();
        verifiesSmoothingAndSoftLimit();
        verifiesWeatherResponsiveTiming();
        verifiesStrongWindAddsHighFrequencyTurbulence();
        verifiesExtremeWindAddsSquallFrequency();
        verifiesFilteredCalmWindRemainsDynamic();
        verifiesFrameRateConsistency();
        verifiesPartResponseProfiles();
        verifiesLegacyNoWindCompatibility();
        verifiesWindDoesNotDisplaceTheInitialPose();
        verifiesSteadyWindDoesNotBecomeThePose();
        verifiesBoneReturnsToRestAfterWindStops();
        verifiesWindDrivesAnimationPose();
        verifiesMassControlsWindResponse();
        verifiesBonesDoNotFlutterInLockstep();
        verifiesRepeatedRenderHoldsTurbulencePhase();
        verifiesSkirtWindAngleIsBounded();
        verifiesChainRipplesAlongItsLength();
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
        float base = EnvironmentalWindSampler.dimensionBase(
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
        float nether = EnvironmentalWindSampler.dimensionBase(
                "minecraft:the_nether"
        );
        float end = EnvironmentalWindSampler.dimensionBase(
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
                120.0D, -80.0D, 24_000.25D, 913, 0.05F, first
        );
        EnvironmentalWindField.targetInto(
                120.0D, -80.0D, 24_000.25D, 913, 0.05F, same
        );
        EnvironmentalWindField.targetInto(
                122.0D, -79.0D, 24_000.25D, 913, 0.05F, nearby
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
                    120.0D, -80.0D, time, 913, 1171, 0.25F, first
            );
            EnvironmentalWindField.targetInto(
                    120.0D, -80.0D, time, 913, 3571, 0.25F, second
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
                913, 1171, 0.25F, repeated
        );
        EnvironmentalWindField.targetInto(
                120.0D, -80.0D, 24_000.25D,
                913, 1171, 0.25F, first
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

    private static float[] highPass(float[] samples) {
        float[] differences = new float[samples.length - 1];
        for (int index = 0; index < differences.length; index++) {
            differences[index] = samples[index + 1] - samples[index];
        }
        return differences;
    }

    private static float correlation(float[] first, float[] second) {
        float firstMean = 0.0F;
        float secondMean = 0.0F;
        for (int index = 0; index < first.length; index++) {
            firstMean += first[index];
            secondMean += second[index];
        }
        firstMean /= first.length;
        secondMean /= second.length;
        float covariance = 0.0F;
        float firstVariance = 0.0F;
        float secondVariance = 0.0F;
        for (int index = 0; index < first.length; index++) {
            float a = first[index] - firstMean;
            float b = second[index] - secondMean;
            covariance += a * b;
            firstVariance += a * a;
            secondVariance += b * b;
        }
        float denominator = (float) Math.sqrt(
                firstVariance * secondVariance
        );
        return denominator <= 1.0E-12F ? 0.0F : covariance / denominator;
    }

    private static void verifiesNoiseDoesNotTileOverTime() {
        Vector3f first = new Vector3f();
        Vector3f later = new Vector3f();
        float accumulatedDifference = 0.0F;
        for (int sample = 0; sample < 32; sample++) {
            double time = 32_000.0D + sample * 20.0D;
            EnvironmentalWindField.targetInto(
                    73.0D, -41.0D, time, 1771, 0.16F, first
            );
            EnvironmentalWindField.targetInto(
                    73.0D,
                    -41.0D,
                    time + 1_000_003.0D,
                    1771,
                    0.16F,
                    later
            );
            accumulatedDifference += first.distance(later);
        }
        require(
                accumulatedDifference > 0.10F,
                "Environmental wind repeated a distant temporal window"
        );
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
                current
        );
        requireNear(current.x, 0.0F, 0.0F, "dt=0 advanced wind smoothing");

        float previous = 0.0F;
        for (int frame = 0; frame < 180; frame++) {
            EnvironmentalWindField.smoothInto(
                    current,
                    target,
                    1.0F / 60.0F,
                    current
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
                0.0D, 0.0D, 0.0D, 0, 100.0F, target
        );
        require(
                target.length() <= EnvironmentalWindField.MAX_FORCE
                        + 1.0E-6F,
                "Extreme environment input bypassed wind limiting"
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
                    sample
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
                    sample
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
                    target
            );
            EnvironmentalWindField.smoothInto(
                    filtered,
                    target,
                    1.0F / 60.0F,
                    response,
                    filtered
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
                before
        );
        EnvironmentalWindField.targetInto(
                18.0D,
                42.0D,
                24_000_000.0D,
                991,
                0.1251F,
                after
        );
        require(
                before.distance(after) < 0.001F,
                "A small weather change jumped the wind phase"
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
                    target
            );
            EnvironmentalWindField.smoothInto(
                    filtered,
                    target,
                    1.0F / 60.0F,
                    response,
                    filtered
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
                    target
            );
            EnvironmentalWindField.smoothInto(
                    filtered,
                    target,
                    1.0F / 60.0F,
                    response,
                    filtered
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
                    target
            );
            EnvironmentalWindField.smoothInto(
                    current,
                    target,
                    dt,
                    current
            );
        }
        return current;
    }

    private static void verifiesPartResponseProfiles() {
        float head = profile(PhysicsBoneSelectionPlan.PartType.HEAD_SHELL);
        float hair = profile(PhysicsBoneSelectionPlan.PartType.HAIR);
        float tail = profile(PhysicsBoneSelectionPlan.PartType.TAIL);
        float ear = profile(PhysicsBoneSelectionPlan.PartType.EAR);
        float skirt = profile(PhysicsBoneSelectionPlan.PartType.SKIRT);
        float wing = profile(PhysicsBoneSelectionPlan.PartType.WING);
        float skirtGravity = PhysicsBoneSelectionPlan.SpringProfile.defaults(
                PhysicsBoneSelectionPlan.PartType.SKIRT
        ).gravityScale();
        float skirtMass = PhysicsBoneSelectionPlan.SpringProfile.defaults(
                PhysicsBoneSelectionPlan.PartType.SKIRT
        ).massScale();
        float hairMass = PhysicsBoneSelectionPlan.SpringProfile.defaults(
                PhysicsBoneSelectionPlan.PartType.HAIR
        ).massScale();
        float ribbonMass = PhysicsBoneSelectionPlan.SpringProfile.defaults(
                PhysicsBoneSelectionPlan.PartType.RIBBON
        ).massScale();
        float skirtInward = PhysicsBoneSelectionPlan.ConstraintProfile.defaults(
                PhysicsBoneSelectionPlan.PartType.SKIRT
        ).swingLimits().inward();
        require(
                head == 0.0F
                        && tail > hair
                        && hair > ear
                        && skirt > wing,
                "Default part wind response ordering is unsafe"
        );
        require(
                skirtGravity > 1.0F,
                "Skirt wind response lost its physical weight"
        );
        require(
                skirtMass > 1.0F
                        && hairMass < 1.0F
                        && ribbonMass < hairMass,
                "Default part mass ordering is not physically useful"
        );
        require(
                skirtInward <= Math.toRadians(8.1D),
                "Skirt wind response lost its inward backstop"
        );

        PhysicsBoneSelectionPlan.SpringProfile clamped =
                new PhysicsBoneSelectionPlan.SpringProfile(
                        1.0F, 1.0F, 9.0F, 1.0F,
                        1.0F, 1.0F, 1.0F, 1.0F
                );
        requireNear(
                clamped.windScale(),
                4.0F,
                0.0F,
                "Spring profile did not clamp wind scale"
        );
    }

    private static float profile(PhysicsBoneSelectionPlan.PartType type) {
        return PhysicsBoneSelectionPlan.SpringProfile.defaults(type)
                .windScale();
    }

    private static void verifiesLegacyNoWindCompatibility() {
        Fixture legacy = createFixture("legacy");
        Fixture explicitZero = createFixture("explicit_zero");
        Vector3f acceleration = new Vector3f(0.035F, 0.0F, -0.02F);
        Vector3f legacyDirection = new Vector3f();
        Vector3f explicitDirection = new Vector3f();

        for (int frame = 0; frame < 120; frame++) {
            legacy.solver().restoreAnimationPose();
            explicitZero.solver().restoreAnimationPose();
            legacy.solver().solve(
                    acceleration,
                    0.03F,
                    1.0F / 60.0F,
                    false
            );
            explicitZero.solver().solve(
                    acceleration,
                    ZERO,
                    0.03F,
                    1.0F / 60.0F,
                    false
            );
        }
        require(
                legacy.solver().copyCurrentDirection(
                        legacy.drivenSlot(),
                        legacyDirection
                )
                        && explicitZero.solver().copyCurrentDirection(
                        explicitZero.drivenSlot(),
                        explicitDirection
                ),
                "No-wind solver direction was unavailable"
        );
        requireVectorNear(
                explicitDirection,
                legacyDirection,
                0.0F,
                "Explicit zero wind changed the legacy solve"
        );
        requireNear(
                explicitZero.tail().getRotationX(),
                legacy.tail().getRotationX(),
                0.0F,
                "Explicit zero wind changed rotation X"
        );
        requireNear(
                explicitZero.tail().getRotationZ(),
                legacy.tail().getRotationZ(),
                0.0F,
                "Explicit zero wind changed rotation Z"
        );
    }

    private static void verifiesWindDrivesAnimationPose() {
        Fixture calm = createFixture("calm");
        Fixture windy = createFixture("windy");
        Vector3f wind = new Vector3f(0.06F, 0.0F, 0.0F);
        Vector3f calmDirection = new Vector3f();
        Vector3f windyDirection = new Vector3f();

        for (int frame = 0; frame < 120; frame++) {
            calm.solver().restoreAnimationPose();
            windy.solver().restoreAnimationPose();
            calm.solver().solve(
                    ZERO,
                    ZERO,
                    0.0F,
                    1.0F / 60.0F,
                    false
            );
            windy.solver().solve(
                    ZERO,
                    wind,
                    0.0F,
                    1.0F / 60.0F,
                    false
            );
        }
        require(
                calm.solver().copyCurrentDirection(
                        calm.drivenSlot(),
                        calmDirection
                )
                        && windy.solver().copyCurrentDirection(
                        windy.drivenSlot(),
                        windyDirection
                ),
                "Wind response direction was unavailable"
        );
        require(
                windyDirection.x > calmDirection.x + 0.005F,
                "Wind did not move the spring animation target"
        );
        float renderedDifference = Math.abs(
                windy.tail().getRotationX() - calm.tail().getRotationX()
        ) + Math.abs(
                windy.tail().getRotationY() - calm.tail().getRotationY()
        ) + Math.abs(
                windy.tail().getRotationZ() - calm.tail().getRotationZ()
        );
        require(
                renderedDifference > 0.005F,
                "Wind pose target was not written to the bone"
        );
    }

    /**
     * A bone that enters the solver while a gust is already blowing must start
     * where the animation put it. Wind is a lean added to the spring, not a
     * relocation of what the spring pulls toward, so a frame carrying no time
     * carries no wind either.
     */
    private static void verifiesWindDoesNotDisplaceTheInitialPose() {
        Fixture fixture = createFixture("zero_step_pose");
        Vector3f gust = new Vector3f(0.20F, 0.0F, 0.0F);
        float initialX = fixture.tail().getRotationX();
        float initialY = fixture.tail().getRotationY();
        float initialZ = fixture.tail().getRotationZ();
        fixture.solver().solve(ZERO, gust, 0.0F, 0.0F, false);
        require(
                fixture.tail().getRotationX() == initialX
                        && fixture.tail().getRotationY() == initialY
                        && fixture.tail().getRotationZ() == initialZ,
                "Wind displaced the authored pose on a zero-time frame"
        );
        for (int frame = 0; frame < 120; frame++) {
            fixture.solver().restoreAnimationPose();
            fixture.solver().solve(ZERO, gust, 0.0F, 1.0F / 60.0F, false);
        }
        float leaned = Math.abs(fixture.tail().getRotationX() - initialX)
                + Math.abs(fixture.tail().getRotationY() - initialY)
                + Math.abs(fixture.tail().getRotationZ() - initialZ);
        require(
                leaned > 0.005F,
                "Wind never leaned the bone once time advanced: " + leaned
        );
    }

    /**
     * The authored pose stays the equilibrium: once the gust drops the spring
     * has to pull the bone back on its own, which a relocated rest target
     * could never do.
     */
    private static void verifiesBoneReturnsToRestAfterWindStops() {
        Fixture fixture = createFixture("wind_release");
        Vector3f gust = new Vector3f(0.20F, 0.0F, 0.0F);
        Vector3f authored = new Vector3f();
        Vector3f leaned = new Vector3f();
        Vector3f released = new Vector3f();
        fixture.solver().solve(ZERO, ZERO, 0.0F, 0.0F, false);
        require(
                fixture.solver().copyCurrentDirection(
                        fixture.drivenSlot(),
                        authored
                ),
                "Wind release direction was unavailable"
        );
        for (int frame = 0; frame < 120; frame++) {
            fixture.solver().restoreAnimationPose();
            fixture.solver().solve(ZERO, gust, 0.0F, 1.0F / 60.0F, false);
        }
        fixture.solver().copyCurrentDirection(
                fixture.drivenSlot(),
                leaned
        );
        for (int frame = 0; frame < 240; frame++) {
            fixture.solver().restoreAnimationPose();
            fixture.solver().solve(ZERO, ZERO, 0.0F, 1.0F / 60.0F, false);
        }
        fixture.solver().copyCurrentDirection(
                fixture.drivenSlot(),
                released
        );
        float leanedAngle = angleBetween(authored, leaned);
        float releasedAngle = angleBetween(authored, released);
        require(
                leanedAngle > 0.01F && releasedAngle < leanedAngle * 0.05F,
                "Bone did not return to the authored pose after the gust: "
                        + leanedAngle + " -> " + releasedAngle
        );
    }

    /**
     * A breeze that never lets up must not become part of the silhouette. The
     * filter keeps the gusts and drops whatever is held for several seconds,
     * so a maid standing in steady weather reads as the pose her author built.
     */
    private static void verifiesSteadyWindDoesNotBecomeThePose() {
        PoseDriveGustFilter filter = new PoseDriveGustFilter();
        Vector3f signal = new Vector3f();
        Vector3f steady = new Vector3f(0.30F, 0.0F, -0.12F);
        signal.set(steady);
        filter.isolateGust(signal, 0.0F, false);
        require(
                signal.lengthSquared() == 0.0F,
                "The first sample leaned before any weather had passed"
        );
        for (int frame = 0; frame < 1200; frame++) {
            signal.set(steady);
            filter.isolateGust(signal, 1.0F / 60.0F, false);
        }
        require(
                signal.length() < steady.length() * 0.02F,
                "A sustained breeze survived as a permanent lean: " + signal
        );
        float gustPeak = 0.0F;
        for (int frame = 0; frame < 600; frame++) {
            float seconds = frame / 60.0F;
            signal.set(steady).mul(
                    1.0F + 0.8F * (float) Math.sin(
                            2.0D * Math.PI * 0.7D * seconds
                    )
            );
            filter.isolateGust(signal, 1.0F / 60.0F, false);
            gustPeak = Math.max(gustPeak, signal.length());
        }
        require(
                gustPeak > steady.length() * 0.5F,
                "Gusts were filtered away along with the steady wind: "
                        + gustPeak
        );
    }

    private static float angleBetween(Vector3f left, Vector3f right) {
        return (float) Math.acos(
                Math.max(-1.0F, Math.min(1.0F, left.dot(right)))
        );
    }

    /**
     * The lean settles at the safety ceiling instead of overshooting it, so
     * this has to be measured after the spring converges rather than on a
     * single frame. Measuring against a calm fixture rather than against the
     * authored axis keeps the reading to the wind's own contribution whatever
     * else settles the segment.
     */
    private static void verifiesSkirtWindAngleIsBounded() {
        Fixture rest = createFixture("skirt_rest", "SKIRT");
        Fixture windy = createFixture("skirt_bounded", "SKIRT");
        Vector3f restDirection = new Vector3f();
        Vector3f windyDirection = new Vector3f();
        Vector3f gale = new Vector3f(100.0F, 0.0F, 0.0F);
        for (int frame = 0; frame < 240; frame++) {
            rest.solver().restoreAnimationPose();
            windy.solver().restoreAnimationPose();
            rest.solver().solve(ZERO, ZERO, 0.0F, 1.0F / 60.0F, false);
            windy.solver().solve(ZERO, gale, 0.0F, 1.0F / 60.0F, false);
        }
        require(
                rest.solver().copyCurrentDirection(
                        rest.drivenSlot(),
                        restDirection
                )
                        && windy.solver().copyCurrentDirection(
                        windy.drivenSlot(),
                        windyDirection
                ),
                "Bounded skirt wind direction was unavailable"
        );
        float angle = angleBetween(restDirection, windyDirection);
        require(
                angle > 0.17F && angle <= 0.23F,
                "Skirt wind angle escaped its safety ceiling: " + angle
        );
    }

    private static void verifiesMassControlsWindResponse() {
        float lightSwing = gustTrackingSwing(0.50F);
        float heavySwing = gustTrackingSwing(2.00F);
        require(
                lightSwing > heavySwing * 1.20F,
                "Mass did not low-pass the fast gust: "
                        + lightSwing + " / " + heavySwing
        );
    }

    /**
     * Peak-to-peak swing while a fast gust oscillates. A heavier part must
     * track less of it, which is the visible meaning of mass.
     */
    private static float gustTrackingSwing(float massScale) {
        Fixture fixture = createFixture(
                "gust_" + Float.toString(massScale).replace('.', '_'),
                "TAIL",
                massScale
        );
        Vector3f wind = new Vector3f();
        Vector3f direction = new Vector3f();
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        int warmupFrames = 120;
        for (int frame = 0; frame < 480; frame++) {
            float seconds = frame / 60.0F;
            wind.set(
                    0.18F * (float) Math.sin(
                            2.0D * Math.PI * 2.0D * seconds
                    ),
                    0.0F,
                    0.0F
            );
            fixture.solver().restoreAnimationPose();
            fixture.solver().solve(
                    ZERO, wind, 0.0F, 1.0F / 60.0F, false
            );
            if (frame < warmupFrames) {
                continue;
            }
            require(
                    fixture.solver().copyCurrentDirection(
                            fixture.drivenSlot(),
                            direction
                    ),
                    "Gust tracking direction was unavailable"
            );
            minimum = Math.min(minimum, direction.x);
            maximum = Math.max(maximum, direction.x);
        }
        return maximum - minimum;
    }

    private static void verifiesBonesDoNotFlutterInLockstep() {
        require(
            siblingDivergence(new Vector3f(0.22F, 0.0F, 0.0F)) > 0.02F,
            "Identical bones flutter in lockstep under the same wind"
        );
        require(
            siblingDivergence(ZERO) <= 1.0E-6F,
            "Bones diverged without any atmosphere signal"
        );
    }

    private static float siblingDivergence(Vector3f wind) {
        SiblingFixture fixture = createSiblingFixture();
        Vector3f first = new Vector3f();
        Vector3f second = new Vector3f();
        float divergence = 0.0F;
        for (int frame = 0; frame < 300; frame++) {
            fixture.solver().restoreAnimationPose();
            fixture.solver().solve(
                    ZERO, wind, 0.0F, 1.0F / 60.0F, false
            );
            if (frame < 60) {
                continue;
            }
            require(
                    fixture.solver().copyCurrentDirection(
                            fixture.firstSlot(),
                            first
                    )
                            && fixture.solver().copyCurrentDirection(
                            fixture.secondSlot(),
                            second
                    ),
                    "Sibling strand directions were unavailable"
            );
            divergence = Math.max(divergence, first.distance(second));
        }
        return divergence;
    }

    private static void verifiesRepeatedRenderHoldsTurbulencePhase() {
        Fixture fixture = createFixture("phase_hold");
        Vector3f wind = new Vector3f(0.20F, 0.0F, 0.0F);
        for (int frame = 0; frame < 30; frame++) {
            fixture.solver().restoreAnimationPose();
            fixture.solver().solve(
                    ZERO, wind, 0.0F, 1.0F / 60.0F, false
            );
        }
        fixture.solver().restoreAnimationPose();
        fixture.solver().solve(ZERO, wind, 0.0F, 0.0F, false);
        float rotationX = fixture.tail().getRotationX();
        float rotationZ = fixture.tail().getRotationZ();
        fixture.solver().restoreAnimationPose();
        fixture.solver().solve(ZERO, wind, 0.0F, 0.0F, false);
        requireNear(
                fixture.tail().getRotationX(),
                rotationX,
                0.0F,
                "Repeated render advanced the buffeting phase on X"
        );
        requireNear(
                fixture.tail().getRotationZ(),
                rotationZ,
                0.0F,
                "Repeated render advanced the buffeting phase on Z"
        );
    }

    /**
     * A disturbance crossing a surface reaches the far end of a chain after the
     * near end, so the tip must repeat the root's motion a moment later rather
     * than beside it. Both halves of that matter: shared timing (a ripple, not
     * independent jitter) and a delay (a travelling wave, not a lockstep swing).
     */
    private static void verifiesChainRipplesAlongItsLength() {
        ChainFixture fixture = createChainFixture();
        int samples = 600;
        int segments = fixture.slots().length;
        float[][] history = new float[segments][samples];
        Vector3f wind = new Vector3f(0.22F, 0.0F, 0.0F);
        Vector3f direction = new Vector3f();
        for (int frame = 0; frame < samples + 120; frame++) {
            fixture.solver().restoreAnimationPose();
            fixture.solver().solve(ZERO, wind, 0.0F, 1.0F / 60.0F, false);
            if (frame < 120) {
                continue;
            }
            for (int segment = 0; segment < segments; segment++) {
                require(
                        fixture.solver().copyCurrentDirection(
                                fixture.slots()[segment],
                                direction
                        ),
                        "Chain ripple direction was unavailable"
                );
                history[segment][frame - 120] = direction.x;
            }
        }
        float[] root = highPass(history[0]);
        float[] tip = highPass(history[segments - 1]);
        int lag = bestLag(root, tip);
        float aligned = shifted(root, tip, 0);
        float delayed = shifted(root, tip, lag);
        /*
         * The spring itself carries some delay down a chain, so a positive lag
         * alone proves nothing: with the travelling gust switched off this
         * fixture still reads 5 frames. What separates a wave from a chain
         * merely dragging behind its root is that the two ends stop resembling
         * each other when compared frame for frame — 0.19 here against 0.86
         * without it — while resembling each other closely once the delay is
         * taken out.
         */
        require(
                lag >= 10 && delayed > 0.80F,
                "No gust travelled down the chain; the tip never reproduced"
                        + " the root's motion later: lag " + lag
                        + " at " + delayed
        );
        require(
                aligned < 0.50F,
                "Chain segments leaned in lockstep instead of rippling: "
                        + aligned
        );
    }

    /**
     * Delay, in frames, at which the tail signal best reproduces the root
     * signal. Searching only positive lags would find one by construction, so
     * negative lags are searched too and a leading tip reads as failure.
     */
    private static final int LAG_SPAN = 60;

    private static int bestLag(float[] root, float[] tip) {
        int best = -LAG_SPAN - 1;
        float strongest = Float.NEGATIVE_INFINITY;
        for (int lag = -LAG_SPAN; lag <= LAG_SPAN; lag++) {
            float value = shifted(root, tip, lag);
            if (value > strongest) {
                strongest = value;
                best = lag;
            }
        }
        return best;
    }

    /** Correlation of the tip signal advanced by {@code lag} frames. */
    private static float shifted(float[] root, float[] tip, int lag) {
        int length = root.length - 2 * LAG_SPAN;
        float[] left = new float[length];
        float[] right = new float[length];
        for (int index = 0; index < length; index++) {
            left[index] = root[index + LAG_SPAN];
            right[index] = tip[index + LAG_SPAN + lag];
        }
        return correlation(left, right);
    }

    private static ChainFixture createChainFixture() {
        AnimatedGeoModel model = modelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{
                      "identifier":"geometry.wind_chain",
                      "texture_width":16,
                      "texture_height":16
                    },
                    "bones":[
                      {"name":"Root","pivot":[0,24,0]},
                      {"name":"Tail1","parent":"Root","pivot":[0,24,0],
                       "cubes":[{"origin":[-.5,20,-.5],"size":[1,4,1],
                                 "uv":[0,0]}]},
                      {"name":"Tail2","parent":"Tail1","pivot":[0,20,0],
                       "cubes":[{"origin":[-.5,16,-.5],"size":[1,4,1],
                                 "uv":[0,0]}]},
                      {"name":"Tail3","parent":"Tail2","pivot":[0,16,0],
                       "cubes":[{"origin":[-.5,12,-.5],"size":[1,4,1],
                                 "uv":[0,0]}]},
                      {"name":"Tail4","parent":"Tail3","pivot":[0,12,0],
                       "cubes":[{"origin":[-.5,8,-.5],"size":[1,4,1],
                                 "uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "schema_version":1,
                          "mode":"explicit",
                          "chains":[{
                            "id":"wind_chain",
                            "type":"TAIL",
                            "root":"Root/Tail1",
                            "profile":{
                              "gravity_scale":0.0,
                              "wind_scale":1.0
                            }
                          }]
                        }
                        """).getAsJsonObject(),
                "environmental wind chain verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:wind_chain",
                model,
                metadata
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        return new ChainFixture(
                new SpringBoneSolver(layout),
                new int[]{
                        drivenSlot(layout, model, "Tail1"),
                        drivenSlot(layout, model, "Tail2"),
                        drivenSlot(layout, model, "Tail3"),
                        drivenSlot(layout, model, "Tail4")
                }
        );
    }

    private static SiblingFixture createSiblingFixture() {
        AnimatedGeoModel model = modelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{
                      "identifier":"geometry.wind_siblings",
                      "texture_width":16,
                      "texture_height":16
                    },
                    "bones":[
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"StrandA","parent":"Root","pivot":[-3,6,0],
                       "cubes":[{
                         "origin":[-3.5,0,-.5],
                         "size":[1,6,1],
                         "uv":[0,0]
                       }]},
                      {"name":"StrandB","parent":"Root","pivot":[3,6,0],
                       "cubes":[{
                         "origin":[2.5,0,-.5],
                         "size":[1,6,1],
                         "uv":[0,0]
                       }]}
                    ]
                  }]
                }
                """);
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "schema_version":1,
                          "mode":"explicit",
                          "chains":[
                            {
                              "id":"strand_a",
                              "type":"HAIR",
                              "root":"Root/StrandA",
                              "profile":{
                                "gravity_scale":0.0,
                                "wind_scale":1.0
                              }
                            },
                            {
                              "id":"strand_b",
                              "type":"HAIR",
                              "root":"Root/StrandB",
                              "profile":{
                                "gravity_scale":0.0,
                                "wind_scale":1.0
                              }
                            }
                          ]
                        }
                        """).getAsJsonObject(),
                "environmental wind sibling verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:wind_siblings",
                model,
                metadata
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        return new SiblingFixture(
                new SpringBoneSolver(layout),
                drivenSlot(layout, model, "StrandA"),
                drivenSlot(layout, model, "StrandB")
        );
    }

    private static int drivenSlot(
            PhysicsSolverLayout layout,
            AnimatedGeoModel model,
            String boneName
    ) {
        AnimatedGeoBone bone = model.bones().get(boneName);
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == bone) {
                int slot = layout.node(index).drivenSlot();
                require(slot >= 0, boneName + " was not driven");
                return slot;
            }
        }
        throw new IllegalStateException(boneName + " is missing");
    }

    private static Fixture createFixture(String suffix) {
        return createFixture(suffix, "TAIL");
    }

    private static Fixture createFixture(String suffix, String type) {
        return createFixture(suffix, type, 1.0F);
    }

    private static Fixture createFixture(
            String suffix,
            String type,
            float massScale
    ) {
        AnimatedGeoModel model = modelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{
                      "identifier":"geometry.wind_%s",
                      "texture_width":16,
                      "texture_height":16
                    },
                    "bones":[
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Tail","parent":"Root","pivot":[0,6,0],
                       "cubes":[{
                         "origin":[-.5,0,-.5],
                         "size":[1,6,1],
                         "uv":[0,0]
                       }]}
                    ]
                  }]
                }
                """.formatted(suffix));
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "schema_version":1,
                          "mode":"explicit",
                          "chains":[{
                            "id":"wind_%s",
                            "type":"%s",
                            "root":"Root/Tail",
                            "profile":{
                              "gravity_scale":0.0,
                              "wind_scale":1.0,
                              "mass_scale":%s
                            }
                          }]
                        }
                        """.formatted(
                                suffix,
                                type,
                                Float.toString(massScale)
                        )).getAsJsonObject(),
                "environmental wind verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:wind_" + suffix,
                model,
                metadata
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        AnimatedGeoBone tail = model.bones().get("Tail");
        int drivenSlot = -1;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == tail) {
                drivenSlot = layout.node(index).drivenSlot();
                break;
            }
        }
        require(drivenSlot >= 0, "Environmental wind fixture was not driven");
        return new Fixture(tail, new SpringBoneSolver(layout), drivenSlot);
    }

    private record Fixture(
            AnimatedGeoBone tail,
            SpringBoneSolver solver,
            int drivenSlot
    ) {
    }

    private record SiblingFixture(
            SpringBoneSolver solver,
            int firstSlot,
            int secondSlot
    ) {
    }

    private record ChainFixture(SpringBoneSolver solver, int[] slots) {
    }
}
