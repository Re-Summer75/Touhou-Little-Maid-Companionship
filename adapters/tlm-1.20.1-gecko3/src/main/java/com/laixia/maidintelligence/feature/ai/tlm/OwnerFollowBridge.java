package com.laixia.maidintelligence.feature.ai.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMode;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerFollowPolicy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

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
     * Whoever would pull her back, or {@code null} when nobody would.
     *
     * <p>The state tests are TLM's own, reproduced rather than called because
     * they are private to the task. Home mode is deliberately among them: a
     * maid told to stay somewhere is not lost when her owner walks off, she is
     * where she was put.
     *
     * <p>Shared with {@link #leashedReach} on purpose. The backstop firing and
     * her planning around it have to agree about whether there is a leash at
     * all — one of them believing in it while the other does not is either a
     * maid who refuses ground she is allowed to take, or a retreat that ends
     * in being snapped back.
     */
    public static LivingEntity leashHolder(EntityMaid maid) {
        if (FreedomMode.isHostOwned(maid)) {
            // The host has its own rule for a maid left behind, and in its own
            // modes that rule is the one the player is relying on.
            return null;
        }
        LivingEntity owner = maid.getOwner();
        if (owner == null
                || owner.isSpectator()
                || owner.isDeadOrDying()
                || owner.level() != maid.level()
                || maid.isHomeModeEnable()) {
            return null;
        }
        return owner;
    }

    /**
     * How far she may walk along a heading before the backstop would fire.
     *
     * <p>Infinite when nothing would pull her back, so a caller can take the
     * smaller of this and whatever else limits it without asking first.
     *
     * @param heading a horizontal unit vector
     */
    public static double leashedReach(
            EntityMaid maid,
            Vec3 from,
            Vec3 heading
    ) {
        LivingEntity owner = leashHolder(maid);
        if (owner == null) {
            return Double.POSITIVE_INFINITY;
        }
        Vec3 offset = from.subtract(owner.position());
        return OwnerFollowPolicy.INSTANCE.reachBeforeTeleport(
                offset.x, offset.y, offset.z, heading.x, heading.z
        );
    }

    /**
     * Whether somewhere she is thinking of standing is somewhere she may.
     *
     * <p>For destinations already chosen. A retreat decided several ticks ago
     * was inside the leash when it was decided and need not still be — her
     * owner has been walking the whole time.
     */
    public static boolean withinLeash(EntityMaid maid, Vec3 destination) {
        LivingEntity owner = leashHolder(maid);
        if (owner == null) {
            return true;
        }
        double radius = OwnerFollowPolicy.INSTANCE.planningRadius(
                maid.position().distanceTo(owner.position())
        );
        return destination.distanceToSqr(owner.position()) <= radius * radius;
    }

    /**
     * The nearest point to somewhere she wanted to go that she is allowed.
     *
     * <p>Pulled straight back along the line from her owner, so it is the
     * closest admissible point rather than merely an admissible one. This is
     * the blunt instrument: every destination she is ever given passes through
     * it, whatever produced it. Where the direction matters as much as the
     * distance — a retreat, which is choosing a heading and not a point — the
     * leash is applied earlier, per bearing, so that she picks a good direction
     * she is allowed rather than the best direction and then a haircut.
     */
    public static Vec3 clampToLeash(EntityMaid maid, Vec3 destination) {
        LivingEntity owner = leashHolder(maid);
        if (owner == null) {
            return destination;
        }
        Vec3 anchor = owner.position();
        double radius = OwnerFollowPolicy.INSTANCE.planningRadius(
                maid.position().distanceTo(anchor)
        );
        Vec3 offset = destination.subtract(anchor);
        double reach = offset.length();
        if (reach <= radius || reach <= 0.0D) {
            return destination;
        }
        return anchor.add(offset.scale(radius / reach));
    }

    /**
     * Teleports her to her owner when she has fallen too far behind.
     *
     * @return whether she was teleported
     */
    public static boolean teleportIfStranded(EntityMaid maid) {
        LivingEntity owner = leashHolder(maid);
        if (owner == null) {
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
