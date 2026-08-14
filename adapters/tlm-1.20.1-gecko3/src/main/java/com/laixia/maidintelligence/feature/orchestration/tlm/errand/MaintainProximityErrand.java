package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.Objects;

/**
 * Be near something. Which something is the only variable.
 *
 * <p>Keeping up with her owner and going back to her home look like different
 * behaviours and are the same errand: walk toward an anchor while too far from
 * it, and be done on arrival. One is a creature that moves and one is a place
 * that does not, which the target abstraction already absorbs.
 *
 * <p>Nothing is reserved. Several maids may follow one owner or share one home
 * without competing, and a claim here would mean the first to set off was the
 * only one allowed to arrive.
 *
 * <p>Being done on arrival rather than running forever is deliberate. Her
 * intent is re-ranked continuously, so drifting away simply makes this the best
 * thing to do again — while an errand that never finished would hold the floor
 * against everything else she might want.
 */
public final class MaintainProximityErrand implements Errand {
    /**
     * Where she should be, resolved fresh because it may have moved.
     *
     * <p>Takes the tick because an anchor that <em>chooses</em> rather than
     * derives has to remember its choice, and a memory that cannot expire is
     * how she ends up walking at an unreachable spot forever.
     */
    @FunctionalInterface
    public interface Anchor {
        ApproachTarget locate(EntityMaid maid, long gameTime);
    }

    /** What she has to do to herself before the errand is possible at all. */
    @FunctionalInterface
    public interface Preparation {
        void apply(EntityMaid maid);
    }

    /**
     * Whether the target she set out for is still the right one.
     *
     * <p>A variation point rather than one rule, because "still the right one"
     * genuinely differs. An anchor that can be re-derived — her owner, her home
     * — is checked by deriving it again and comparing. An anchor that was
     * <em>chosen</em> cannot be: re-deriving a random spot yields a different
     * spot, so the errand would abandon itself on its first tick and she would
     * stand there re-rolling a destination forever.
     */
    @FunctionalInterface
    public interface Validity {
        boolean holds(EntityMaid maid, ApproachTarget target);
    }

    /**
     * What the errand does once it has finished with a target.
     *
     * <p>Only errands that <em>chose</em> their destination need this. A
     * derived anchor can be recomputed at any time, so there is nothing to
     * forget; a chosen one has to be remembered for the whole walk (see
     * {@link Errand#find}) and therefore has to be forgotten at some point, or
     * she arrives once and stands there for good.
     */
    @FunctionalInterface
    public interface Completion {
        void reached(EntityMaid maid, long gameTime);
    }

    /**
     * 这一趟走多快，相对计划里写的速度。
     *
     * <p>又一个只有"挑"目的地的差事才需要的东西：推导出来的锚点每趟都是同一个地方，
     * 快慢没有"这一趟"可言；而散步每趟去处不同，恒定的速度会让它看起来是机器。
     */
    @FunctionalInterface
    public interface Pace {
        double factor(EntityMaid maid);
    }

    /** 什么都不必先做。公开，因为按 band 分包后的差事要引用它。 */
    public static final Preparation NOTHING_TO_PREPARE = maid -> {
    };

    private final String name;
    private final Anchor anchor;
    private final Preparation preparation;
    private final Validity validity;
    private final Completion completion;
    private final Pace pace;

    public MaintainProximityErrand(String name, Anchor anchor) {
        this(name, anchor, NOTHING_TO_PREPARE);
    }

    public MaintainProximityErrand(
            String name,
            Anchor anchor,
            Preparation preparation
    ) {
        this(name, anchor, preparation, null);
    }

    public MaintainProximityErrand(
            String name,
            Anchor anchor,
            Preparation preparation,
            Validity validity
    ) {
        this(name, anchor, preparation, validity, null);
    }

    public MaintainProximityErrand(
            String name,
            Anchor anchor,
            Preparation preparation,
            Validity validity,
            Completion completion
    ) {
        this(name, anchor, preparation, validity, completion, null);
    }

    public MaintainProximityErrand(
            String name,
            Anchor anchor,
            Preparation preparation,
            Validity validity,
            Completion completion,
            Pace pace
    ) {
        this.pace = pace;
        this.name = Objects.requireNonNull(name, "name");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.preparation = Objects.requireNonNull(preparation, "preparation");
        this.validity = validity;
        this.completion = completion;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean requiresClaim() {
        return false;
    }

    @Override
    public void prepare(EntityMaid maid) {
        preparation.apply(maid);
    }

    @Override
    public double paceFactor(EntityMaid maid) {
        return pace == null ? 1.0D : pace.factor(maid);
    }

    @Override
    public ApproachTarget find(EntityMaid maid, long gameTime) {
        return anchor.locate(maid, gameTime);
    }

    /** The anchor may have died, logged out, or been unset mid-walk. */
    @Override
    public boolean stillWorthwhile(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        if (validity != null) {
            return validity.holds(maid, target);
        }
        ApproachTarget current = anchor.locate(maid, gameTime);
        return current != null
                && current.identity().equals(target.identity());
    }

    /**
     * Arriving is the whole errand. There is nothing to pick up and nothing to
     * open, so reaching the anchor is success by itself.
     */
    @Override
    public boolean commit(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        if (completion != null) {
            completion.reached(maid, gameTime);
        }
        return true;
    }
}
