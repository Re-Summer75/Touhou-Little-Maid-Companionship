package com.laixia.maidintelligence.feature.interaction.forge;

import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class InteractionForgeInstaller
        implements ForgeFeatureInstaller {
    private final List<Object> eventHandlers;
    private final Supplier<? extends Consumer<ForgeLifecycle>> clientInstaller;
    private boolean installed;

    public InteractionForgeInstaller(
            List<?> eventHandlers,
            Supplier<? extends Consumer<ForgeLifecycle>> clientInstaller
    ) {
        this.eventHandlers = List.copyOf(eventHandlers);
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
        InteractionConfig.register(lifecycle.modEventBus());
        eventHandlers.forEach(lifecycle.gameEventBus()::register);
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> clientInstaller.get().accept(lifecycle)
        );
    }
}
