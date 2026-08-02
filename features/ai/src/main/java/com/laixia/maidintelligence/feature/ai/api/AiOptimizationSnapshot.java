package com.laixia.maidintelligence.feature.ai.api;

public record AiOptimizationSnapshot(
        long pathCacheHits,
        long pathComputations,
        long pickupCandidates,
        long pickupSelections,
        long brainTickSamples,
        long brainTickTotalNanos,
        long brainTickMaxNanos,
        long activityRadiusExpansions,
        long combatScans,
        long combatCandidates,
        long combatCandidateTruncations,
        long maidAttackerTargets,
        long ownerAttackerTargets,
        long ownerTargetTargets,
        long proactiveHostileTargets
) {
    public long pathRequests() {
        return pathCacheHits + pathComputations;
    }

    public double pathCacheHitRate() {
        long requests = pathRequests();
        return requests == 0 ? 0.0D : (double) pathCacheHits / requests;
    }

    public long averageBrainTickNanos() {
        return brainTickSamples == 0 ? 0L : brainTickTotalNanos / brainTickSamples;
    }

    public long combatTargets() {
        return maidAttackerTargets
                + ownerAttackerTargets
                + ownerTargetTargets
                + proactiveHostileTargets;
    }
}
