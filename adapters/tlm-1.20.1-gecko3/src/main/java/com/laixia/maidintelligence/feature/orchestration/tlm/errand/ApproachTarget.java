package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.PositionTracker;

/**
 * Somewhere a maid can be sent, and something she can claim once there.
 *
 * <p>Exists so the errand skeleton does not have to know whether it is walking
 * to a block or to an entity. Those differ in how they are tracked and in what
 * claiming them means, and in nothing else the skeleton cares about.
 */
public interface ApproachTarget {
    /** What the brain should be told to walk toward. */
    PositionTracker tracker();

    double distanceToSqr(EntityMaid maid);

    /**
     * The resource to hold while she is on her way. A block's slot and a loose
     * item are different kinds of thing to reserve, which is exactly why this
     * is asked of the target rather than assumed by the skeleton.
     */
    CoordinationResourceKey claimKey(ServerLevel level);

    /**
     * Stable across ticks for the same real target, so an errand already under
     * way can be recognised rather than re-claimed every tick.
     */
    String identity();

    /** Whether it is still worth going. */
    boolean valid();

    /**
     * Whether a tracker already written to the brain points here. Trackers are
     * rebuilt every call, so identity comparison would always say no.
     */
    boolean matches(PositionTracker other);
}
