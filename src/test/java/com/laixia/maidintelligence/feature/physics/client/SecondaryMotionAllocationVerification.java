package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.SecondaryMotionFixture.Fixture;
import org.joml.Vector3f;

import java.lang.management.ManagementFactory;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class SecondaryMotionAllocationVerification {
    private static final int WARMUP_FRAMES = 8_000;
    private static final int MEASURED_FRAMES = 4_000;

    private SecondaryMotionAllocationVerification() {
    }

    static void run() {
        java.lang.management.ThreadMXBean platformBean =
                ManagementFactory.getThreadMXBean();
        if (!(platformBean instanceof com.sun.management.ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) {
            return;
        }
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }

        Fixture fixture = SecondaryMotionFixture.create(
                20.0F,
                0.20F,
                true,
                true
        );
        Vector3f acceleration = new Vector3f(2.5F, 0.0F, 0.0F);
        for (int frame = 0; frame < WARMUP_FRAMES; frame++) {
            solveFrame(fixture, acceleration);
        }
        long threadId = Thread.currentThread().getId();
        bean.getThreadAllocatedBytes(threadId);
        long before = bean.getThreadAllocatedBytes(threadId);
        for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
            solveFrame(fixture, acceleration);
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;
        require(
                allocated == 0L,
                "Secondary-motion constraint hot path allocated "
                        + allocated + " bytes"
        );
    }

    private static void solveFrame(
            Fixture fixture,
            Vector3f acceleration
    ) {
        fixture.head().setRotationX(0.0F);
        fixture.head().setRotationY(0.0F);
        fixture.head().setRotationZ(0.0F);
        fixture.hair().setRotationX(0.0F);
        fixture.hair().setRotationY(0.0F);
        fixture.hair().setRotationZ(0.0F);
        fixture.hair().setPositionX(0.0F);
        fixture.hair().setPositionY(0.0F);
        fixture.hair().setPositionZ(0.0F);
        fixture.solver().solve(
                acceleration,
                0.15F,
                1.0F / 60.0F,
                false
        );
    }
}
