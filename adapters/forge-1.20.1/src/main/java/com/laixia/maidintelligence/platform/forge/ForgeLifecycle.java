package com.laixia.maidintelligence.platform.forge;

import net.minecraftforge.eventbus.api.IEventBus;

import java.util.Objects;

/**
 * Forge-only lifecycle dependencies; feature services never retain this value.
 */
public record ForgeLifecycle(IEventBus modEventBus, IEventBus gameEventBus) {
    public ForgeLifecycle {
        Objects.requireNonNull(modEventBus, "modEventBus");
        Objects.requireNonNull(gameEventBus, "gameEventBus");
    }
}
