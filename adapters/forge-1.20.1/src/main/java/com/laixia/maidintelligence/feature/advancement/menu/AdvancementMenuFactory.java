package com.laixia.maidintelligence.feature.advancement.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * TLM-owned container factory consumed by the Forge menu registry.
 */
@FunctionalInterface
public interface AdvancementMenuFactory {
    AbstractContainerMenu create(
            int windowId,
            Inventory inventory,
            FriendlyByteBuf data
    );
}
