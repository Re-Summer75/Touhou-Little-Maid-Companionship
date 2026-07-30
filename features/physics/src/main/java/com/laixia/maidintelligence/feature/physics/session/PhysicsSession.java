package com.laixia.maidintelligence.feature.physics.session;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.engine.MotionSignalSampler;
import com.laixia.maidintelligence.feature.physics.engine.PhysicsMath;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.port.BoneModelPort;
import com.laixia.maidintelligence.feature.physics.port.PoseDriverPort;
import com.laixia.maidintelligence.feature.physics.port.PoseWriterPort;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Owns one entity's allocation-free timeline, input conditioning and solver.
 * Entity identity and lifecycle remain adapter concerns.
 */
public final class PhysicsSession {
    private static final float MAX_ACCELERATION = 0.5F;

    private final BoneModelPort modelPort;
    private final PoseWriterPort poseWriter;
    private final PoseDriverPort poseDriver;
    private final AnimationTimelineClock clock = new AnimationTimelineClock();
    private final MotionSignalSampler motionSampler = new MotionSignalSampler();
    private final PoseDriveGustFilter gustFilter = new PoseDriveGustFilter();
    private final Vector3f worldAcceleration = new Vector3f();
    private final Vector3f modelAcceleration = new Vector3f();
    private final Vector3f modelPoseDrive = new Vector3f();
    private final SpringBoneSolver solver;
    private float yawRate;
    private float lastDeltaSeconds;
    private boolean discontinuous;

    public PhysicsSession(
            BoneModelPort modelPort,
            PoseWriterPort poseWriter,
            PoseDriverPort poseDriver,
            PhysicsBoneSelectionPlan plan
    ) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort");
        this.poseWriter = Objects.requireNonNull(poseWriter, "poseWriter");
        this.poseDriver = Objects.requireNonNull(poseDriver, "poseDriver");
        solver = new SpringBoneSolver(PhysicsSolverLayout.build(
                modelPort.model(),
                Objects.requireNonNull(plan, "plan")
        ));
    }

    /**
     * Runs one rendered frame. All vectors are retained session state.
     */
    public float advance(
            double animationTime,
            double velocityX,
            double velocityY,
            double velocityZ,
            boolean onGround,
            int currentTick,
            float bodyYaw,
            float previousBodyYaw,
            boolean paused
    ) {
        modelPort.readAnimationPose();
        lastDeltaSeconds = clock.advance(animationTime, paused);
        discontinuous = clock.discontinuous();
        if (discontinuous) {
            resetTransientState();
        }
        yawRate = motionSampler.updateInto(
                velocityX,
                onGround ? 0.0D : velocityY,
                velocityZ,
                currentTick,
                bodyYaw,
                previousBodyYaw,
                MAX_ACCELERATION,
                lastDeltaSeconds,
                worldAcceleration
        );
        worldToModelInto(worldAcceleration, bodyYaw, modelAcceleration);
        poseDriver.sampleInto(
                animationTime,
                lastDeltaSeconds,
                paused,
                modelPoseDrive
        );
        gustFilter.isolateGust(
                modelPoseDrive,
                lastDeltaSeconds,
                paused
        );
        solver.solve(
                modelAcceleration,
                modelPoseDrive,
                yawRate,
                lastDeltaSeconds,
                paused
        );
        poseWriter.writePose(modelPort.model());
        return lastDeltaSeconds;
    }

    public void restoreAnimationPose() {
        solver.restoreAnimationPose();
        poseWriter.writePose(modelPort.model());
    }

    public void resetTransientState() {
        solver.reset();
        motionSampler.reset();
        poseDriver.reset();
        gustFilter.reset();
        worldAcceleration.zero();
        modelAcceleration.zero();
        modelPoseDrive.zero();
        yawRate = 0.0F;
    }

    public void reset() {
        resetTransientState();
        clock.reset();
        lastDeltaSeconds = 0.0F;
        discontinuous = false;
    }

    public SpringBoneSolver solver() {
        return solver;
    }

    public PhysicsSolverLayout layout() {
        return solver.layout();
    }

    public float yawRate() {
        return yawRate;
    }

    public float lastDeltaSeconds() {
        return lastDeltaSeconds;
    }

    public boolean discontinuous() {
        return discontinuous;
    }

    public Vector3f copyModelAcceleration(Vector3f output) {
        return output.set(modelAcceleration);
    }

    public Vector3f copyModelPoseDrive(Vector3f output) {
        return output.set(modelPoseDrive);
    }

    private static Vector3f worldToModelInto(
            Vector3f world,
            float bodyYawDegrees,
            Vector3f output
    ) {
        float yaw = (float) Math.toRadians(
                PhysicsMath.wrapDegrees(bodyYawDegrees - 180.0F)
        );
        return output.set(world).rotateY(yaw);
    }
}
