package com.laixia.maidintelligence.feature.level.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidPickupEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.level.api.ExperienceSource;
import com.laixia.maidintelligence.feature.level.api.MaidLevelApi;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class LevelExperienceHandler {
    private final MaidLevelApi levelApi;

    public LevelExperienceHandler(MaidLevelApi levelApi) {
        this.levelApi = levelApi;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExperienceOrbPickup(MaidPickupEvent.ExperienceResult event) {
        EntityMaid maid = event.getMaid();
        ExperienceOrb orb = event.getExperienceOrb();
        if (maid.level().isClientSide || !orb.isAlive() || orb.tickCount <= 2 || orb.value <= 0) {
            return;
        }
        levelApi.awardExperience(maid, orb.value, ExperienceSource.EXPERIENCE_ORB);
    }
}
