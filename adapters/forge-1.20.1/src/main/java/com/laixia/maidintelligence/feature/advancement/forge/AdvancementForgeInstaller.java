package com.laixia.maidintelligence.feature.advancement.forge;

import com.laixia.maidintelligence.feature.advancement.criterion.MaidCriteriaTriggers;
import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AdvancementForgeInstaller
        implements ForgeFeatureInstaller {
    private final Consumer<IEventBus> menuRegistrar;
    private final Object lifecycleHandler;
    /** 桥接器是一组而不是一个：判定按「挂钩点的形状」分类，不是按主题分类。 */
    private final List<Object> bridgeHandlers;
    private final Supplier<? extends Consumer<ForgeLifecycle>> clientInstaller;
    private boolean installed;

    public AdvancementForgeInstaller(
            Consumer<IEventBus> menuRegistrar,
            Object lifecycleHandler,
            List<Object> bridgeHandlers,
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
        this.bridgeHandlers = List.copyOf(Objects.requireNonNull(
                bridgeHandlers,
                "bridgeHandlers"
        ));
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
        bridgeHandlers.forEach(lifecycle.gameEventBus()::register);
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
