package com.laixia.maidintelligence.feature.advancement.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.bridge.HoneySlideHandler;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidWorldAdvancementTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

public final class TlmHoneySlideHandler implements HoneySlideHandler {
    private final MaidWorldAdvancementTriggers triggers;

    public TlmHoneySlideHandler(MaidWorldAdvancementTriggers triggers) {
        this.triggers = Objects.requireNonNull(triggers, "triggers");
    }

    @Override
    public void onHoneySlide(Entity entity, BlockPos blockPosition) {
        if (entity instanceof EntityMaid maid
                && !maid.level().isClientSide()
                && maid.level().getGameTime() % 20L == 0L) {
            triggers.slidDownBlock(
                    maid,
                    maid.level().getBlockState(blockPosition)
            );
        }
    }
}
