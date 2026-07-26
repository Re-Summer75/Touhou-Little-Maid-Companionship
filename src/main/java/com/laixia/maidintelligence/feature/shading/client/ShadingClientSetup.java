package com.laixia.maidintelligence.feature.shading.client;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** 维护外法线模板的资源生命周期。 */
@OnlyIn(Dist.CLIENT)
public final class ShadingClientSetup {
    private ShadingClientSetup() {
    }

    public static void initialize(IEventBus modEventBus) {
        modEventBus.addListener(ShadingClientSetup::registerReloadListener);
    }

    private static void registerReloadListener(
            RegisterClientReloadListenersEvent event
    ) {
        event.registerReloadListener(
                (ResourceManagerReloadListener) resourceManager ->
                        GeoMeshNormalCache.clear()
        );
    }

    /**
     * TLM 下载模型包的目录/ZIP 加载不触发 Minecraft 资源重载，需显式清理模板。
     */
    public static void refreshAfterCustomPackLoad() {
        Minecraft.getInstance().execute(GeoMeshNormalCache::clear);
    }
}
