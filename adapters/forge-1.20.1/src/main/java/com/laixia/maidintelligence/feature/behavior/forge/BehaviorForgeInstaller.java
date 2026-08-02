package com.laixia.maidintelligence.feature.behavior.forge;

import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Objects;
import java.util.function.Consumer;

public final class BehaviorForgeInstaller implements ForgeFeatureInstaller {
    private final Consumer<TickEvent.PlayerTickEvent> playerTickHandler;
    private final Consumer<PlayerEvent.PlayerLoggedOutEvent> logoutHandler;
    private boolean installed;

    public BehaviorForgeInstaller(
            Consumer<TickEvent.PlayerTickEvent> playerTickHandler,
            Consumer<PlayerEvent.PlayerLoggedOutEvent> logoutHandler
    ) {
        this.playerTickHandler = Objects.requireNonNull(
                playerTickHandler,
                "playerTickHandler"
        );
        this.logoutHandler = Objects.requireNonNull(
                logoutHandler,
                "logoutHandler"
        );
    }

    @Override
    public void install(ForgeLifecycle lifecycle) {
        if (installed) {
            return;
        }
        installed = true;
        BehaviorServerConfig.register(lifecycle.modEventBus());
        lifecycle.gameEventBus().addListener(playerTickHandler);
        lifecycle.gameEventBus().addListener(logoutHandler);
    }
}
