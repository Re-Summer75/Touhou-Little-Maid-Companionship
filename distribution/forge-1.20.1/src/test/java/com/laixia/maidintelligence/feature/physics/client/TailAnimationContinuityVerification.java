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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadWinefoxGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Reproduces TLM's rate-limited controller plus per-render tail/default path.
 */
final class TailAnimationContinuityVerification {
    private static final float TAIL_PHASE_PER_TICK = 0.20F;
    private static final float RENDER_FRAMES_PER_TICK = 6.0F;
    private static final Vector3f MOTION =
            new Vector3f(0.35F, 0.0F, 0.20F);
    private static final Vector3f NO_ENTITY_ACCELERATION = new Vector3f();
    private static final Vector3f ENVIRONMENT_WIND =
            new Vector3f(0.045F, 0.0F, -0.015F);

    private TailAnimationContinuityVerification() {
    }

    static void run() throws Exception {
        verifiesSnapshotRestoresEveryLocalChannel();
        verifiesSmallPoseErrorsRemainContinuous();
        verifiesMicroscopicPoseErrorsRemainContinuous();
        verifiesRateLimitedTailAnimationRemainsContinuous();
        verifiesBundledTailCoalescesDuplicateRenders();
        verifiesRiceCakeSlowTailRemainsContinuous();
    }

    private static void verifiesSnapshotRestoresEveryLocalChannel() {
        Fixture fixture = createFixture();
        BoneModelSnapshot.Bone tail = fixture.tail();
        tail.setRotationX(0.13F);
        tail.setRotationY(-0.17F);
        tail.setRotationZ(0.21F);
        tail.setPositionX(1.25F);
        tail.setPositionY(-2.50F);
        tail.setPositionZ(3.75F);
        fixture.solver().solve(new Vector3f(), 0.0F, 0.0F, true);

        tail.setRotationX(10.0F);
        tail.setRotationY(11.0F);
        tail.setRotationZ(12.0F);
        tail.setPositionX(13.0F);
        tail.setPositionY(14.0F);
        tail.setPositionZ(15.0F);
        fixture.solver().restoreAnimationPose();

        requireNear(tail.getRotationX(), 0.13F, "rotation X");
        requireNear(tail.getRotationY(), -0.17F, "rotation Y");
        requireNear(tail.getRotationZ(), 0.21F, "rotation Z");
        requireNear(tail.getPositionX(), 1.25F, "position X");
        requireNear(tail.getPositionY(), -2.50F, "position Y");
        requireNear(tail.getPositionZ(), 3.75F, "position Z");
    }

    private static void verifiesSmallPoseErrorsRemainContinuous() {
        Fixture fixture = createFixture();
        BoneModelSnapshot.Bone tail = fixture.tail();
        Quaternionf animation = new Quaternionf().rotateZYX(
                tail.getRotationZ(),
                tail.getRotationY(),
                tail.getRotationX()
        );
        fixture.solver().solve(
                NO_ENTITY_ACCELERATION,
                0.0F,
                0.0F,
                false
        );
        fixture.solver().restoreAnimationPose();
        fixture.solver().solve(
                new Vector3f(0.001F, 0.0F, 0.0F),
                0.0F,
                1.0F / 120.0F,
                false
        );
        Quaternionf rendered = new Quaternionf().rotateZYX(
                tail.getRotationZ(),
                tail.getRotationY(),
                tail.getRotationX()
        );
        float physicalAngle = angularDistance(animation, rendered);
        require(
                physicalAngle > 1.0E-5F && physicalAngle < 2.0E-4F,
                "Sub-degree pose error was quantized away: "
                        + physicalAngle
        );
    }

