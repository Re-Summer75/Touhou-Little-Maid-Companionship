package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.solver.AdaptiveMotionFilter;
import com.laixia.maidintelligence.feature.physics.client.solver.MotionNoiseGate;
import com.laixia.maidintelligence.feature.physics.client.solver.MotionSignalSampler;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireNear;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireVectorNear;

final class MotionAllocationVerification {
    private MotionAllocationVerification() {
    }

    static void run() {
        Vector3f input = new Vector3f(0.12F, -0.03F, 0.04F);
        Vector3f allocatingGate = ReferenceMotionNoiseGate.vector(
                input,
                0.5F,
                0.01F,
                0.04F
        );
        Vector3f intoGate = MotionNoiseGate.vectorInto(
                input,
                0.5F,
                0.01F,
                0.04F,
                new Vector3f()
        );
        requireVectorNear(
                intoGate,
                allocatingGate,
                1.0E-6F,
                "Motion noise gate updateInto changed its output"
        );

        ReferenceAdaptiveMotionFilter allocatingFilter =
                new ReferenceAdaptiveMotionFilter();
        AdaptiveMotionFilter intoFilter = new AdaptiveMotionFilter();
        Vector3f intoAcceleration = new Vector3f();
        for (int frame = 0; frame < 90; frame++) {
            Vector3f raw = new Vector3f(
                    (float) Math.sin(frame * 0.17D) * 0.22F,
                    frame > 45 ? 0.03F : 0.0F,
                    (float) Math.cos(frame * 0.11D) * 0.08F
            );
            float rawYaw = (float) Math.sin(frame * 0.09D) * 0.35F;
            float dt = frame % 3 == 0
                    ? 1.0F / 30.0F
                    : 1.0F / 60.0F;
            ReferenceAdaptiveMotionFilter.Output expected =
                    allocatingFilter.update(raw, rawYaw, 0.5F, dt);
            float actualYaw = intoFilter.updateInto(
                    raw,
                    rawYaw,
                    0.5F,
                    dt,
                    intoAcceleration
            );
            requireVectorNear(
                    intoAcceleration,
                    expected.acceleration(),
                    1.0E-6F,
                    "Adaptive filter updateInto changed acceleration at frame "
                            + frame
            );
            requireNear(
                    actualYaw,
                    expected.yawRate(),
                    1.0E-6F,
                    "Adaptive filter updateInto changed yaw at frame " + frame
            );
        }

        ReferenceMotionSignalSampler allocatingSampler =
                new ReferenceMotionSignalSampler();
        MotionSignalSampler intoSampler = new MotionSignalSampler();
        Vector3f sampledInto = new Vector3f();
        for (int frame = 0; frame < 80; frame++) {
            int tick = frame / 3;
            Vec3 velocity = new Vec3(
                    tick < 8 ? tick * 0.01D : 0.08D,
                    tick > 12 && tick < 16 ? 0.04D : 0.0D,
                    tick > 18 ? -0.025D : 0.0D
            );
            float yaw = tick * 2.0F;
            ReferenceMotionSignalSampler.Output expected =
                    allocatingSampler.update(
                            velocity,
                            tick,
                            yaw,
                            yaw - 2.0F,
                            0.5F,
                            1.0F / 60.0F
                    );
            float actualYaw = intoSampler.updateInto(
                    velocity.x,
                    velocity.y,
                    velocity.z,
                    tick,
                    yaw,
                    yaw - 2.0F,
                    0.5F,
                    1.0F / 60.0F,
                    sampledInto
            );
            requireVectorNear(
                    sampledInto,
                    expected.worldAcceleration(),
                    1.0E-6F,
                    "Motion sampler updateInto changed acceleration at frame "
                            + frame
            );
            requireNear(
                    actualYaw,
                    expected.yawRate(),
                    1.0E-6F,
                    "Motion sampler updateInto changed yaw at frame " + frame
            );
        }
    }
}
