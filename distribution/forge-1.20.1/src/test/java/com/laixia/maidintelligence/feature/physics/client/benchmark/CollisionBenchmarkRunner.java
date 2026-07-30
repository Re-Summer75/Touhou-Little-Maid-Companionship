package com.laixia.maidintelligence.feature.physics.client.benchmark;

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

import static com.laixia.maidintelligence.feature.physics.client.benchmark.CollisionBenchmarkFixture.Scenario;

final class CollisionBenchmarkRunner {
    static final int WARMUP_FRAMES = 10_000;
    static final int MEASURED_FRAMES = 20_000;
    private static volatile double blackhole;

    private CollisionBenchmarkRunner() {
    }

    static Result run(Scenario scenario) {
        SpringBoneSolver solver = scenario.solver();
        PoseResetter resetter = new PoseResetter(scenario.layout());
        ThreadAllocationMeter allocations = ThreadAllocationMeter.create();
        Vector3f acceleration = new Vector3f();
        double checksum = warm(
                solver,
                resetter,
                allocations,
                acceleration
        );
        long solveNanos = 0L;
        long allocatedBytes = allocations.available() ? 0L : -1L;
        for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
            resetter.reset(frame);
            motion(frame, acceleration);
            float yawRate = yawRate(frame);
            float deltaSeconds = deltaSeconds(frame);
            long allocatedBefore = allocations.currentBytes();
            long start = System.nanoTime();
            solver.solve(
                    acceleration,
                    yawRate,
                    deltaSeconds,
                    false
            );
            long end = System.nanoTime();
            long allocatedAfter = allocations.currentBytes();
            solveNanos += end - start;
            if (allocatedBytes >= 0L) {
                allocatedBytes += allocatedAfter - allocatedBefore;
            }
            checksum += solver.lastPeakDeflection();
        }
        blackhole = checksum;
        return new Result(
                solveNanos / (double) MEASURED_FRAMES,
                allocatedBytes < 0L
                        ? -1.0D
                        : allocatedBytes / (double) MEASURED_FRAMES
        );
    }

    private static double warm(
            SpringBoneSolver solver,
            PoseResetter resetter,
            ThreadAllocationMeter allocations,
            Vector3f acceleration
    ) {
        double checksum = 0.0D;
        for (int frame = 0; frame < WARMUP_FRAMES; frame++) {
            resetter.reset(frame);
            motion(frame, acceleration);
            float yawRate = yawRate(frame);
            float deltaSeconds = deltaSeconds(frame);
            allocations.currentBytes();
            System.nanoTime();
            solver.solve(
                    acceleration,
                    yawRate,
                    deltaSeconds,
                    false
            );
            System.nanoTime();
            allocations.currentBytes();
            checksum += solver.lastPeakDeflection();
        }
        return checksum;
    }

    private static void motion(int frame, Vector3f output) {
        output.set(
                (float) Math.sin(frame * 0.12D) * 0.20F,
                0.0F,
                (float) Math.cos(frame * 0.08D) * 0.16F
        );
    }

    private static float yawRate(int frame) {
        return (float) Math.sin(frame * 0.05D) * 0.35F;
    }

    private static float deltaSeconds(int frame) {
        return frame % 4 == 0 ? 1.0F / 30.0F : 1.0F / 60.0F;
    }

    record Result(
            double nanosecondsPerFrame,
            double allocatedBytesPerFrame
    ) {
    }

    private static final class PoseResetter {
        private final PhysicsSolverLayout layout;

        private PoseResetter(PhysicsSolverLayout layout) {
            this.layout = layout;
        }

        private void reset(int frame) {
            for (int index = 0; index < layout.activeNodeCount(); index++) {
                BoneModelSnapshot.Bone bone = layout.node(index).bone();
                float wave = (float) Math.sin(
                        frame * 0.071D + index * 0.193D
                );
                bone.setRotationX(wave * 0.018F);
                bone.setRotationY(-wave * 0.011F);
                bone.setRotationZ(wave * 0.009F);
                bone.setPositionX(0.0F);
                bone.setPositionY(0.0F);
                bone.setPositionZ(0.0F);
                bone.setScaleX(1.0F);
                bone.setScaleY(1.0F);
                bone.setScaleZ(1.0F);
            }
        }
    }
}