    private static void verifiesMicroscopicPoseErrorsRemainContinuous() {
        Fixture fixture = createFixture();
        BoneModelSnapshot.Bone tail = fixture.tail();
        Quaternionf animation = new Quaternionf().rotateZYX(
                tail.getRotationZ(),
                tail.getRotationY(),
                tail.getRotationX()
        );
        fixture.solver().solve(
                NO_ENTITY_ACCELERATION,
                0.0F,
                0.0F,
                false
        );
        fixture.solver().restoreAnimationPose();
        fixture.solver().solve(
                new Vector3f(0.0001F, 0.0F, 0.0F),
                0.0F,
                1.0F / 120.0F,
                false
        );
        Quaternionf rendered = new Quaternionf().rotateZYX(
                tail.getRotationZ(),
                tail.getRotationY(),
                tail.getRotationX()
        );
        float physicalAngle = angularDistance(animation, rendered);
        require(
                physicalAngle > 5.0E-7F && physicalAngle < 1.0E-5F,
                "Microscopic pose error was quantized away: "
                        + physicalAngle
        );
    }

    private static void verifiesRateLimitedTailAnimationRemainsContinuous() {
        Fixture fixture = createFixture();
        BoneModelSnapshot.Bone tail = fixture.tail();
        Quaternionf previous = new Quaternionf();
        Quaternionf current = new Quaternionf();
        float expectedY = tail.getRotationY();
        float expectedPositionX = tail.getPositionX();
        float expectedPositionY = tail.getPositionY();
        float expectedPositionZ = tail.getPositionZ();
        float maximumStep = 0.0F;
        float peakDeflection = 0.0F;
        boolean hasPrevious = false;

        for (int frame = 0; frame < 360; frame++) {
            /*
             * HEAD injection: remove the previous physical overlay. Gecko's
             * controller is allowed to skip, while tail/default below still
             * writes X and Z from tickCount + partialTick every render call.
             */
            fixture.solver().restoreAnimationPose();
            requireNear(tail.getRotationY(), expectedY, "restored tail Y");
            requireNear(
                    tail.getPositionX(),
                    expectedPositionX,
                    "restored tail position X"
            );
            requireNear(
                    tail.getPositionY(),
                    expectedPositionY,
                    "restored tail position Y"
            );
            requireNear(
                    tail.getPositionZ(),
                    expectedPositionZ,
                    "restored tail position Z"
            );

            float ageInTicks = frame / RENDER_FRAMES_PER_TICK;
            float phase = ageInTicks * TAIL_PHASE_PER_TICK;
            tail.setRotationX(0.05F * (float) Math.sin(phase));
            tail.setRotationZ(0.10F * (float) Math.cos(phase));

            fixture.solver().solve(
                    MOTION,
                    ENVIRONMENT_WIND,
                    0.0F,
                    1.0F / 120.0F,
                    false
            );
            peakDeflection = Math.max(
                    peakDeflection,
                    fixture.solver().lastPeakDeflection()
            );
            current.identity().rotateZYX(
                    tail.getRotationZ(),
                    tail.getRotationY(),
                    tail.getRotationX()
            );
            if (hasPrevious && frame >= 60) {
                maximumStep = Math.max(
                        maximumStep,
                        angularDistance(previous, current)
                );
            }
            previous.set(current);
            hasPrevious = true;
        }

        require(
                peakDeflection > 1.0E-3F,
                "Tail continuity fixture never received a physical overlay"
        );
        require(
                maximumStep < 0.035F,
                "Rate-limited tail animation alternated between animation and "
                        + "physics poses: step=" + maximumStep
        );
    }

    private static float angularDistance(
            Quaternionf first,
            Quaternionf second
    ) {
        Quaternionf delta = new Quaternionf(first).conjugate().mul(second);
        float sine = (float) Math.sqrt(
                delta.x() * delta.x()
                        + delta.y() * delta.y()
                        + delta.z() * delta.z()
        );
        return 2.0F * (float) Math.atan2(sine, Math.abs(delta.w()));
    }

