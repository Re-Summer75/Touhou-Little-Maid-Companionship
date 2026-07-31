package com.laixia.maidintelligence.feature.physics.client.motion;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneDiscoverer;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;
import com.laixia.maidintelligence.feature.physics.session.AnimationTimelineClock;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.TailAnimationContinuityTestFacade.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.TailAnimationContinuityTestFacade.loadWinefoxGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.TailAnimationContinuityTestFacade.require;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.ENVIRONMENT_WIND;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.NO_ENTITY_ACCELERATION;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.RENDER_FRAMES_PER_TICK;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.TAIL_PHASE_PER_TICK;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.angularDistance;

/**
 * Verifies duplicate render passes are coalesced for a bundled tail.
 */
public final class TailCoalesceScenario {
    private TailCoalesceScenario() {
    }

    public static void run() throws Exception {
        verifiesBundledTailCoalescesDuplicateRenders();
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
}
