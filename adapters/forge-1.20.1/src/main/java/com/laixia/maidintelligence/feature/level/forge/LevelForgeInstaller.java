package com.laixia.maidintelligence.feature.level.forge;

import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.DistExecutor;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LevelForgeInstaller implements ForgeFeatureInstaller {
    private final Object experienceHandler;
    private final Consumer<RegisterCommandsEvent> commandHandler;
    private final Supplier<?> clientGuiHandler;
    private boolean installed;

    public LevelForgeInstaller(
            Object experienceHandler,
            Consumer<RegisterCommandsEvent> commandHandler,
            Supplier<?> clientGuiHandler
    ) {
        this.experienceHandler = Objects.requireNonNull(
                experienceHandler,
                "experienceHandler"
        );
        this.commandHandler = Objects.requireNonNull(
                commandHandler,
                "commandHandler"
        );
        this.clientGuiHandler = Objects.requireNonNull(
                clientGuiHandler,
                "clientGuiHandler"
        );
    }

    @Override
    public void install(ForgeLifecycle lifecycle) {
        if (installed) {
            return;
        }
        installed = true;
        lifecycle.gameEventBus().register(experienceHandler);
        lifecycle.gameEventBus().addListener(commandHandler);
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> lifecycle.gameEventBus().register(
                        clientGuiHandler.get()
                )
        );
    }
}
