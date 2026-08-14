package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * The part of an errand that is actually about the errand.
 *
 * <p>Going somewhere and reserving what is there is the same work every time,
 * and it is the work that must not be got wrong twice in different ways — one
 * place writing movement and one place claiming resources cannot disagree with
 * itself. {@link ApproachAndCommitAction} owns that. What is left here is what
 * genuinely differs between fetching a meal, taking a drop, and putting down a
 * boat: which thing to go to, and what to do on arrival.
 */
public interface Errand {
    /**
     * Names claims and release reasons, so a stuck claim can be traced back to
     * the errand that took it.
     */
    String name();

    /**
     * Whether arriving somewhere means taking something nobody else may have.
     *
     * <p>Fetching a meal does; keeping near her owner does not, and reserving
     * him would be actively wrong — the first maid to set off would hold the
     * only claim and every other maid in the household would stop following.
     * Errands about being somewhere rather than taking something say false.
     */
    default boolean requiresClaim() {
        return true;
    }

    /**
     * A chance to put herself in a state where the errand is possible at all,
     * run before she is checked for being free. Only errands that genuinely
     * need it should do anything here — getting up off a decorative chair is
     * reasonable, wandering off to fetch a tool is not.
     */
    default void prepare(EntityMaid maid) {
    }

    /**
     * The best thing to go to right now, or null if there is nothing worth
     * going to. Called every tick, so it must be cheap and must not reserve
     * anything itself.
     *
     * <p><b>And it must keep answering the same thing while she is on her
     * way.</b> {@link ApproachAndCommitAction} re-derives the target every tick
     * and compares {@link ApproachTarget#identity()} against the one she set
     * out for; a different identity is read as "the errand changed its mind",
     * so the walk target is erased and rewritten. Every errand here derives its
     * target from the world — an owner, a home, the nearest cabinet — and gets
     * that stability for free, which is why the requirement went unwritten
     * until an errand that <em>chose</em> its destination at random broke it.
     * She re-rolled a destination every tick and walked a block in a new
     * direction each time.
     *
     * <p>So an errand that picks rather than derives has to remember its pick.
     * The natural place to forget it again is {@link #commit}, which is called
     * exactly when the errand has finished with that target.
     */
    ApproachTarget find(EntityMaid maid, long gameTime);

    /**
     * 这一趟走多快，相对计划里写的速度的倍率。
     *
     * <p>默认 1.0——照计划走。存在的理由是计划参数是**每个动作一个常数**，而有些
     * 差事的"多快"该按趟变：一段恒定速度的散步和恒定长度的停顿一样，一眼看得出
     * 是机器。
     *
     * <p>倍率而不是绝对速度：计划里那个数仍然是唯一的标称值，服务器改它照样有效，
     * 而这里只表达"这一趟相对它快一点还是慢一点"。
     *
     * <p>与目标一样，它在一趟之内必须稳定——每 tick 重掷会让她走走停停地抽搐。
     */
    default double paceFactor(EntityMaid maid) {
        return 1.0D;
    }

    /**
     * Do the thing, now that she is there and holds the claim.
     *
     * @return whether it was done; false releases the claim and fails the step
     */
    boolean commit(EntityMaid maid, ApproachTarget target, long gameTime);

    /**
     * Whether a target already being walked to is still worth reaching. The
     * default accepts anything the target itself still considers valid, which
     * is right for errands whose target cannot go stale in some other way.
     */
    default boolean stillWorthwhile(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        return target.valid();
    }
}
