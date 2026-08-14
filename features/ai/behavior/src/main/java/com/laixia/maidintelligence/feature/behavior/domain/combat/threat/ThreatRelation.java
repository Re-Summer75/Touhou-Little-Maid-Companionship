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
    UNENGAGED,

    /**
     * 已经锁定了别的东西——既不是她，也不是她主人，也不是同一个主人的另一个女仆。
     *
     * <p>和 {@link #UNENGAGED} 分开，因为对**承伤定价**而言两者是相反的：闲着的那
     * 一只下一秒就会发现她（原版索敌 goal 每十 tick 重扫一次），而锁着别人的那一只
     * 会一直打完手上那个——原版怪不会中途换目标。
     *
     * <p>这个区别在近战身上无关紧要，因为到达时钟本来就替她回答了"它是不是冲我
     * 来的"；在**远程**身上却是全部：骷髅的够到距离就是它的索敌范围，于是
     * {@code secondsToContact} 在十六格内恒为零、{@code convergenceWeight} 恒为一。
     * 一只在射牛的骷髅因此和一只正在瞄她的骷髅在账本里一模一样，而她据此把自己钉在
     * 远处不敢靠近——玩家报的正是这件事。
     */
    BUSY_ELSEWHERE;

    /** Whether this one outranks {@code other} as a thing to hit first. */
    public boolean outranks(ThreatRelation other) {
        return ordinal() < other.ordinal();
    }

    /**
     * 它是不是**已经**卷进了她或她主人的这一场。
     *
     * <p>用来回答"她得清掉多少东西才算完"——闲着的和忙着别处的都不算，她不必为了
     * 走过一片旁观者而备足弹药。
     */
    public boolean concernsHer() {
        return this != UNENGAGED && this != BUSY_ELSEWHERE;
    }

    /**
     * 它有没有可能把矛头转向她。
     *
     * <p>用来回答"有多少伤害会落在她身上"。闲着的算——它还没挑好目标，而她就在
     * 眼前；锁着别人的不算，直到它手上那个倒下为止，而那时它的关系会自己变。
     *
     * <p>这不是"忽略它"：它照旧在威胁清单里、照旧可以被选作目标、照旧计入她要打的
     * 那群人的血量。只是它的伤害不再被算进**她**的承伤。
     */
    public boolean mayTurnOnHer() {
        return this != BUSY_ELSEWHERE;
    }
}
