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
    /**
     * 还没动手，但已经站在她所守之处旁边。
     *
     * <p>跟随时是主人，家园模式是锚点。它是**即将发生的**那一下——等到它真的
     * 抡下去才升到 {@code ATTACKING_OWNER}，就晚了一击。
     *
     * <p>做成一个档而不是一个连续的"离锚点多远"，是这一条唯一能成立的形式。
     * 按距离严格排序试过，实测把僵尸局从每局五六杀打成零到一杀：那个量每 tick
     * 都在微动，于是目标不停翻面，而**任何一次改选都会把她的脚或头一起转过去**
     * ——本文件的注释里已经为血量记过三次同样的失败。离散的档不会抖：进了就是
     * 进了，出了才出。
     */
    NEAR_WARD,
    /** Hostile, but not yet part of anyone's fight. */
    UNENGAGED;

    /** Whether this one outranks {@code other} as a thing to hit first. */
    public boolean outranks(ThreatRelation other) {
        return ordinal() < other.ordinal();
    }
}
