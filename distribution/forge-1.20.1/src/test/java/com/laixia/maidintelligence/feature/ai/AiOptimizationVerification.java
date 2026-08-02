package com.laixia.maidintelligence.feature.ai;

import com.laixia.maidintelligence.feature.ai.api.AiOptimizationSnapshot;
import com.laixia.maidintelligence.feature.ai.api.MaidAiTuning;
import com.laixia.maidintelligence.feature.ai.application.DefaultMaidAiOptimizationService;
import com.laixia.maidintelligence.feature.ai.domain.CombatThreatKind;
import com.laixia.maidintelligence.feature.ai.domain.PathReachabilityCache;

import java.util.concurrent.atomic.AtomicReference;

public final class AiOptimizationVerification {
    private AiOptimizationVerification() {
    }

    public static void main(String[] args) {
        verifiesReachableResultReuse();
        verifiesCacheIdentityInvalidation();
        verifiesCacheExpiryAndClockRollback();
        verifiesOnlyRememberedSuccessesCanHit();
        verifiesRuntimeControlsAndMetrics();
        System.out.println("Maid AI optimization verification passed.");
    }

    private static void verifiesReachableResultReuse() {
        PathReachabilityCache cache = new PathReachabilityCache();
        int navigation = 101;
        cache.rememberReachable(100L, 20, 1L, 2L, 3L, navigation);

        require(
                cache.containsReachable(100L, 1L, 2L, 3L, navigation),
                "Fresh reachable result was not reused"
        );
        require(
                cache.containsReachable(119L, 1L, 2L, 3L, navigation),
                "Reachable result expired before its configured TTL"
        );
    }

    private static void verifiesCacheIdentityInvalidation() {
        PathReachabilityCache cache = new PathReachabilityCache();
        int landNavigation = 101;
        int waterNavigation = 202;
        cache.rememberReachable(
                20L,
                20,
                10L,
                30L,
                7L,
                landNavigation
        );

        require(
                !cache.containsReachable(
                        21L,
                        11L,
                        30L,
                        7L,
                        landNavigation
                ),
                "Moving the maid did not invalidate reachability"
        );
        require(
                !cache.containsReachable(
                        21L,
                        10L,
                        31L,
                        7L,
                        landNavigation
                ),
                "Moving the target did not invalidate reachability"
        );
        require(
                !cache.containsReachable(
                        21L,
                        10L,
                        30L,
                        8L,
                        landNavigation
                ),
                "Changing target identity did not invalidate reachability"
        );
        require(
                !cache.containsReachable(
                        21L,
                        10L,
                        30L,
                        7L,
                        waterNavigation
                ),
                "Changing navigation mode did not invalidate reachability"
        );
    }

    private static void verifiesCacheExpiryAndClockRollback() {
        PathReachabilityCache cache = new PathReachabilityCache();
        int navigation = 101;
        cache.rememberReachable(50L, 10, 1L, 2L, 0L, navigation);

        require(
                !cache.containsReachable(60L, 1L, 2L, 0L, navigation),
                "Reachable result survived its exclusive expiry tick"
        );
        cache.rememberReachable(50L, 10, 1L, 2L, 0L, navigation);
        require(
                !cache.containsReachable(49L, 1L, 2L, 0L, navigation),
                "Clock rollback reused a result from a future tick"
        );
    }

    private static void verifiesOnlyRememberedSuccessesCanHit() {
        PathReachabilityCache cache = new PathReachabilityCache();
        int navigation = 101;

        require(
                !cache.containsReachable(5L, 1L, 2L, 0L, navigation),
                "A failed or unknown route was treated as cached reachable"
        );
        cache.rememberReachable(5L, 0, 1L, 2L, 0L, navigation);
        require(
                !cache.containsReachable(5L, 1L, 2L, 0L, navigation),
                "Disabled caching retained a reachable result"
        );
    }

    private static void verifiesRuntimeControlsAndMetrics() {
        AtomicReference<MaidAiTuning> tuning =
                new AtomicReference<>(MaidAiTuning.defaults());
        DefaultMaidAiOptimizationService service =
                new DefaultMaidAiOptimizationService(tuning::get);

        service.recordPathCacheHit();
        service.recordPathComputation();
        service.recordPickupScan(12, 3);
        service.recordBrainTick(1_000L);
        service.recordBrainTick(3_000L);
        service.recordActivityRadiusExpansion();
        service.recordCombatScan(9, true);
        service.recordCombatTarget(CombatThreatKind.MAID_ATTACKER);
        service.recordCombatTarget(CombatThreatKind.OWNER_TARGET);
        AiOptimizationSnapshot snapshot = service.snapshot();

        require(service.enabled(), "Enabled supplier was not observed");
        require(
                service.reachablePathCacheTicks() == 20,
                "Configured reachable cache TTL was not observed"
        );
        require(!service.profilingEnabled(), "Profiling default changed");
        require(snapshot.pathRequests() == 2, "Path request metrics changed");
        require(
                Math.abs(snapshot.pathCacheHitRate() - 0.5D) < 1.0E-9D,
                "Path cache hit rate changed"
        );
        require(
                snapshot.pickupCandidates() == 12
                        && snapshot.pickupSelections() == 3,
                "Pickup scan metrics changed"
        );
        require(
                snapshot.brainTickSamples() == 2
                        && snapshot.averageBrainTickNanos() == 2_000L
                        && snapshot.brainTickMaxNanos() == 3_000L,
                "AI timing aggregation changed"
        );
        require(
                snapshot.activityRadiusExpansions() == 1
                        && snapshot.combatScans() == 1
                        && snapshot.combatCandidates() == 9
                        && snapshot.combatCandidateTruncations() == 1,
                "Adaptive AI scan metrics changed"
        );
        require(
                snapshot.combatTargets() == 2
                        && snapshot.maidAttackerTargets() == 1
                        && snapshot.ownerTargetTargets() == 1,
                "Combat target provenance metrics changed"
        );

        MaidAiTuning defaults = MaidAiTuning.defaults();
        tuning.set(new MaidAiTuning(
                new MaidAiTuning.Performance(false, 0, true),
                defaults.activityRadius(),
                defaults.combatReaction()
        ));
        require(service.reachablePathCacheTicks() == 0, "TTL was not clamped");
        require(service.profilingEnabled(), "Profiling supplier was not observed");
        require(!service.enabled(), "Enabled supplier update was not observed");

        service.resetMetrics();
        require(
                service.snapshot().equals(new AiOptimizationSnapshot(
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L
                )),
                "Metrics reset left accumulated values"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
