package com.laixia.maidintelligence.feature.status.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;
import net.minecraft.world.item.ItemStack;

public interface MaidStatusApi {
    MaidStatusState getState(EntityMaid maid);

    void setHunger(EntityMaid maid, int hunger);

    void captureFoodNutrition(EntityMaid maid, ItemStack food);
}
