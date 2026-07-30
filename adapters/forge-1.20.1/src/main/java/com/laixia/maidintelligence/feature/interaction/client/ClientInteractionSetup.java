package com.laixia.maidintelligence.feature.interaction.client;

import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Forge client lifecycle for TLM face tracking and particle state.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientInteractionSetup
        implements Consumer<ForgeLifecycle> {
    private final Runnable resourceReloader;
    private final Runnable endTickHandler;

    public ClientInteractionSetup(
            Runnable resourceReloader,
            Runnable endTickHandler
    ) {
        this.resourceReloader = Objects.requireNonNull(
                resourceReloader,
                "resourceReloader"
        );
        this.endTickHandler = Objects.requireNonNull(
                endTickHandler,
                "endTickHandler"
        );
    }

    @Override
    public void accept(ForgeLifecycle lifecycle) {
        lifecycle.modEventBus().addListener(
                this::registerReloadListener
        );
        lifecycle.gameEventBus().addListener(this::onClientTick);
    }

    private void registerReloadListener(
            RegisterClientReloadListenersEvent event
    ) {
        event.registerReloadListener(
                (ResourceManagerReloadListener) resourceManager ->
                        resourceReloader.run()
        );
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            endTickHandler.run();
        }
    }
}
