package com.laixia.maidintelligence.feature.behavior.domain.combat.threat;

/**
 * What a hostile is currently doing about the people she cares about.
 *
 * <p>Ordering is the point: she is a companion, not a survivor. Something
 * hitting her owner outranks something hitting her, because she can be repaired
 * and he cannot be un-killed. Declared in priority order so the comparison is
 * the enum's own.
 */
public enum ThreatRelation {
    /** It is hitting her owner. Everything else waits. */
    ATTACKING_OWNER,
    /** It is hitting her. Worth answering, but not before the above. */
    ATTACKING_MAID,
    /** Her owner is fighting it, so she helps finish it. */
    OWNER_TARGET,
    /** Hostile, but not yet part of anyone's fight. */
    UNENGAGED;

    /** Whether this one outranks {@code other} as a thing to hit first. */
    public boolean outranks(ThreatRelation other) {
        return ordinal() < other.ordinal();
    }
}
