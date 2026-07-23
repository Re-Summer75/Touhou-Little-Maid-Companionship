package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.solver.AdaptiveMotionFilter;
import com.laixia.maidintelligence.feature.physics.client.solver.MotionNoiseGate;
import com.laixia.maidintelligence.feature.physics.client.solver.MotionSignalSampler;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireNear;

final class MotionDynamicsVerification {
    private MotionDynamicsVerification() {
    }

    static void run() {
        verifiesMotionCoordinateFrame();
        verifiesTimeCorrectedDrag();
        verifiesAdaptiveMotionDenoising();
    }

    private static void verifiesMotionCoordinateFrame() {
        Vector3f facingSouth = MaidBonePhysics.worldAccelerationToModel(
                new Vec3(0.0D, 0.0D, 1.0D),
                0.0F
        );
        requireNear(facingSouth.x, 0.0F, "South-facing acceleration gained X");
        requireNear(
                facingSouth.z,
                -1.0F,
                "South-facing forward axis was reversed"
        );
        require(
                new Vector3f(facingSouth).negate().z > 0.99F,
                "Inertial force did not lag behind forward acceleration"
        );

        Vector3f facingWest = MaidBonePhysics.worldAccelerationToModel(
                new Vec3(-1.0D, 0.0D, 0.0D),
                90.0F
        );
        requireNear(
                facingWest.z,
                -1.0F,
                "West-facing forward axis was reversed"
        );

        Quaternionf parentDeflection = new Quaternionf().rotateZ(
                (float) Math.toRadians(10.0D)
        );
        Quaternionf childBase = MaidBonePhysics.composeOrientation(
                parentDeflection,
                0.0F,
                0.0F,
                0.0F
        );
        Vector3f childRest = childBase.transform(
                new Vector3f(0.0F, -1.0F, 0.0F)
        );
        require(
                childRest.x > 0.17F,
                "Child rest frame did not inherit the current parent deflection"
        );
    }

    private static void verifiesTimeCorrectedDrag() {
        float atSixtyFps = MaidBonePhysics.dragRetention(
                0.35F,
                1.0F / 60.0F
        );
        float atThirtyFps = MaidBonePhysics.dragRetention(
                0.35F,
                1.0F / 30.0F
        );
        float atOneTwentyFps = MaidBonePhysics.dragRetention(
                0.35F,
                1.0F / 120.0F
        );
        requireNear(
                atThirtyFps,
                atSixtyFps * atSixtyFps,
                "30 FPS drag does not match two 60 FPS steps"
        );
        requireNear(
                atOneTwentyFps * atOneTwentyFps,
                atSixtyFps,
                "120 FPS drag does not match one 60 FPS step"
        );
    }

    private static void verifiesAdaptiveMotionDenoising() {
        Vector3f tinyNoise = MotionNoiseGate.vector(
                new Vector3f(0.005F, 0.0F, 0.0F),
                0.5F,
                0.01F,
                0.04F
        );
        require(
                tinyNoise.lengthSquared() < 1.0E-8F,
                "Nonlinear dead zone did not remove tiny movement noise"
        );
        Vector3f saturatedSpike = MotionNoiseGate.vector(
                new Vector3f(5.0F, 0.0F, 0.0F),
                0.5F,
                0.01F,
                0.04F
        );
        require(
                saturatedSpike.x > 0.35F && saturatedSpike.x <= 0.5F,
                "Soft saturation did not bound a movement spike"
        );

        float dt = 1.0F / 60.0F;
        AdaptiveMotionFilter slowFilter = new AdaptiveMotionFilter();
        AdaptiveMotionFilter fastFilter = new AdaptiveMotionFilter();
        slowFilter.update(new Vector3f(), 0.0F, 0.5F, dt);
        fastFilter.update(new Vector3f(), 0.0F, 0.5F, dt);
        float slowResponse = slowFilter.update(
                new Vector3f(0.06F, 0.0F, 0.0F),
                0.0F,
                0.5F,
                dt
        ).acceleration().x / 0.06F;
        float fastResponse = fastFilter.update(
                new Vector3f(0.30F, 0.0F, 0.0F),
                0.0F,
                0.5F,
                dt
        ).acceleration().x / 0.30F;
        require(
                fastResponse > slowResponse,
                "Adaptive cutoff did not respond faster to intentional motion"
        );
        require(
                fastResponse < 1.0F,
                "Adaptive filter passed a full one-frame movement step"
        );

        MotionSignalSampler sampler = new MotionSignalSampler();
        sampler.update(Vec3.ZERO, 0, 0.0F, 0.0F, 0.5F, dt);
        float firstTickSample = sampler.update(
                new Vec3(0.02D, 0.0D, 0.0D),
                1, 0.0F, 0.0F, 0.5F, dt
        ).worldAcceleration().x;
        float heldRenderSample = sampler.update(
                new Vec3(0.02D, 0.0D, 0.0D),
                1, 0.0F, 0.0F, 0.5F, dt
        ).worldAcceleration().x;
        require(
                heldRenderSample > firstTickSample,
                "Tick acceleration was replaced by zero between render frames"
        );
        float releaseSample = sampler.update(
                new Vec3(0.02D, 0.0D, 0.0D),
                2, 0.0F, 0.0F, 0.5F, dt
        ).worldAcceleration().x;
        require(
                releaseSample > 0.0F && releaseSample < heldRenderSample,
                "Tick acceleration release was not smoothed"
        );
    }
}
