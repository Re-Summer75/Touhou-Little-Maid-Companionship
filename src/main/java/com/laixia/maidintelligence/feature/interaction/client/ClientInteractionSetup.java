package com.laixia.maidintelligence.feature.interaction.client;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

@OnlyIn(Dist.CLIENT)
public final class ClientInteractionSetup {
    private ClientInteractionSetup() {
    }

    public static void initialize(IEventBus modEventBus) {
        modEventBus.addListener(ClientInteractionSetup::registerReloadListener);
    }

    private static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                DynamicMaidFaceTracker.clear()
        );
    }
}