    private static void verifiesBundledTailCoalescesDuplicateRenders()
            throws Exception {
        BoneModelSnapshot model = coreModel(loadWinefoxGeoModel());
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:tail_continuity_winefox",
                model,
                PhysicsMetadata.EMPTY
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        AnimationTimelineClock clock = new AnimationTimelineClock();
        BoneModelSnapshot.Bone root = model.bones().get("Tail");
        BoneModelSnapshot.Bone terminal = model.bones().get("Tail7");
        int terminalIndex = -1;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == terminal) {
                terminalIndex = index;
                break;
            }
        }
        require(
                root != null && terminal != null && terminalIndex >= 0
                        && layout.node(terminalIndex).driven(),
                "Bundled seven-segment tail was not driven"
        );
        require(
                plan.decision(terminal).profile().windScale()
                        > plan.decision(root).profile().windScale(),
                "Automatic tail wind response did not increase root-to-tip"
        );

        float initialX = root.getRotationX();
        float initialZ = root.getRotationZ();
        int terminalSlot = layout.node(terminalIndex).drivenSlot();
        Vector3f tip = new Vector3f();
        Vector3f previousTip = new Vector3f();
        Vector3f velocity = new Vector3f();
        Vector3f previousVelocity = new Vector3f();
        Vector3f advancedDirection = new Vector3f();
        Vector3f duplicateDirection = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        Quaternionf previousRotation = new Quaternionf();
        float maximumTipStep = 0.0F;
        float maximumTipAcceleration = 0.0F;
        float maximumLocalRotationStep = 0.0F;
        boolean previousAvailable = false;
        for (int frame = 0; frame < 720; frame++) {
            float ageInTicks = frame / RENDER_FRAMES_PER_TICK;
            float phase = ageInTicks * TAIL_PHASE_PER_TICK;
            int duplicatePasses = switch (frame & 3) {
                case 1 -> 1;
                case 3 -> 2;
                default -> 0;
            };
            for (int pass = 0; pass <= duplicatePasses; pass++) {
                solver.restoreAnimationPose();
                root.setRotationX(
                        initialX + 0.05F * (float) Math.sin(phase)
                );
                root.setRotationZ(
                        initialZ + 0.10F * (float) Math.cos(phase)
                );
                float dt = clock.advance(ageInTicks, false);
                solver.solve(
                        NO_ENTITY_ACCELERATION,
                        ENVIRONMENT_WIND,
                        0.0F,
                        dt,
                        false
                );
                if (pass == 0) {
                    require(
                            solver.copyCurrentDirection(
                                    terminalSlot,
                                    advancedDirection
                            ),
                            "Bundled tail wind direction unavailable"
                    );
                } else {
                    require(
                            solver.copyCurrentDirection(
                                    terminalSlot,
                                    duplicateDirection
                            ),
                            "Duplicate-render tail direction unavailable"
                    );
                    require(
                            duplicateDirection.distance(advancedDirection)
                                    < 1.0E-7F,
                            "Duplicate render advanced environmental wind"
                    );
                }
            }
            require(
                    solver.copyRuntimeTip(terminalIndex, tip),
                    "Bundled tail terminal tip unavailable"
            );
            rotation.identity().rotateZYX(
                    terminal.getRotationZ(),
                    terminal.getRotationY(),
                    terminal.getRotationX()
            );
            if (previousAvailable && frame >= 120) {
                velocity.set(tip).sub(previousTip);
                maximumTipStep = Math.max(
                        maximumTipStep,
                        velocity.length()
                );
                maximumTipAcceleration = Math.max(
                        maximumTipAcceleration,
                        velocity.distance(previousVelocity)
                );
                maximumLocalRotationStep = Math.max(
                        maximumLocalRotationStep,
                        angularDistance(previousRotation, rotation)
                );
                previousVelocity.set(velocity);
            }
            previousTip.set(tip);
            previousRotation.set(rotation);
            previousAvailable = true;
        }
        require(
                maximumTipStep < 0.007F
                        && maximumTipAcceleration < 0.006F
                        && maximumLocalRotationStep < 0.012F,
                "Duplicate renders accumulated along the seven-segment tail: "
                        + "tipStep=" + maximumTipStep
                        + ", tipAcceleration=" + maximumTipAcceleration
                        + ", localRotationStep=" + maximumLocalRotationStep
        );
    }

    private static void verifiesRiceCakeSlowTailRemainsContinuous()
            throws Exception {
        BoneModelSnapshot model = coreModel(loadGeoModel(
                MODEL_DIRECTORY.resolve("rice_cake_fox.json")
        ));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:rice_cake_slow_tail",
                model,
                PhysicsMetadata.EMPTY
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        AnimationTimelineClock clock = new AnimationTimelineClock();
        BoneModelSnapshot.Bone root = model.bones().get("Tail");
        BoneModelSnapshot.Bone terminal = model.bones().get("Body_Tail6");
        int terminalIndex = -1;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == terminal) {
                terminalIndex = index;
                break;
            }
        }
        require(
                root != null && terminal != null && terminalIndex >= 0
                        && layout.node(terminalIndex).driven(),
                "Rice Cake Fox six-segment tail was not driven"
        );

        float initialX = root.getRotationX();
        float initialZ = root.getRotationZ();
        Quaternionf rotation = new Quaternionf();
        Quaternionf previousRotation = new Quaternionf();
        float previousStep = 0.0F;
        float maximumStep = 0.0F;
        float maximumStepChange = 0.0F;
        int quantizedFrames = 0;
        boolean previousAvailable = false;
        for (int frame = 0; frame < 2400; frame++) {
            float ageInTicks = frame / RENDER_FRAMES_PER_TICK;
            float phase = ageInTicks * 0.025F;
            solver.restoreAnimationPose();
            root.setRotationX(
                    initialX + 0.02F * (float) Math.sin(phase)
            );
            root.setRotationZ(
                    initialZ + 0.04F * (float) Math.cos(phase)
            );
            solver.solve(
                    NO_ENTITY_ACCELERATION,
                    ENVIRONMENT_WIND,
                    0.0F,
                    clock.advance(ageInTicks, false),
                    false
            );
            rotation.identity().rotateZYX(
                    terminal.getRotationZ(),
                    terminal.getRotationY(),
                    terminal.getRotationX()
            );
            if (previousAvailable && frame >= 240) {
                float step = angularDistance(previousRotation, rotation);
                maximumStep = Math.max(maximumStep, step);
                maximumStepChange = Math.max(
                        maximumStepChange,
                        Math.abs(step - previousStep)
                );
                if (step <= 1.0E-7F
                        && Math.abs(Math.cos(phase)) > 0.25D) {
                    quantizedFrames++;
                }
                previousStep = step;
            }
            previousRotation.set(rotation);
            previousAvailable = true;
        }
        require(
                quantizedFrames == 0
                        && maximumStep < 0.003F
                        && maximumStepChange < 0.001F,
                "Rice Cake Fox slow tail contained pose quantization: "
                        + "zeroFrames=" + quantizedFrames
                        + ", step=" + maximumStep
                        + ", stepChange=" + maximumStepChange
        );
    }

    private static void requireNear(
            float actual,
            float expected,
            String channel
    ) {
        require(
                Math.abs(actual - expected) < 1.0E-6F,
                "Animation pose snapshot lost " + channel + ": "
                        + actual + " != " + expected
        );
    }

    private static Fixture createFixture() {
        BoneModelSnapshot model = coreModelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{
                      "identifier":"geometry.tail_continuity",
                      "texture_width":32,
                      "texture_height":32
                    },
                    "bones":[
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Body","parent":"Root","pivot":[0,8,0],
                       "cubes":[{"origin":[-3,0,-2],"size":[6,8,4],"uv":[0,0]}]},
                      {"name":"MTail","parent":"Body","pivot":[0,7,2]},
                      {"name":"Tail","parent":"MTail","pivot":[0,7,2],
                       "cubes":[{"origin":[-.5,1,1.5],"size":[1,6,1],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {
                          "mode":"explicit",
                          "chains":[{
                            "id":"hardcoded_tail",
                            "type":"TAIL",
                            "root":"Root/Body/MTail/Tail",
                            "profile":{"gravity_scale":0.7},
                            "constraints":{
                              "simulation_space":"MODEL",
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
                "tail animation continuity verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:tail_continuity",
                model,
                metadata
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        BoneModelSnapshot.Bone tail = model.bones().get("Tail");
        boolean driven = false;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == tail) {
                driven = layout.node(index).driven();
                break;
            }
        }
        require(driven, "Tail continuity fixture was not driven");
        return new Fixture(tail, new SpringBoneSolver(layout));
    }

    private record Fixture(
            BoneModelSnapshot.Bone tail,
            SpringBoneSolver solver
    ) {
    }
}
