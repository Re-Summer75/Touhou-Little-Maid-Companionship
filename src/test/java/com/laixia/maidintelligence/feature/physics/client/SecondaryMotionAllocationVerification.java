package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.atmosphere.client.wind.EnvironmentalWindField;
import com.laixia.maidintelligence.feature.physics.client.SecondaryMotionFixture.Fixture;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxies;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionScratch;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.management.ManagementFactory;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class SecondaryMotionAllocationVerification {
    private static final int WARMUP_FRAMES = 20_000;
    private static final int MEASURED_FRAMES = 4_000;
    private static final Vector3f WIND =
            new Vector3f(0.04F, 0.0F, -0.015F);

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
        AnimationTimelineClock timeline = new AnimationTimelineClock();
        for (int frame = 0; frame < WARMUP_FRAMES; frame++) {
            solveFrame(fixture, acceleration, timeline, frame);
        }
        long threadId = Thread.currentThread().getId();
        bean.getThreadAllocatedBytes(threadId);
        long before = bean.getThreadAllocatedBytes(threadId);
        for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
            solveFrame(
                    fixture,
                    acceleration,
                    timeline,
                    WARMUP_FRAMES + frame
            );
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;
        require(
                allocated == 0L,
                "Secondary-motion constraint hot path allocated "
                        + allocated + " bytes"
        );
        verifyGenericProxyAllocation(bean, threadId);
        verifyWindFieldAllocation(bean, threadId);
    }

    private static void solveFrame(
            Fixture fixture,
            Vector3f acceleration,
            AnimationTimelineClock timeline,
            int frame
    ) {
        fixture.solver().restoreAnimationPose();
        float phase = frame * (2.0F * (float) Math.PI / 120.0F);
        fixture.head().setRotationX(0.0F);
        fixture.head().setRotationY(0.0F);
        fixture.head().setRotationZ(
                0.15F * (float) Math.sin(phase)
        );
        fixture.head().setPositionX(
                0.5F + 0.25F * (float) Math.cos(phase)
        );
        fixture.head().setScaleX(
                1.15F + 0.05F * (float) Math.sin(phase)
        );
        fixture.head().setScaleY(0.85F);
        fixture.head().setScaleZ(1.05F);
        fixture.hair().setRotationX(0.0F);
        fixture.hair().setRotationY(0.0F);
        fixture.hair().setRotationZ(0.0F);
        fixture.hair().setPositionX(0.0F);
        fixture.hair().setPositionY(0.0F);
        fixture.hair().setPositionZ(0.0F);
        fixture.hair().setScaleX(0.90F);
        fixture.hair().setScaleY(1.10F);
        fixture.hair().setScaleZ(1.0F);
        fixture.solver().solve(
                acceleration,
                WIND,
                0.15F,
                timeline.advance(frame / 3.0D, false),
                false
        );
    }

    private static void verifyGenericProxyAllocation(
            com.sun.management.ThreadMXBean bean,
            long threadId
    ) {
        CollisionProxySet proxies = new CollisionProxySet(
                CollisionProxies.plane(
                        0,
                        new Vector3f(2.0F, 0.0F, 0.0F),
                        new Vector3f(1.5F, 0.0F, 0.0F),
                        new Vector3f(1.0F, 0.0F, 0.0F),
                        0.0F,
                        1.0F
                ),
                CollisionProxies.sphere(
                        1,
                        new Vector3f(2.0F, 0.0F, 0.0F),
                        new Vector3f(),
                        1.25F,
                        0.0F,
                        1.0F
                ),
                CollisionProxies.capsule(
                        0,
                        new Vector3f(2.0F, 0.0F, 0.0F),
                        new Vector3f(0.0F, -1.0F, 0.0F),
                        new Vector3f(0.0F, 1.0F, 0.0F),
                        1.25F,
                        0.0F,
                        1.0F
                )
        );
        Quaternionf[] references = {
                new Quaternionf(),
                new Quaternionf()
        };
        Quaternionf root = new Quaternionf();
        CollisionScratch scratch = new CollisionScratch();
        Vector3f direction = new Vector3f();
        for (int frame = 0; frame < WARMUP_FRAMES; frame++) {
            projectFrame(
                    proxies,
                    references,
                    root,
                    scratch,
                    direction
            );
        }
        bean.getThreadAllocatedBytes(threadId);
        long before = bean.getThreadAllocatedBytes(threadId);
        for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
            projectFrame(
                    proxies,
                    references,
                    root,
                    scratch,
                    direction
            );
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;
        require(
                allocated == 0L,
                "Generic multi-proxy hot path allocated "
                        + allocated + " bytes"
        );
    }

    private static void verifyWindFieldAllocation(
            com.sun.management.ThreadMXBean bean,
            long threadId
    ) {
        Vector3f target = new Vector3f();
        Vector3f filtered = new Vector3f();
        for (int frame = 0; frame < WARMUP_FRAMES; frame++) {
            sampleWindFrame(target, filtered, frame);
        }
        bean.getThreadAllocatedBytes(threadId);
        long before = bean.getThreadAllocatedBytes(threadId);
        for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
            sampleWindFrame(target, filtered, WARMUP_FRAMES + frame);
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;
        require(
                allocated == 0L,
                "Environmental wind hot path allocated "
                        + allocated + " bytes"
        );
    }

    private static void sampleWindFrame(
            Vector3f target,
            Vector3f filtered,
            int frame
    ) {
        EnvironmentalWindField.targetInto(
                48.0D,
                -32.0D,
                frame / 3.0D,
                991,
                1171,
                0.06F,
                target
        );
        EnvironmentalWindField.smoothInto(
                filtered,
                target,
                1.0F / 60.0F,
                filtered
        );
    }

    private static void projectFrame(
            CollisionProxySet proxies,
            Quaternionf[] references,
            Quaternionf root,
            CollisionScratch scratch,
            Vector3f direction
    ) {
        direction.set(-1.0F, 0.0F, 0.0F);
        proxies.project(direction, references, root, scratch);
    }
}
