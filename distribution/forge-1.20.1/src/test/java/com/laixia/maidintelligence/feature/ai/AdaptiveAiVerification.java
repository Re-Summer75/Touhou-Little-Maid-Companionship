package com.laixia.maidintelligence.feature.ai;

import com.laixia.maidintelligence.feature.ai.api.MaidAiTuning;
import com.laixia.maidintelligence.feature.ai.domain.CombatReactionPolicy;
import com.laixia.maidintelligence.feature.ai.domain.CombatThreatKind;
import com.laixia.maidintelligence.feature.ai.domain.DynamicActivityRadiusPolicy;
import com.laixia.maidintelligence.feature.ai.domain.OwnerMotionTracker;

public final class AdaptiveAiVerification {
    private AdaptiveAiVerification() {
    }

    public static void main(String[] args) {
        verifiesOwnerMotionHysteresis();
        verifiesDynamicRadiusBoundaries();
        verifiesCombatThreatRankingAndCadence();
        System.out.println("Adaptive maid AI verification passed.");
    }

    private static void verifiesOwnerMotionHysteresis() {
        OwnerMotionTracker tracker = new OwnerMotionTracker();
        for (long tick = 1L; tick < 20L; tick++) {
            require(
                    !tracker.observe(tick, 7L, false, 20, 3),
                    "Owner became stationary before the confirmation window"
            );
        }
        require(
                tracker.observe(20L, 7L, false, 20, 3),
                "Owner did not become stationary after twenty ticks"
        );
        require(
                tracker.observe(20L, 7L, true, 20, 3),
                "Repeated sampling in one tick changed motion state"
        );
        require(
                tracker.observe(21L, 7L, true, 20, 3)
                        && tracker.observe(22L, 7L, true, 20, 3),
                "Owner movement collapsed expansion too early"
        );
        require(
                !tracker.observe(23L, 7L, true, 20, 3),
                "Three moving ticks did not restore the base radius"
        );
        require(
                !tracker.observe(24L, 8L, false, 20, 3),
                "Changing owner retained the previous motion state"
        );
    }

    private static void verifiesDynamicRadiusBoundaries() {
        MaidAiTuning.ActivityRadius tuning =
                MaidAiTuning.defaults().activityRadius();
        DynamicActivityRadiusPolicy policy =
                DynamicActivityRadiusPolicy.INSTANCE;
        require(
                policy.effectiveRadius(
                        8.0F,
                        true,
                        DynamicActivityRadiusPolicy.ActivityKind.IDLE,
                        tuning
                ) == 12.0F,
                "Idle stationary radius default changed"
        );
        require(
                policy.effectiveRadius(
                        8.0F,
                        true,
                        DynamicActivityRadiusPolicy.ActivityKind.WORK,
                        tuning
                ) == 16.0F,
                "Work stationary radius default changed"
        );
        require(
                policy.effectiveRadius(
                        8.0F,
                        true,
                        DynamicActivityRadiusPolicy.ActivityKind.COMBAT,
                        tuning
                ) == 20.0F,
                "Combat stationary radius default changed"
        );
        require(
                policy.effectiveRadius(
                        20.0F,
                        true,
                        DynamicActivityRadiusPolicy.ActivityKind.COMBAT,
                        tuning
                ) == 24.0F,
                "Expanded radius exceeded the hard cap"
        );
        require(
                policy.effectiveRadius(
                        8.0F,
                        false,
                        DynamicActivityRadiusPolicy.ActivityKind.COMBAT,
                        tuning
                ) == 8.0F,
                "Moving owner retained an expanded radius"
        );
    }

    private static void verifiesCombatThreatRankingAndCadence() {
        CombatReactionPolicy policy = CombatReactionPolicy.INSTANCE;
        require(
                policy.outranks(
                        CombatThreatKind.MAID_ATTACKER,
                        100.0D,
                        CombatThreatKind.OWNER_TARGET,
                        4.0D
                ),
                "Direct maid attacker did not outrank owner assistance"
        );
        require(
                !policy.eligible(
                        CombatThreatKind.PROACTIVE_HOSTILE,
                        true,
                        true,
                        false,
                        true,
                        true
                ),
                "Invisible proactive target was accepted"
        );
        require(
                policy.eligible(
                        CombatThreatKind.OWNER_ATTACKER,
                        true,
                        true,
                        false,
                        true,
                        true
                ),
                "Recent owner attacker incorrectly required line of sight"
        );
        MaidAiTuning.CombatReaction tuning =
                MaidAiTuning.defaults().combatReaction();
        require(
                policy.scanInterval(true, tuning) == 10
                        && policy.scanInterval(false, tuning) == 20,
                "Adaptive combat scan cadence changed"
        );
        require(
                policy.shouldScan(9L, 1, true, tuning),
                "Staggered stationary scan did not trigger"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
