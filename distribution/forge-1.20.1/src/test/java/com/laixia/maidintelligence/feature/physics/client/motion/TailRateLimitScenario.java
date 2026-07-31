package com.laixia.maidintelligence.feature.physics.client.motion;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneDiscoverer;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;
import com.laixia.maidintelligence.feature.physics.session.AnimationTimelineClock;
import org.joml.Quaternionf;

import static com.laixia.maidintelligence.feature.physics.client.TailAnimationContinuityTestFacade.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.TailAnimationContinuityTestFacade.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.TailAnimationContinuityTestFacade.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.TailAnimationContinuityTestFacade.require;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.ENVIRONMENT_WIND;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.MOTION;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.NO_ENTITY_ACCELERATION;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.RENDER_FRAMES_PER_TICK;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.TAIL_PHASE_PER_TICK;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.angularDistance;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.createFixture;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.requireNear;

/**
 * Verifies render-rate tail continuity for normal and slow animation phases.
 */
public final class TailRateLimitScenario {
    private TailRateLimitScenario() {
    }

    public static void run() {
        verifiesRateLimitedTailAnimationRemainsContinuous();
    }

    public static void runRiceCakeSlowTail() throws Exception {
        verifiesRiceCakeSlowTailRemainsContinuous();
    }

    private static void verifiesRateLimitedTailAnimationRemainsContinuous() {
        TailMotionVerificationSupport.Fixture fixture = createFixture();
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
}
