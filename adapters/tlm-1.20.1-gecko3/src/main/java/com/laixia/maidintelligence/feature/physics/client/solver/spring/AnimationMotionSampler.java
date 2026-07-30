package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Samples animation-only mount motion and publishes one inertial acceleration
 * vector per driven bone without allocating in the frame loop.
 */
final class AnimationMotionSampler {
    private final PhysicsSolverLayout layout;
    private final AnimationPoseFrame poseFrame;
    private final AnimationMotionSample[] samples;
    private final AnimationMotionScratch scratch =
            new AnimationMotionScratch();
    private final Vector3f currentPivot = new Vector3f();
    private final Vector3f currentTip = new Vector3f();

    AnimationMotionSampler(PhysicsSolverLayout layout) {
        this.layout = layout;
        poseFrame = new AnimationPoseFrame(layout);
        samples = new AnimationMotionSample[layout.drivenBoneCount()];
        for (int slot = 0; slot < samples.length; slot++) {
            samples[slot] = new AnimationMotionSample();
        }
    }

    void sample(
            SpringBoneState state,
            float dt,
            boolean paused
    ) {
        poseFrame.prepare(state);
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (!node.driven()) {
                continue;
            }
            poseFrame.endpointsInto(index, currentPivot, currentTip);
            Quaternionf mountOrientation =
                    poseFrame.mountOrientation(index, state);
            samples[node.drivenSlot()].update(
                    node,
                    currentPivot,
                    currentTip,
                    mountOrientation,
                    dt,
                    paused,
                    scratch
            );
        }
    }

    Vector3f acceleration(int drivenSlot) {
        return samples[drivenSlot].acceleration();
    }

    boolean copyAcceleration(int drivenSlot, Vector3f output) {
        if (drivenSlot < 0 || drivenSlot >= samples.length) {
            output.zero();
            return false;
        }
        return samples[drivenSlot].copyAcceleration(output);
    }

    void reset() {
        poseFrame.reset();
        for (AnimationMotionSample sample : samples) {
            sample.reset();
        }
    }
}
