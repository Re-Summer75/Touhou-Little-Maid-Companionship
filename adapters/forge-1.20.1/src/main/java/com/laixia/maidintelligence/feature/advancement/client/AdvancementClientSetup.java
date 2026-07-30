package com.laixia.maidintelligence.feature.advancement.client;

import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Installs Forge client lifecycle hooks around the TLM-owned screen bridge.
 */
@OnlyIn(Dist.CLIENT)
public final class AdvancementClientSetup
        implements Consumer<ForgeLifecycle> {
    private final Object guiHandler;
    private final Runnable screenRegistrar;

    public AdvancementClientSetup(
            Object guiHandler,
            Runnable screenRegistrar
    ) {
        this.guiHandler = Objects.requireNonNull(
                guiHandler,
                "guiHandler"
        );
        this.screenRegistrar = Objects.requireNonNull(
                screenRegistrar,
                "screenRegistrar"
        );
    }

    @Override
    public void accept(ForgeLifecycle lifecycle) {
        lifecycle.gameEventBus().register(new ClientMaidAdvancements());
        lifecycle.gameEventBus().register(guiHandler);
        lifecycle.modEventBus().addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(screenRegistrar);
    }
}
