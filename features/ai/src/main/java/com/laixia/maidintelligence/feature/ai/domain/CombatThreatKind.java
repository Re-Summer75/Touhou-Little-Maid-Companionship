package com.laixia.maidintelligence.feature.ai.domain;

/**
 * Stable provenance used to rank and diagnose combat acquisition.
 */
public enum CombatThreatKind {
    MAID_ATTACKER(0),
    OWNER_ATTACKER(1),
    OWNER_TARGET(2),
    PROACTIVE_HOSTILE(3);

    private final int priority;

    CombatThreatKind(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
