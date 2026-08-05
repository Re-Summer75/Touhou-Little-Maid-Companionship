package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationResourceKey;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmCoordinationClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;

import java.util.Objects;

/**
 * One slot inside one block: a cabinet's shelf rather than the cabinet.
 *
 * <p>The slot is part of the identity because two maids may take from the same
 * container at once without competing, so long as they reach for different
 * shelves.
 */
public final class ContainerSlotApproachTarget implements ApproachTarget {
    private final BlockPos position;
    private final int slot;

    public ContainerSlotApproachTarget(BlockPos position, int slot) {
        this.position = Objects.requireNonNull(position, "position");
        this.slot = slot;
    }

    public BlockPos position() {
        return position;
    }

    public int slot() {
        return slot;
    }

    @Override
    public PositionTracker tracker() {
        return new BlockPosTracker(position);
    }

    @Override
    public double distanceToSqr(EntityMaid maid) {
        return maid.distanceToSqr(
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D
        );
    }

    @Override
    public CoordinationResourceKey claimKey(ServerLevel level) {
        return TlmCoordinationClaims.containerSlot(level, position, slot);
    }

    @Override
    public String identity() {
        return position.asLong() + "/" + slot;
    }

    /** A block does not stop existing; whether it still holds anything is the errand's question. */
    @Override
    public boolean valid() {
        return true;
    }

    @Override
    public boolean matches(PositionTracker other) {
        return other instanceof BlockPosTracker tracker
                && position.equals(tracker.currentBlockPosition());
    }
}
