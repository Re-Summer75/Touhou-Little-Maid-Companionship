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
    /**
     * Whether finishing one target finishes the errand.
     *
     * <p>差事默认是**一趟一件**：走到、拿到、结束，由编排器决定下一步做什么。
     * 对取一份饭、坐下、走到主人身边都对——那些事做完就是做完了。
     *
     * <p>清扫不是。地上还有第二件东西时，"做完了"是假的，而按默认那样收工的代价
     * 是可见的：意图结束 → 编排器下一次评估（最快五 tick）→ 重新选中 → 重新找
     * 目标 → 重新写移动目标。**这中间她没有移动目标，于是站住、再起步。**实机
     * 表现就是捡一件停一下，而它不是任何一个参数调得掉的——冷却调成零、评估间隔
     * 对齐到编排器的节拍之后，那一停仍然在，因为它根本不在参数里。
     *
     * <p>答真的差事在一次激活里连着做：提交成功后不收工，下一 tick 直接找下一个
     * 目标接着走。地上清空时 {@link #find} 交白卷，那才是这一趟真正的终点。
     */
    default boolean sweeps() {
        return false;
    }

    /**
     * 正走向目标的每一 tick，给差事一次"再使点劲"的机会。
     *
     * <p>存在的理由是跳：**把跳跃算进"够不够得着"，并不会让她真的跳。**原版只在
     * 寻路需要迈台阶时才跳，而悬在两格高处的东西根本没有路可走——寻路当场报走不
     * 到，差事把它丢掉，实机看就是她对着一件明明跳一下就能拿到的东西毫无反应。
     *
     * <p>默认什么都不做。走到座位、走到柜子前都不需要额外动作，那些差事一个字
     * 都不用改。
     */
    default void whileApproaching(EntityMaid maid, ApproachTarget target) {
    }

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
