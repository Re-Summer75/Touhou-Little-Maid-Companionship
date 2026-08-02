package com.laixia.maidintelligence.feature.ai.api;

import com.laixia.maidintelligence.feature.ai.domain.CombatThreatKind;

/**
 * Runtime controls and diagnostics for behavior-preserving maid AI optimizations.
 */
public interface MaidAiOptimizationApi {
    MaidAiTuning tuning();

    default boolean enabled() {
        return tuning().performance().enabled();
    }

    default int reachablePathCacheTicks() {
        return tuning().performance().reachablePathCacheTicks();
    }

    default boolean profilingEnabled() {
        return tuning().performance().profilingEnabled();
    }

    void recordPathCacheHit();

    void recordPathComputation();

    void recordPickupScan(int candidates, int selected);

    void recordBrainTick(long elapsedNanos);

    void recordActivityRadiusExpansion();

    void recordCombatScan(int candidates, boolean truncated);

    void recordCombatTarget(CombatThreatKind kind);

    AiOptimizationSnapshot snapshot();

    void resetMetrics();
}
