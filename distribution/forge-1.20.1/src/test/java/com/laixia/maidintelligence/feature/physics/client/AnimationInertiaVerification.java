package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class AnimationInertiaVerification {
    private static final Vector3f NO_ENTITY_ACCELERATION = new Vector3f();
    private static final float FREQUENCY_HZ = 1.50F;
    private static final float TAU = 2.0F * (float) Math.PI;

    private AnimationInertiaVerification() {
    }

    static void run() {
        verifiesStationaryAnimationHasNoForce();
        verifiesPositionRotationAndScaleDriveInertia();
        verifiesFrameRateIndependentSampling();
        verifiesHeldAnimationSamplesRemainContinuous();
        verifiesDuplicateRenderSampleKeepsHistory();
        verifiesCutsAndPauseResetHistory();
    }

    private static void verifiesStationaryAnimationHasNoForce() {
        Fixture fixture = createFixture();
        Vector3f acceleration = new Vector3f();
        for (int frame = 0; frame < 120; frame++) {
            applyPose(fixture, 0.0F, 0.0F, 1.0F);
            fixture.solver().solve(
                    NO_ENTITY_ACCELERATION,
                    0.0F,
                    1.0F / 60.0F,
                    false
            );
        }
        require(
                fixture.solver().copyAnimationAcceleration(
                        fixture.node().drivenSlot(),
                        acceleration
                ) && acceleration.lengthSquared() < 1.0E-12F,
                "Stationary authored animation generated inertial force: "
                        + acceleration
        );
    }

    private static void verifiesPositionRotationAndScaleDriveInertia() {
        MotionResult translation = runMotion(60, Motion.TRANSLATION);
        MotionResult rotation = runMotion(60, Motion.ROTATION);
        MotionResult scale = runMotion(60, Motion.SCALE);
        require(
                translation.peakAcceleration() > 0.02F
                        && translation.peakDeflection() > 0.001F,
                "Animated mount translation did not drive secondary motion: "
                        + translation
        );
        require(
                rotation.peakAcceleration() > 0.01F
                        && rotation.peakDeflection() > 0.001F,
                "Animated angular acceleration did not drive secondary motion: "
                        + rotation
        );
        require(
                scale.peakAcceleration() > 0.002F,
                "Animated scale acceleration was not sampled: " + scale
        );
    }

    private static void verifiesFrameRateIndependentSampling() {
        float atThirty = runMotion(30, Motion.TRANSLATION)
                .peakAcceleration();
        float atSixty = runMotion(60, Motion.TRANSLATION)
                .peakAcceleration();
        float atOneTwenty = runMotion(120, Motion.TRANSLATION)
                .peakAcceleration();
        float minimum = Math.min(atThirty, Math.min(atSixty, atOneTwenty));
        float maximum = Math.max(atThirty, Math.max(atSixty, atOneTwenty));
        require(
                minimum / maximum > 0.70F,
                "Animation inertia changed excessively with frame rate: "
                        + atThirty + "/" + atSixty + "/" + atOneTwenty
        );
    }

    private static void verifiesHeldAnimationSamplesRemainContinuous() {
        Fixture fixture = createFixture();
        Vector3f acceleration = new Vector3f();
        float heldPosition = 0.0F;
        float previousAcceleration = 0.0F;
        float maximumStep = 0.0F;
        int previousSign = 0;
        int signChanges = 0;
        int nextSourceFrame = 0;
        int cadenceIndex = 0;
        for (int frame = 0; frame < 360; frame++) {
            if (frame >= nextSourceFrame) {
                float time = frame / 120.0F;
                heldPosition = 2.0F * (float) Math.sin(
                        TAU * FREQUENCY_HZ * time
                );
                int sourceInterval = switch (cadenceIndex++ & 3) {
                    case 0 -> 5;
                    case 1 -> 7;
                    case 2 -> 4;
                    default -> 8;
                };
                nextSourceFrame = frame + sourceInterval;
            }
            applyPose(fixture, heldPosition, 0.0F, 1.0F);
            fixture.solver().solve(
                    NO_ENTITY_ACCELERATION,
                    0.0F,
                    1.0F / 120.0F,
                    false
            );
            fixture.solver().copyAnimationAcceleration(
                    fixture.node().drivenSlot(),
                    acceleration
            );
            if (frame < 120) {
                previousAcceleration = acceleration.x();
                continue;
            }
            maximumStep = Math.max(
                    maximumStep,
                    Math.abs(acceleration.x() - previousAcceleration)
            );
            int sign = Math.abs(acceleration.x()) < 0.01F
                    ? 0
                    : acceleration.x() > 0.0F ? 1 : -1;
            if (sign != 0) {
                if (previousSign != 0 && sign != previousSign) {
                    signChanges++;
                }
                previousSign = sign;
            }
            previousAcceleration = acceleration.x();
        }
        require(
                maximumStep < 0.10F && signChanges <= 8,
                "Held continuous animation produced inertial pulses: step="
                        + maximumStep + ", signChanges=" + signChanges
        );
    }

    private static void verifiesDuplicateRenderSampleKeepsHistory() {
        Fixture fixture = createFixture();
        Vector3f before = new Vector3f();
        Vector3f after = new Vector3f();
        float lastPosition = 0.0F;
        for (int frame = 0; frame < 15; frame++) {
            float time = frame / 60.0F;
            lastPosition = 2.0F * (float) Math.sin(
                    TAU * FREQUENCY_HZ * time
            );
            applyPose(fixture, lastPosition, 0.0F, 1.0F);
            fixture.solver().solve(
                    NO_ENTITY_ACCELERATION,
                    0.0F,
                    1.0F / 60.0F,
                    false
            );
        }
        fixture.solver().copyAnimationAcceleration(
                fixture.node().drivenSlot(),
                before
        );
        require(before.lengthSquared() > 1.0E-6F,
                "Duplicate-frame fixture did not establish inertia");

        applyPose(fixture, lastPosition, 0.0F, 1.0F);
        fixture.solver().solve(
                NO_ENTITY_ACCELERATION,
                0.0F,
                0.0F,
                false
        );
        fixture.solver().copyAnimationAcceleration(
                fixture.node().drivenSlot(),
                after
        );
        require(
                after.distanceSquared(before) < 1.0E-12F,
                "Duplicate render sample cleared animation history: "
                        + before + " -> " + after
        );
    }

    private static void verifiesCutsAndPauseResetHistory() {
        Fixture fixture = createFixture();
        Vector3f acceleration = new Vector3f();
        for (int frame = 0; frame < 20; frame++) {
            float time = frame / 60.0F;
            applyMotion(fixture, Motion.TRANSLATION, time);
            fixture.solver().solve(
                    NO_ENTITY_ACCELERATION,
                    0.0F,
                    1.0F / 60.0F,
                    false
            );
        }

        applyPose(
                fixture,
                64.0F,
                (float) Math.toRadians(120.0D),
                4.0F
        );
        fixture.solver().solve(
                NO_ENTITY_ACCELERATION,
                0.0F,
                1.0F / 60.0F,
                false
        );
        require(
                fixture.solver().copyAnimationAcceleration(
                        fixture.node().drivenSlot(),
                        acceleration
                ) && acceleration.lengthSquared() < 1.0E-12F,
                "Animation cut injected a false impulse: " + acceleration
        );

        applyPose(fixture, -64.0F, 0.0F, 0.25F);
        fixture.solver().solve(
                NO_ENTITY_ACCELERATION,
                0.0F,
                1.0F / 60.0F,
                true
        );
        require(
                fixture.solver().copyAnimationAcceleration(
                        fixture.node().drivenSlot(),
                        acceleration
                ) && acceleration.lengthSquared() < 1.0E-12F,
                "Paused animation retained inertial history: " + acceleration
        );
        fixture.solver().solve(
                NO_ENTITY_ACCELERATION,
                0.0F,
                1.0F / 60.0F,
                false
        );
        fixture.solver().copyAnimationAcceleration(
                fixture.node().drivenSlot(),
                acceleration
        );
        require(
                acceleration.lengthSquared() < 1.0E-12F,
                "Resume from an unchanged pose injected an impulse: "
                        + acceleration
        );
    }

    private static MotionResult runMotion(int fps, Motion motion) {
        Fixture fixture = createFixture();
        Vector3f acceleration = new Vector3f();
        float peakAcceleration = 0.0F;
        float peakDeflection = 0.0F;
        int frames = fps * 2;
        for (int frame = 0; frame < frames; frame++) {
            applyMotion(fixture, motion, frame / (float) fps);
            fixture.solver().solve(
                    NO_ENTITY_ACCELERATION,
                    0.0F,
                    1.0F / fps,
                    false
            );
            require(
                    fixture.solver().copyAnimationAcceleration(
                            fixture.node().drivenSlot(),
                            acceleration
                    ),
                    "Animation acceleration was unavailable"
            );
            peakAcceleration = Math.max(
                    peakAcceleration,
                    acceleration.length()
            );
            peakDeflection = Math.max(
                    peakDeflection,
                    fixture.solver().lastPeakDeflection()
            );
        }
        return new MotionResult(peakAcceleration, peakDeflection);
    }

    private static void applyMotion(
            Fixture fixture,
            Motion motion,
            float time
    ) {
        float wave = (float) Math.sin(TAU * FREQUENCY_HZ * time);
        switch (motion) {
            case TRANSLATION -> applyPose(
                    fixture,
                    2.0F * wave,
                    0.0F,
                    1.0F
            );
            case ROTATION -> applyPose(
                    fixture,
                    0.0F,
                    (float) Math.toRadians(20.0D) * wave,
                    1.0F
            );
            case SCALE -> applyPose(
                    fixture,
                    0.0F,
                    0.0F,
                    1.0F + 0.25F * wave
            );
        }
    }

    private static void applyPose(
            Fixture fixture,
            float headPositionX,
            float headRotationZ,
            float headScaleY
    ) {
        for (BoneModelSnapshot.Bone bone : fixture.model().bones().values()) {
            bone.setRotationX(0.0F);
            bone.setRotationY(0.0F);
            bone.setRotationZ(0.0F);
            bone.setPositionX(0.0F);
            bone.setPositionY(0.0F);
            bone.setPositionZ(0.0F);
            bone.setScaleX(1.0F);
            bone.setScaleY(1.0F);
            bone.setScaleZ(1.0F);
        }
        fixture.head().setPositionX(headPositionX);
        fixture.head().setRotationZ(headRotationZ);
        fixture.head().setScaleY(headScaleY);
    }

    private static Fixture createFixture() {
        BoneModelSnapshot model = coreModelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{
                      "identifier":"geometry.animation_inertia",
                      "texture_width":32,
                      "texture_height":32
                    },
                    "bones":[
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Body","parent":"Root","pivot":[0,0,0],
                       "cubes":[{"origin":[-3,0,-2],"size":[6,8,4],"uv":[0,0]}]},
                      {"name":"Head","parent":"Body","pivot":[0,8,0],
                       "cubes":[{"origin":[-4,8,-4],"size":[8,8,8],"uv":[0,0]}]},
                      {"name":"Hair","parent":"Head","pivot":[0,8,0],
                       "cubes":[{"origin":[-.5,4,-.5],"size":[1,4,1],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {
                          "mode":"explicit",
                          "chains":[{
                            "id":"animated_hair",
                            "type":"HAIR",
                            "root":"Root/Body/Head/Hair",
                            "profile":{"gravity_scale":0.0},
                            "constraints":{
                              "simulation_space":"HEAD_LOCAL",
                              "rotation_inertia_scale":0.5,
                              "swing_limits":{
                                "left_degrees":70.0,
                                "right_degrees":70.0,
                                "outward_degrees":70.0,
                                "inward_degrees":70.0
                              },
                              "backstop":false,
                              "head_collision":false
                            }
                          }]
                        }
                        """).getAsJsonObject(),
                "animation inertia verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:animation_inertia",
                model,
                metadata
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        BoneModelSnapshot.Bone hair = model.bones().get("Hair");
        PhysicsSolverLayout.Node driven = null;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == hair) {
                driven = layout.node(index);
                break;
            }
        }
        require(driven != null && driven.driven(),
                "Animation inertia fixture hair was not driven");
        return new Fixture(
                model,
                model.bones().get("Head"),
                driven,
                new SpringBoneSolver(layout)
        );
    }

    private enum Motion {
        TRANSLATION,
        ROTATION,
        SCALE
    }

    private record Fixture(
            BoneModelSnapshot model,
            BoneModelSnapshot.Bone head,
            PhysicsSolverLayout.Node node,
            SpringBoneSolver solver
    ) {
    }

    private record MotionResult(
            float peakAcceleration,
            float peakDeflection
    ) {
    }
}
