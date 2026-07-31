package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.motion.TailCoalesceScenario;
import com.laixia.maidintelligence.feature.physics.client.motion.TailRateLimitScenario;
import com.laixia.maidintelligence.feature.physics.client.motion.TailSnapshotContinuityScenario;

/**
 * Aggregates TLM tail snapshot, rate-limit, and render-coalescing scenarios.
 */
final class TailAnimationContinuityVerification {
    private TailAnimationContinuityVerification() {
    }

    static void run() throws Exception {
        TailSnapshotContinuityScenario.run();
        TailRateLimitScenario.run();
        TailCoalesceScenario.run();
        TailRateLimitScenario.runRiceCakeSlowTail();
    }
}
