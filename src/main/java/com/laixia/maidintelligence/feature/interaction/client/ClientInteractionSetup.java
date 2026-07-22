package com.laixia.maidintelligence.feature.interaction.client;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

@OnlyIn(Dist.CLIENT)
public final class ClientInteractionSetup {
    private ClientInteractionSetup() {
    }

    public static void initialize(IEventBus modEventBus, IEventBus gameEventBus) {
        modEventBus.addListener(ClientInteractionSetup::registerReloadListener);
        gameEventBus.addListener(ClientInteractionSetup::onClientTick);
    }

    private static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            DynamicMaidFaceTracker.clear();
            MaidEatingParticleEffect.clearPendingEffects();
        });
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MaidEatingParticleEffect.tickPendingEffects();
        }
    }
}
