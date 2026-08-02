package com.laixia.maidintelligence.feature.ai.domain;

import com.laixia.maidintelligence.feature.ai.api.MaidAiTuning;

/**
 * Pure combat acquisition eligibility, ranking and scan cadence.
 */
public final class CombatReactionPolicy {
    public static final CombatReactionPolicy INSTANCE =
            new CombatReactionPolicy();

    private CombatReactionPolicy() {
    }

    public boolean eligible(
            CombatThreatKind kind,
            boolean alive,
            boolean attackable,
            boolean visible,
            boolean withinRange,
            boolean recent
    ) {
        if (!alive || !attackable || !withinRange) {
            return false;
        }
        if (kind == CombatThreatKind.PROACTIVE_HOSTILE) {
            return visible;
        }
        return recent;
    }

    public boolean outranks(
            CombatThreatKind candidateKind,
            double candidateDistanceSquared,
            CombatThreatKind currentKind,
            double currentDistanceSquared
    ) {
        if (currentKind == null) {
            return true;
        }
        if (candidateKind.priority() != currentKind.priority()) {
            return candidateKind.priority() < currentKind.priority();
        }
        return candidateDistanceSquared < currentDistanceSquared;
    }

    public int scanInterval(
            boolean ownerStationary,
            MaidAiTuning.CombatReaction tuning
    ) {
        return ownerStationary
                ? tuning.stationaryScanInterval()
                : tuning.movingScanInterval();
    }

    public boolean shouldScan(
            long gameTick,
            int maidIdentity,
            boolean ownerStationary,
            MaidAiTuning.CombatReaction tuning
    ) {
        int interval = Math.max(1, scanInterval(ownerStationary, tuning));
        return Math.floorMod(gameTick + maidIdentity, interval) == 0L;
    }
}
