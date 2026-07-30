package com.laixia.maidintelligence.feature.advancement.forge;

import com.laixia.maidintelligence.feature.advancement.criterion.MaidCriteriaTriggers;
import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AdvancementForgeInstaller
        implements ForgeFeatureInstaller {
    private final Consumer<IEventBus> menuRegistrar;
    private final Object lifecycleHandler;
    private final Object bridgeHandler;
    private final Supplier<? extends Consumer<ForgeLifecycle>> clientInstaller;
    private boolean installed;

    public AdvancementForgeInstaller(
            Consumer<IEventBus> menuRegistrar,
            Object lifecycleHandler,
            Object bridgeHandler,
            Supplier<? extends Consumer<ForgeLifecycle>> clientInstaller
    ) {
        this.menuRegistrar = Objects.requireNonNull(
                menuRegistrar,
                "menuRegistrar"
        );
        this.lifecycleHandler = Objects.requireNonNull(
                lifecycleHandler,
                "lifecycleHandler"
        );
        this.bridgeHandler = Objects.requireNonNull(
                bridgeHandler,
                "bridgeHandler"
        );
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
        menuRegistrar.accept(lifecycle.modEventBus());
        lifecycle.modEventBus().addListener(this::onCommonSetup);
        lifecycle.gameEventBus().register(lifecycleHandler);
        lifecycle.gameEventBus().register(bridgeHandler);
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> clientInstaller.get().accept(lifecycle)
        );
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Vanilla's criterion registry is not thread-safe.
        event.enqueueWork(MaidCriteriaTriggers::register);
    }
}
