package com.laixia.maidintelligence.feature.orchestration.forge;

import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;

import java.util.Objects;
import java.util.function.Consumer;

public final class OrchestrationForgeInstaller
        implements ForgeFeatureInstaller {
    private final PreparableReloadListener reloadListener;
    private final Consumer<Entity> entityLeaveHandler;
    private boolean installed;

    public OrchestrationForgeInstaller(
            PreparableReloadListener reloadListener,
            Consumer<Entity> entityLeaveHandler
    ) {
        this.reloadListener = Objects.requireNonNull(
                reloadListener,
                "reloadListener"
        );
        this.entityLeaveHandler = Objects.requireNonNull(
                entityLeaveHandler,
                "entityLeaveHandler"
        );
    }

    @Override
    public void install(ForgeLifecycle lifecycle) {
        if (installed) {
            return;
        }
        installed = true;
        lifecycle.gameEventBus().addListener(this::onAddReloadListener);
        lifecycle.gameEventBus().addListener(this::onEntityLeave);
    }

    private void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(reloadListener);
    }

    private void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            entityLeaveHandler.accept(event.getEntity());
        }
    }
}
