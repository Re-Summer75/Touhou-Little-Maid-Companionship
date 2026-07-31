package com.laixia.maidintelligence.feature.physics.client.wind;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.session.PoseDriveGustFilter;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.EnvironmentalWindVerificationFacade.require;
import static com.laixia.maidintelligence.feature.physics.client.EnvironmentalWindVerificationFacade.requireNear;
import static com.laixia.maidintelligence.feature.physics.client.EnvironmentalWindVerificationFacade.requireVectorNear;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.ZERO;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.angleBetween;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.createFixture;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.createSiblingFixture;

public final class SolverWindIntegrationScenarios {
    private SolverWindIntegrationScenarios() {
    }

    public static void run() {
        verifiesPartResponseProfiles();
        verifiesLegacyNoWindCompatibility();
        verifiesWindDoesNotDisplaceTheInitialPose();
        verifiesSteadyWindDoesNotBecomeThePose();
        verifiesBoneReturnsToRestAfterWindStops();
        verifiesWindDrivesAnimationPose();
        verifiesMassControlsWindResponse();
        verifiesBonesDoNotFlutterInLockstep();
        verifiesRepeatedRenderHoldsTurbulencePhase();
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
        WindScenarioSupport.WindFixture legacy = createFixture("legacy");
        WindScenarioSupport.WindFixture explicitZero =
                createFixture("explicit_zero");
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

    /**
     * A bone that enters the solver while a gust is already blowing must start
     * where the animation put it. Wind is a lean added to the spring, not a
     * relocation of what the spring pulls toward, so a frame carrying no time
     * carries no wind either.
     */
    private static void verifiesWindDoesNotDisplaceTheInitialPose() {
        WindScenarioSupport.WindFixture fixture =
                createFixture("zero_step_pose");
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
            fixture.solver().solve(
                    ZERO, gust, 0.0F, 1.0F / 60.0F, false
            );
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

    /**
     * The authored pose stays the equilibrium: once the gust drops the spring
     * has to pull the bone back on its own, which a relocated rest target
     * could never do.
     */
    private static void verifiesBoneReturnsToRestAfterWindStops() {
        WindScenarioSupport.WindFixture fixture =
                createFixture("wind_release");
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
            fixture.solver().solve(
                    ZERO, gust, 0.0F, 1.0F / 60.0F, false
            );
        }
        fixture.solver().copyCurrentDirection(
                fixture.drivenSlot(),
                leaned
        );
        for (int frame = 0; frame < 240; frame++) {
            fixture.solver().restoreAnimationPose();
            fixture.solver().solve(
                    ZERO, ZERO, 0.0F, 1.0F / 60.0F, false
            );
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

    private static void verifiesWindDrivesAnimationPose() {
        WindScenarioSupport.WindFixture calm = createFixture("calm");
        WindScenarioSupport.WindFixture windy = createFixture("windy");
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
        WindScenarioSupport.WindFixture fixture = createFixture(
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
        WindScenarioSupport.SiblingWindFixture fixture =
                createSiblingFixture();
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
        WindScenarioSupport.WindFixture fixture =
                createFixture("phase_hold");
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
}
