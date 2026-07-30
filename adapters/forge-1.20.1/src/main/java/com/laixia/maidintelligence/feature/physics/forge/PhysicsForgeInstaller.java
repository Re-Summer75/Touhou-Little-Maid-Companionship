package com.laixia.maidintelligence.feature.physics.forge;

import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.DistExecutor;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PhysicsForgeInstaller implements ForgeFeatureInstaller {
    private final Consumer<RegisterCommandsEvent> commandHandler;
    private final Supplier<? extends Consumer<ForgeLifecycle>> clientInstaller;
    private boolean installed;

    public PhysicsForgeInstaller(
            Consumer<RegisterCommandsEvent> commandHandler,
            Supplier<? extends Consumer<ForgeLifecycle>> clientInstaller
    ) {
        this.commandHandler = Objects.requireNonNull(
                commandHandler,
                "commandHandler"
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
        lifecycle.gameEventBus().addListener(commandHandler);
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> clientInstaller.get().accept(lifecycle)
        );
    }
}
