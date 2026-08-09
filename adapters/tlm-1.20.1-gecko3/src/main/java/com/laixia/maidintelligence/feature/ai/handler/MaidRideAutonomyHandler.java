package com.laixia.maidintelligence.feature.ai.handler;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.tlm.MaidSeatAutonomyBridge;
import com.laixia.maidintelligence.feature.ai.tlm.OwnerFollowBridge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Gets a free-mode maid out of a seat her own plans need her out of.
 *
 * <p>Combat dismounting used to live here too, gated on the host's own attack
 * tasks. It went with them: this mod no longer decides anything for a maid on a
 * host work mode, and in free mode those tasks are not registered at all.
 */
public final class MaidRideAutonomyHandler {
    @SubscribeEvent
    public void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        MaidSeatAutonomyBridge.leaveOrdinaryBoatWhenOwnerLeaves(maid);
        // Teleporting a maid who has been left behind used to hang off the
        // host's own follow behaviour, through a Mixin. Free mode does not
        // register that behaviour any more, and losing the leash with it would
        // mean a maid stranded across a chunk boundary stays there forever —
        // a far worse outcome than the coupling it came from. Both bridges
        // decline outside free mode, so this tick is inert everywhere else.
        MaidSeatAutonomyBridge.leaveSeatForFollow(maid);
        OwnerFollowBridge.teleportIfStranded(maid);
    }
}
