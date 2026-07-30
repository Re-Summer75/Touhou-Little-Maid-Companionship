package com.laixia.maidintelligence.feature.shading.client;

import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Maintains renderer template state across Forge resource lifecycles.
 */
@OnlyIn(Dist.CLIENT)
public final class ShadingClientSetup {
    private ShadingClientSetup() {
    }

    public static void initialize(IEventBus modEventBus) {
        modEventBus.addListener(
                ShadingClientSetup::registerReloadListener
        );
    }

    private static void registerReloadListener(
            RegisterClientReloadListenersEvent event
    ) {
        event.registerReloadListener(
                (ResourceManagerReloadListener) resourceManager ->
                        invalidator().clear()
        );
    }

    /**
     * TLM custom model packs do not trigger a Minecraft resource reload.
     */
    public static void refreshAfterCustomPackLoad() {
        Minecraft.getInstance().execute(() -> invalidator().clear());
    }

    private static ShadingCacheInvalidator invalidator() {
        return AdapterRuntime.require(ShadingCacheInvalidator.class);
    }
}
