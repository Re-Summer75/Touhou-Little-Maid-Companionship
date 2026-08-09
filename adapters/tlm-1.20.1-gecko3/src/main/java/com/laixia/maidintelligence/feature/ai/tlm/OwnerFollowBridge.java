package com.laixia.maidintelligence.feature.ai.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMode;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerFollowPolicy;
import net.minecraft.world.entity.LivingEntity;

/**
 * Runs the owner-distance backstop against live TLM state.
 *
 * <p>The native follow task used to do two things in one method: walk her
 * toward her owner, and teleport her when walking had stopped being enough.
 * The first is now an intent, so only the second is left, and it is kept here
 * rather than in the mixin so that what counts as "too far" is one decision in
 * one place rather than a number repeated at every call site.
 */
public final class OwnerFollowBridge {
    private OwnerFollowBridge() {
    }

    /**
     * Teleports her to her owner when she has fallen too far behind.
     *
     * <p>The state tests are TLM's own, reproduced rather than called because
     * they are private to the task. Home mode is deliberately among them: a
     * maid told to stay somewhere is not lost when her owner walks off, she is
     * where she was put.
     *
     * @return whether she was teleported
     */
    public static boolean teleportIfStranded(EntityMaid maid) {
        if (FreedomMode.isHostOwned(maid)) {
            // The host has its own rule for a maid left behind, and in its own
            // modes that rule is the one the player is relying on.
            return false;
        }
        LivingEntity owner = maid.getOwner();
        if (owner == null
                || owner.isSpectator()
                || owner.isDeadOrDying()
                || owner.level() != maid.level()) {
            return false;
        }
        if (maid.isHomeModeEnable()) {
            return false;
        }
        if (!OwnerFollowPolicy.INSTANCE.shouldTeleport(
                maid.distanceToSqr(owner)
        )) {
            return false;
        }
        // Before asking whether she can move, not after. A maid on a seat
        // cannot, so testing that first would mean the one case the backstop
        // exists for — she stayed on a stool while her owner walked out of
        // range — is the one case it never fires in.
        MaidSeatAutonomyBridge.leaveSeatForFollow(maid);
        if (!maid.canBrainMoving() || !maid.teleportToOwner(owner)) {
            return false;
        }
        maid.getNavigationManager().resetNavigation();
        return true;
    }
}
