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
 * This fixture isolates that integration from swing and collision projection,
 * releases a displaced model, and varies the step while the transient decays.
 *
 * <p>That failure was found in play rather than here, just after a world load
 * where frame times swing hardest. Every driven segment of the maid reversed on
 * every single frame — hair, ears, skirt, none of them touching a collider or
 * clamped by a swing limit — at up to 22 px a frame, decaying over about a
 * second. Nothing in the bench caught it because every other fixture steps at a
 * fixed dt, so this one deliberately does not.
 */
final class VariableFrameStabilityVerification {
    private static final String MODEL = "winefox.json";
    private static final float TICK = 1.0F / 20.0F;
    /** Frames to let the model settle before the frame rate is disturbed. */
    private static final int SETTLE = 120;
    private static final int SAMPLE = 240;
    /** Steps below this are numerical noise rather than travel. */
    private static final float MOVED = 1.0E-4F;
    /** Acceleration used to displace the model before the transient is released. */
    private static final float SHOVE = 6.0F;
    /**
     * Reversing travel a segment may cover per frame, in model pixels.
     *
     * <p>Counted as amplitude rather than as a reversal tally. A stable spring
     * driven by an alternating frame time reverses on nearly every frame by
     * design — it is being asked for a different step each time — so the tally
     * alone condemns healthy motion: it read 238 of 240 frames on a model that
     * was visibly fine. What separates the divergence is how far each of those
     * reversals travels; the one measured in game ran at 22 px a frame.
     *
     * <p>The isolated transient measures about 0.001 px per frame. A 0.02 px
     * ceiling leaves room for platform rounding while remaining three orders of
     * magnitude below the 22 px world-load failure this test guards against.
     */
    private static final float MAX_BUZZ_PIXELS = 0.02F;

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
        SpringBoneSolver solver = new SpringBoneSolver(layout, false);
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
        measure(layout, solver, new Vector3f());
    }

    private static void measure(
            PhysicsSolverLayout layout,
            SpringBoneSolver solver,
            Vector3f modelAcceleration
    ) {
        int count = layout.activeNodeCount();
        Vector3f[] previous = new Vector3f[count];
        Vector3f[] lastStep = new Vector3f[count];
        int[] reversals = new int[count];
        float[] reversing = new float[count];
        Vector3f current = new Vector3f();
        Vector3f step = new Vector3f();
        for (int index = 0; index < count; index++) {
            previous[index] = new Vector3f();
            lastStep[index] = new Vector3f();
            if (layout.node(index).driven()) {
                solver.copyCurrentDirection(
                        layout.node(index).drivenSlot(), previous[index]
                );
            }
        }
        for (int frame = 0; frame < SAMPLE; frame++) {
            solver.restoreAnimationPose();
            solver.solve(
                    modelAcceleration,
                    0.0F,
                    alternatingStep(frame),
                    false
            );
            for (int index = 0; index < count; index++) {
                PhysicsSolverLayout.Node node = layout.node(index);
                if (!node.driven()
                        || !solver.copyCurrentDirection(
                                node.drivenSlot(), current)) {
                    continue;
                }
                step.set(current).sub(previous[index]);
                previous[index].set(current);
                if (step.length() <= MOVED) {
                    continue;
                }
                if (lastStep[index].dot(step) < 0.0F) {
                    reversals[index]++;
                    reversing[index] += step.length();
                }
                lastStep[index].set(step);
            }
        }
        report(layout, reversing);
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

    private static void report(PhysicsSolverLayout layout, float[] reversing) {
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
        float perFrame = reversing[worst] / SAMPLE
                * layout.node(worst).kinematics().leverArm() * 16.0F;
        System.out.printf(
                "variable frame: worst %s buzz %.3f px/f%n",
                layout.node(worst).bone().getName(),
                perFrame
        );
        require(
                perFrame <= MAX_BUZZ_PIXELS,
                "An uneven frame rate shook " + MODEL + " segment "
                        + layout.node(worst).bone().getName()
                        + " by " + perFrame + " px per frame"
        );
    }
}
