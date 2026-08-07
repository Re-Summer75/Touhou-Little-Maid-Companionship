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
    /**
     * What sharing this place means, for errands that reserve it.
     *
     * <p>Two blocks at the same coordinates are not the same resource when one
     * is a spot to put a boat down and the other is a chessboard to sit at, so
     * the errand says which it means rather than every block sharing one
     * namespace.
     */
    public enum Exclusivity {
        /** Somewhere to stand or place something. */
        PLACEMENT,
        /** Somewhere that seats exactly one maid. */
        JOY_BLOCK
    }

    private final BlockPos position;
    private final Exclusivity exclusivity;

    public BlockApproachTarget(BlockPos position) {
        this(position, Exclusivity.PLACEMENT);
    }

    public BlockApproachTarget(BlockPos position, Exclusivity exclusivity) {
        this.position = Objects.requireNonNull(position, "position");
        this.exclusivity = Objects.requireNonNull(exclusivity, "exclusivity");
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
        return switch (exclusivity) {
            case PLACEMENT -> TlmCoordinationClaims.placement(level, position);
            case JOY_BLOCK -> TlmCoordinationClaims.joyBlock(level, position);
        };
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
