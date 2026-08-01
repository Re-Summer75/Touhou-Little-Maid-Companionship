package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import org.joml.Vector3f;

import java.nio.file.Path;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Checks that an uneven frame rate does not make a settled model shake.
 *
 * <p>A Verlet integrator carries a displacement rather than a velocity, so a
 * changing frame time has to be corrected for by rescaling that displacement.
 * This fixture runs the production constraints but excludes every sample where
 * swing, collision, or a skirt tether projected the segment. The remaining
 * reversals therefore belong to free integration, as did the affected ears and
 * hair in the live report.
 *
 * <p>That failure was found in play rather than here, just after a world load
 * where frame times swing hardest. Every driven segment of the maid reversed on
 * every single frame — hair and ears with no support, collision, or swing
 * correction — at up to 16.9 px a frame. Nothing in the bench caught it because
 * every other fixture either stepped at a fixed dt or measured total vector
 * travel rather than the reversing rest-offset reported in play.
 */
final class VariableFrameStabilityVerification {
    private static final String MODEL = "winefox.json";
    private static final float TICK = 1.0F / 20.0F;
    /** Frames to let the model settle before the frame rate is disturbed. */
    private static final int SETTLE = 120;
    private static final int SAMPLE = 240;
    /** Tip movement below this is numerical noise rather than travel. */
    private static final float MOVED = 0.002F;
    /** Acceleration used to displace the model before the transient is released. */
    private static final float SHOVE = 6.0F;
    /**
     * Reversing travel a segment may cover per frame, in model pixels.
     *
     * <p>Counted with the same angular tip-offset metric as the live logger.
     * Reversal count alone condemns harmless contact chatter; visible travel
     * distinguishes it from the multi-pixel period-one bursts in the log.
     *
     * <p>The stabilized variable-step wind reversal measures about 0.065 px per
     * frame; the undamped A/B baseline measured 0.098. The ceiling retains
     * platform margin while remaining two orders below the live failure.
     */
    private static final float MAX_BUZZ_PIXELS = 0.08F;

    private VariableFrameStabilityVerification() {
    }

    static void run() throws Exception {
        Path modelPath = MODEL_DIRECTORY.resolve(MODEL);
        BoneModelSnapshot model = coreModel(loadGeoModel(modelPath));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:variable_frame",
                model,
                PhysicsMetadata.EMPTY
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        SpringBoneSolver solver = new SpringBoneSolver(layout, true);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        /*
         * First hold the model at a displaced equilibrium. Releasing that force
         * below supplies real carried motion; varying dt around a spring already
         * at rest would only be rescaling zero.
         */
        Vector3f push = new Vector3f(SHOVE, 0.0F, SHOVE);
        for (int frame = 0; frame < SETTLE; frame++) {
            solver.restoreAnimationPose();
            solver.solve(push, 0.0F, TICK, false);
        }
        // Releasing the displaced model creates the world-load transient.
        measure(layout, solver, new Vector3f(), new Vector3f(), "transient");

        solver.restoreAnimationPose();
        solver.reset();
        Vector3f wind = new Vector3f(0.32F, 0.0F, 0.18F);
        for (int frame = 0; frame < SETTLE; frame++) {
            solver.restoreAnimationPose();
            solver.solve(new Vector3f(), wind, 0.0F, TICK, false);
        }
        measure(
                layout,
                solver,
                new Vector3f(),
                new Vector3f(wind).negate(),
                "wind reversal"
        );
    }

    private static void measure(
            PhysicsSolverLayout layout,
            SpringBoneSolver solver,
            Vector3f modelAcceleration,
            Vector3f poseDrive,
            String label
    ) {
        int count = layout.activeNodeCount();
        float[] previousOffset = new float[count];
        float[] lastStep = new float[count];
        int[] reversals = new int[count];
        float[] reversing = new float[count];
        Vector3f current = new Vector3f();
        Vector3f rest = new Vector3f();
        for (int index = 0; index < count; index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (node.driven()
                    && solver.copyCurrentDirection(
                            node.drivenSlot(), current)
                    && solver.copyRestDirection(node.drivenSlot(), rest)) {
                previousOffset[index] = displacement(current, rest, node);
            }
        }
        for (int frame = 0; frame < SAMPLE; frame++) {
            solver.restoreAnimationPose();
            solver.solve(
                    modelAcceleration,
                    poseDrive,
                    0.0F,
                    alternatingStep(frame),
                    false
            );
            for (int index = 0; index < count; index++) {
                PhysicsSolverLayout.Node node = layout.node(index);
                if (!node.driven()
                        || !solver.copyCurrentDirection(
                                node.drivenSlot(), current)
                        || !solver.copyRestDirection(
                                node.drivenSlot(), rest)) {
                    continue;
                }
                float offset = displacement(current, rest, node);
                float step = offset - previousOffset[index];
                previousOffset[index] = offset;
                // Projection cycles have their own detector and regression set.
                if (solver.lastProjectionSource(node.drivenSlot()) != 0) {
                    lastStep[index] = 0.0F;
                    continue;
                }
                if (Math.abs(step) <= MOVED) {
                    continue;
                }
                if (lastStep[index] * step < 0.0F) {
                    reversals[index]++;
                    reversing[index] += Math.abs(step);
                }
                lastStep[index] = step;
            }
        }
        report(layout, reversals, reversing, label);
    }

    /**
     * A frame time that swings between a full tick and a fifth of one.
     *
     * <p>The pattern is deliberately the worst case for the correction rather
     * than a realistic trace: alternating long and short frames is what turns a
     * loosely bounded rescale into a sign-alternating divergence, and a smooth
     * ramp between the same extremes does not.
     */
    private static float alternatingStep(int frame) {
        return switch (frame % 4) {
            case 0, 2 -> TICK;
            case 1 -> TICK * 0.2F;
            default -> TICK * 0.6F;
        };
    }

    private static void report(
            PhysicsSolverLayout layout,
            int[] reversals,
            float[] reversing,
            String label
    ) {
        int worst = -1;
        for (int index = 0; index < reversing.length; index++) {
            if (layout.node(index).driven()
                    && (worst < 0
                    || reversing[index] > reversing[worst])) {
                worst = index;
            }
        }
        if (worst < 0) {
            return;
        }
        float perFrame = reversing[worst] / SAMPLE;
        System.out.printf(
                "variable frame %s: worst %s buzz %.3f px/f, reversals %d%n",
                label,
                layout.node(worst).bone().getName(),
                perFrame,
                reversals[worst]
        );
        require(
                perFrame <= MAX_BUZZ_PIXELS,
                "An uneven frame rate shook " + MODEL + " segment "
                        + layout.node(worst).bone().getName()
                        + " by " + perFrame + " px per frame"
        );
    }

    private static float displacement(
            Vector3f current,
            Vector3f rest,
            PhysicsSolverLayout.Node node
    ) {
        float dot = Math.max(-1.0F, Math.min(1.0F, current.dot(rest)));
        return (float) Math.acos(dot)
                * node.kinematics().leverArm() * 16.0F;
    }
}
