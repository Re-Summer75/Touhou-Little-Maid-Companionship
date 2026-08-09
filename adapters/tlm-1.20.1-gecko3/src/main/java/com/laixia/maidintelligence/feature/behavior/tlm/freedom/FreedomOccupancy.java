package com.laixia.maidintelligence.feature.behavior.tlm.freedom;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;

/**
 * Whether she is free to act on her own plans right now.
 *
 * <p>What is left of a much larger classifier. That one answered "who currently
 * owns her movement", ranking this mod's plans against a dozen host behaviours
 * and an owner-command override — a question free mode stopped being able to
 * ask, because those behaviours are no longer registered and there is nobody to
 * rank against.
 *
 * <p>The half that survives is the half that was never about the host's AI: a
 * maid told to sit, asleep, on a lead, or riding something is unavailable for
 * reasons the player or the world imposed, and no amount of deciding changes
 * that. Plans still have to ask.
 *
 * <p>Deliberately not a snapshot object with levels and reasons. Every caller
 * wanted one boolean, and the ceremony around it is what made the old version
 * look like it was doing something the simple check could not.
 */
public final class FreedomOccupancy {
    private FreedomOccupancy() {
    }

    /**
     * Whether something outside her judgement has already claimed her.
     *
     * <p>Home mode counts only while she is actually outside her home area —
     * being home is a place, not an occupation. Getting that backwards once
     * switched off every companion intent permanently: she sat at home doing
     * nothing, hungry, with food at her feet.
     */
    public static boolean claimed(EntityMaid maid) {
        return maid.isSleeping()
                || maid.isLeashed()
                || maid.isOrderedToSit()
                || maid.isUsingItem()
                || (maid.isMaidInSittingPose() && !onPassiveSeat(maid))
                || (maid.isHomeModeEnable() && !maid.isWithinRestriction());
    }

    /** Whether she is free — the reading most call sites want. */
    public static boolean available(EntityMaid maid) {
        return !claimed(maid);
    }

    /**
     * A seat she chose to sit on, as opposed to a pose she was put into.
     *
     * <p>The difference decides whether sitting blocks a plan: she can get off
     * a chair by herself, so wanting to be elsewhere is enough to end it.
     */
    public static boolean isPassiveSeat(Entity vehicle) {
        return vehicle != null
                && (vehicle.getType() == EntityChair.TYPE
                || vehicle.getType() == EntitySit.TYPE);
    }

    private static boolean onPassiveSeat(EntityMaid maid) {
        return isPassiveSeat(maid.getVehicle());
    }
}
