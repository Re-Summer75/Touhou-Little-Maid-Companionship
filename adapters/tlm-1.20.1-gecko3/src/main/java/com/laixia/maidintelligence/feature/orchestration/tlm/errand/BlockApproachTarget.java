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
 * A place, with nothing in it to take: a home to stand near rather than a
 * container to open.
 */
public final class BlockApproachTarget implements ApproachTarget {
    private final BlockPos position;

    public BlockApproachTarget(BlockPos position) {
        this.position = Objects.requireNonNull(position, "position");
    }

    public BlockPos position() {
        return position;
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

    /**
     * Only meaningful for an errand that reserves where it is going. Standing
     * somewhere is not exclusive, so errands using this normally decline to
     * claim at all.
     */
    @Override
    public CoordinationResourceKey claimKey(ServerLevel level) {
        return TlmCoordinationClaims.placement(level, position);
    }

    @Override
    public String identity() {
        return Long.toString(position.asLong());
    }

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
