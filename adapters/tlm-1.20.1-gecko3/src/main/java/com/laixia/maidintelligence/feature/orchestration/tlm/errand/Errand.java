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
     */
    ApproachTarget find(EntityMaid maid, long gameTime);

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
