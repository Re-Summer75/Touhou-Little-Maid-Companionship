package com.laixia.maidintelligence.feature.shading.forge;

import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ShadingForgeInstaller implements ForgeFeatureInstaller {
    private final Supplier<? extends Consumer<IEventBus>> clientInstaller;
    private boolean installed;

    public ShadingForgeInstaller(
            Supplier<? extends Consumer<IEventBus>> clientInstaller
    ) {
        this.clientInstaller = Objects.requireNonNull(
                clientInstaller,
                "clientInstaller"
        );
    }

    @Override
    public void install(ForgeLifecycle lifecycle) {
        if (installed) {
            return;
        }
        installed = true;
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> clientInstaller.get().accept(
                        lifecycle.modEventBus()
                )
        );
    }
}
