package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.MaidGazeRecallApi;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

/**
 * TLM implementation of the explicit gaze-recall behavior.
 */
public final class TlmMaidGazeRecallService
        implements MaidGazeRecallApi<Player, EntityMaid> {
    private static final int GAZE_SIGNAL_TTL_TICKS = 20;

    private final MaidIntentApi<EntityMaid> intents;

    public TlmMaidGazeRecallService(MaidIntentApi<EntityMaid> intents) {
        this.intents = Objects.requireNonNull(intents, "intents");
    }

    @Override
    public boolean tryRecall(Player owner, EntityMaid maid) {
        if (!validPair(owner, maid)) {
            return false;
        }
        return intents.signal(
                maid,
                CompanionIntentIds.GAZE_RECALL,
                maid.level().getGameTime(),
                GAZE_SIGNAL_TTL_TICKS
        );
    }

    private static boolean validPair(Player owner, EntityMaid maid) {
        return owner != null
                && maid != null
                && owner.isAlive()
                && !owner.isSpectator()
                && maid.isAlive()
                && owner.level() == maid.level()
                && maid.isTame()
                && owner.getUUID().equals(maid.getOwnerUUID());
    }

}
