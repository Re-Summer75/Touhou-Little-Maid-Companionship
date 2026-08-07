package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.block.BlockJoy;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitPoi;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityJoy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;

/**
 * Go and do something for its own sake: read, play chess, use the computer.
 *
 * <p>TLM already had this as a behaviour of its own, and that was the problem.
 * It sat in the idle activity at priority seven, decided on its own that a
 * bookshelf nearby was worth sitting at, and the seat it produced reads as soft
 * occupancy — so a maid who had settled in to read could no longer be reached
 * by eleven of her thirteen companion intents, including every one of the ways
 * she feeds herself. She would sit at a chessboard and starve.
 *
 * <p>Nothing here changes what the pastime is or how it looks. Sitting is still
 * {@link BlockJoy#startMaidSit}, and the advancement TLM awards for it is still
 * awarded. What moved is only the decision to go: it is now weighed against
 * being hungry, being far from her owner, and everything else she might want,
 * instead of being taken before any of them are asked.
 */
public final class JoyBlockErrand implements Errand {
    private final int searchRange;

    public JoyBlockErrand(int searchRange) {
        this.searchRange = searchRange;
    }

    @Override
    public String name() {
        return "joy_block";
    }

    /**
     * A joy block seats one maid. Two heading for the same bookshelf is a race
     * that has to be settled before either sits, not after both do.
     */
    @Override
    public boolean requiresClaim() {
        return true;
    }

    /**
     * Gets up off whatever she is already on. Without this she would claim a
     * chessboard from a stool across the room and never arrive.
     */
    @Override
    public void prepare(EntityMaid maid) {
        if (maid.isPassenger()) {
            maid.stopRiding();
        }
    }

    @Override
    public ApproachTarget find(EntityMaid maid, long gameTime) {
        if (maid.isPassenger() || !(maid.level() instanceof ServerLevel level)) {
            return null;
        }
        PoiManager poi = level.getPoiManager();
        return poi.getInRange(
                        type -> type.get().equals(InitPoi.JOY_BLOCK.get()),
                        maid.getBrainSearchPos(),
                        searchRange,
                        PoiManager.Occupancy.ANY
                )
                .map(PoiRecord::getPos)
                .filter(position -> usable(level, maid, position))
                .min(Comparator.comparingDouble(
                        position -> position.distSqr(maid.blockPosition())
                ))
                .map(position -> (ApproachTarget) new BlockApproachTarget(
                        position,
                        BlockApproachTarget.Exclusivity.JOY_BLOCK
                ))
                .orElse(null);
    }

    /** Somebody may have sat down at it while she crossed the room. */
    @Override
    public boolean stillWorthwhile(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        return !maid.isPassenger()
                && target instanceof BlockApproachTarget joy
                && maid.level() instanceof ServerLevel level
                && usable(level, maid, joy.position());
    }

    @Override
    public boolean commit(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        if (!(target instanceof BlockApproachTarget joy)
                || !(maid.level() instanceof ServerLevel level)
                || !usable(level, maid, joy.position())) {
            return false;
        }
        BlockPos position = joy.position();
        BlockState state = level.getBlockState(position);
        if (!(state.getBlock() instanceof BlockJoy block)) {
            return false;
        }
        block.startMaidSit(maid, state, level, position);
        if (maid.getOwner() instanceof ServerPlayer owner) {
            InitTrigger.MAID_EVENT.trigger(owner, TriggerType.MAID_SIT_JOY);
        }
        return maid.isPassenger();
    }

    /**
     * Whether that block is a pastime she can actually take up right now.
     *
     * <p>The restriction test is TLM's own and is kept: a maid with a home has
     * been told where she may be, and a chessboard outside it is not hers to
     * walk to.
     */
    private static boolean usable(
            ServerLevel level,
            EntityMaid maid,
            BlockPos position
    ) {
        if (!maid.isWithinRestriction(position)) {
            return false;
        }
        if (!(level.getBlockState(position).getBlock() instanceof BlockJoy)) {
            return false;
        }
        return level.getBlockEntity(position) instanceof TileEntityJoy joy
                && level.getEntity(joy.getSitId()) == null;
    }
}
