package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.tlm.MaidSeatAutonomyBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

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
    /** Where she should be, resolved fresh because it may have moved. */
    @FunctionalInterface
    public interface Anchor {
        ApproachTarget locate(EntityMaid maid);
    }

    /** What she has to do to herself before the errand is possible at all. */
    @FunctionalInterface
    public interface Preparation {
        void apply(EntityMaid maid);
    }

    private static final Preparation NOTHING_TO_PREPARE = maid -> {
    };

    private final String name;
    private final Anchor anchor;
    private final Preparation preparation;

    public MaintainProximityErrand(String name, Anchor anchor) {
        this(name, anchor, NOTHING_TO_PREPARE);
    }

    public MaintainProximityErrand(
            String name,
            Anchor anchor,
            Preparation preparation
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.preparation = Objects.requireNonNull(preparation, "preparation");
    }

    /**
     * Keeping up with whoever tamed her.
     *
     * <p>Getting up off a seat is part of it. The native follow task used to do
     * this on her behalf, and with that gone a maid who had sat down on a stool
     * would have watched her owner leave without moving.
     */
    public static MaintainProximityErrand followingOwner() {
        return new MaintainProximityErrand(
                "follow_owner",
                maid -> {
                    LivingEntity owner = maid.getOwner();
                    return owner == null
                            || !owner.isAlive()
                            || owner.isSpectator()
                            ? null
                            : new EntityApproachTarget(owner);
                },
                MaidSeatAutonomyBridge::leaveSeatForFollow
        );
    }

    /**
     * Going back to where she has been told she belongs.
     *
     * <p>Answers nothing while home mode is off, so the intent needs no
     * separate condition for it — a maid without a home cannot be far from one.
     */
    public static MaintainProximityErrand returningHome() {
        return new MaintainProximityErrand("return_home", maid -> {
            if (!maid.isHomeModeEnable()) {
                return null;
            }
            BlockPos home = maid.getRestrictCenter();
            return home == null || BlockPos.ZERO.equals(home)
                    ? null
                    : new BlockApproachTarget(home);
        });
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
    public ApproachTarget find(EntityMaid maid, long gameTime) {
        return anchor.locate(maid);
    }

    /** The anchor may have died, logged out, or been unset mid-walk. */
    @Override
    public boolean stillWorthwhile(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        ApproachTarget current = anchor.locate(maid);
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
        return true;
    }
}
