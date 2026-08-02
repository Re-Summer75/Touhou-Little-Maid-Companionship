package com.laixia.maidintelligence.feature.ai.forge;

import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Objects;
import java.util.function.Consumer;

public final class AiForgeInstaller implements ForgeFeatureInstaller {
    private final Consumer<RegisterCommandsEvent> commandHandler;
    private final Object maidTickHandler;
    private boolean installed;

    public AiForgeInstaller(
            Consumer<RegisterCommandsEvent> commandHandler,
            Object maidTickHandler
    ) {
        this.commandHandler = Objects.requireNonNull(
                commandHandler,
                "commandHandler"
        );
        this.maidTickHandler = Objects.requireNonNull(
                maidTickHandler,
                "maidTickHandler"
        );
    }

    @Override
    public void install(ForgeLifecycle lifecycle) {
        if (installed) {
            return;
        }
        installed = true;
        AiServerConfig.register(lifecycle.modEventBus());
        lifecycle.gameEventBus().addListener(commandHandler);
        lifecycle.gameEventBus().register(maidTickHandler);
    }
}
