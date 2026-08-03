package com.laixia.maidintelligence.feature.behavior.forge;

import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;

import java.util.Objects;
import java.util.function.Consumer;

public final class BehaviorForgeInstaller implements ForgeFeatureInstaller {
    private final Consumer<TickEvent.PlayerTickEvent> playerTickHandler;
    private final Consumer<PlayerEvent.PlayerLoggedOutEvent> logoutHandler;
    private final Consumer<ChunkEvent.Load> chunkLoadHandler;
    private final Consumer<ChunkEvent.Unload> chunkUnloadHandler;
    private final Consumer<BlockEvent.EntityPlaceEvent> blockPlaceHandler;
    private final Consumer<BlockEvent.BreakEvent> blockBreakHandler;
    private final Consumer<EntityJoinLevelEvent> entityJoinHandler;
    private final Consumer<EntityLeaveLevelEvent> entityLeaveHandler;
    private final Consumer<LevelEvent.Unload> levelUnloadHandler;
    private boolean installed;

    public BehaviorForgeInstaller(
            Consumer<TickEvent.PlayerTickEvent> playerTickHandler,
            Consumer<PlayerEvent.PlayerLoggedOutEvent> logoutHandler,
            Consumer<ChunkEvent.Load> chunkLoadHandler,
            Consumer<ChunkEvent.Unload> chunkUnloadHandler,
            Consumer<BlockEvent.EntityPlaceEvent> blockPlaceHandler,
            Consumer<BlockEvent.BreakEvent> blockBreakHandler,
            Consumer<EntityJoinLevelEvent> entityJoinHandler,
            Consumer<EntityLeaveLevelEvent> entityLeaveHandler,
            Consumer<LevelEvent.Unload> levelUnloadHandler
    ) {
        this.playerTickHandler = Objects.requireNonNull(
                playerTickHandler,
                "playerTickHandler"
        );
        this.logoutHandler = Objects.requireNonNull(
                logoutHandler,
                "logoutHandler"
        );
        this.chunkLoadHandler = Objects.requireNonNull(
                chunkLoadHandler,
                "chunkLoadHandler"
        );
        this.chunkUnloadHandler = Objects.requireNonNull(
                chunkUnloadHandler,
                "chunkUnloadHandler"
        );
        this.blockPlaceHandler = Objects.requireNonNull(
                blockPlaceHandler,
                "blockPlaceHandler"
        );
        this.blockBreakHandler = Objects.requireNonNull(
                blockBreakHandler,
                "blockBreakHandler"
        );
        this.entityJoinHandler = Objects.requireNonNull(
                entityJoinHandler,
                "entityJoinHandler"
        );
        this.entityLeaveHandler = Objects.requireNonNull(
                entityLeaveHandler,
                "entityLeaveHandler"
        );
        this.levelUnloadHandler = Objects.requireNonNull(
                levelUnloadHandler,
                "levelUnloadHandler"
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
        lifecycle.gameEventBus().addListener(chunkLoadHandler);
        lifecycle.gameEventBus().addListener(chunkUnloadHandler);
        lifecycle.gameEventBus().addListener(blockPlaceHandler);
        lifecycle.gameEventBus().addListener(blockBreakHandler);
        lifecycle.gameEventBus().addListener(entityJoinHandler);
        lifecycle.gameEventBus().addListener(entityLeaveHandler);
        lifecycle.gameEventBus().addListener(levelUnloadHandler);
    }
}
