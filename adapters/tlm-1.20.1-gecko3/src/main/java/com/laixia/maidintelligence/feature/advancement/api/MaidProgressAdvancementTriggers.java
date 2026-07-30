package com.laixia.maidintelligence.feature.advancement.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;

/**
 * Reports companionship progression and cumulative statistics.
 */
public interface MaidProgressAdvancementTriggers {
    void fed(EntityMaid maid, ItemStack food);

    void level(EntityMaid maid, int level);

    void favorabilityLevel(EntityMaid maid, int level);

    void experienceGained(EntityMaid maid, int amount);

    void replaceStanding(EntityMaid maid, int level);
}
