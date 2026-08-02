package com.laixia.maidintelligence.feature.ai.application;

import com.laixia.maidintelligence.feature.ai.api.AiOptimizationSnapshot;
import com.laixia.maidintelligence.feature.ai.api.MaidAiOptimizationApi;
import com.laixia.maidintelligence.feature.ai.api.MaidAiTuning;
import com.laixia.maidintelligence.feature.ai.domain.CombatThreatKind;

import java.util.Objects;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public final class DefaultMaidAiOptimizationService
        implements MaidAiOptimizationApi {
    private final Supplier<MaidAiTuning> tuning;
    private final LongAdder pathCacheHits = new LongAdder();
    private final LongAdder pathComputations = new LongAdder();
    private final LongAdder pickupCandidates = new LongAdder();
    private final LongAdder pickupSelections = new LongAdder();
    private final LongAdder brainTickSamples = new LongAdder();
    private final LongAdder brainTickTotalNanos = new LongAdder();
    private final LongAccumulator brainTickMaxNanos =
            new LongAccumulator(Long::max, 0L);
    private final LongAdder activityRadiusExpansions = new LongAdder();
    private final LongAdder combatScans = new LongAdder();
    private final LongAdder combatCandidates = new LongAdder();
    private final LongAdder combatCandidateTruncations = new LongAdder();
    private final LongAdder maidAttackerTargets = new LongAdder();
    private final LongAdder ownerAttackerTargets = new LongAdder();
    private final LongAdder ownerTargetTargets = new LongAdder();
    private final LongAdder proactiveHostileTargets = new LongAdder();

    public DefaultMaidAiOptimizationService(
            Supplier<MaidAiTuning> tuning
    ) {
        this.tuning = Objects.requireNonNull(tuning, "tuning");
    }

    @Override
    public MaidAiTuning tuning() {
        MaidAiTuning current = tuning.get();
        return current == null ? MaidAiTuning.defaults() : current;
    }

    @Override
    public void recordPathCacheHit() {
        pathCacheHits.increment();
    }

    @Override
    public void recordPathComputation() {
        pathComputations.increment();
    }

    @Override
    public void recordPickupScan(int candidates, int selected) {
        pickupCandidates.add(Math.max(0, candidates));
        pickupSelections.add(Math.max(0, selected));
    }

    @Override
    public void recordBrainTick(long elapsedNanos) {
        long safeElapsed = Math.max(0L, elapsedNanos);
        brainTickSamples.increment();
        brainTickTotalNanos.add(safeElapsed);
        brainTickMaxNanos.accumulate(safeElapsed);
    }

    @Override
    public void recordActivityRadiusExpansion() {
        activityRadiusExpansions.increment();
    }

    @Override
    public void recordCombatScan(int candidates, boolean truncated) {
        combatScans.increment();
        combatCandidates.add(Math.max(0, candidates));
        if (truncated) {
            combatCandidateTruncations.increment();
        }
    }

    @Override
    public void recordCombatTarget(CombatThreatKind kind) {
        switch (Objects.requireNonNull(kind, "kind")) {
            case MAID_ATTACKER -> maidAttackerTargets.increment();
            case OWNER_ATTACKER -> ownerAttackerTargets.increment();
            case OWNER_TARGET -> ownerTargetTargets.increment();
            case PROACTIVE_HOSTILE -> proactiveHostileTargets.increment();
        }
    }

    @Override
    public AiOptimizationSnapshot snapshot() {
        return new AiOptimizationSnapshot(
                pathCacheHits.sum(),
                pathComputations.sum(),
                pickupCandidates.sum(),
                pickupSelections.sum(),
                brainTickSamples.sum(),
                brainTickTotalNanos.sum(),
                brainTickMaxNanos.get(),
                activityRadiusExpansions.sum(),
                combatScans.sum(),
                combatCandidates.sum(),
                combatCandidateTruncations.sum(),
                maidAttackerTargets.sum(),
                ownerAttackerTargets.sum(),
                ownerTargetTargets.sum(),
                proactiveHostileTargets.sum()
        );
    }

    @Override
    public void resetMetrics() {
        pathCacheHits.reset();
        pathComputations.reset();
        pickupCandidates.reset();
        pickupSelections.reset();
        brainTickSamples.reset();
        brainTickTotalNanos.reset();
        brainTickMaxNanos.reset();
        activityRadiusExpansions.reset();
        combatScans.reset();
        combatCandidates.reset();
        combatCandidateTruncations.reset();
        maidAttackerTargets.reset();
        ownerAttackerTargets.reset();
        ownerTargetTargets.reset();
        proactiveHostileTargets.reset();
    }
}
