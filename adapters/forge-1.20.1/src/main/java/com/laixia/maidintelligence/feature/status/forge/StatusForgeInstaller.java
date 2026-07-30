package com.laixia.maidintelligence.feature.status.forge;

import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.Objects;
import java.util.function.Supplier;

public final class StatusForgeInstaller implements ForgeFeatureInstaller {
    private final Object foodHandler;
    private final Object regenerationHandler;
    private final Supplier<?> clientGuiHandler;
    private boolean installed;

    public StatusForgeInstaller(
            Object foodHandler,
            Object regenerationHandler,
            Supplier<?> clientGuiHandler
    ) {
        this.foodHandler = Objects.requireNonNull(
                foodHandler,
                "foodHandler"
        );
        this.regenerationHandler = Objects.requireNonNull(
                regenerationHandler,
                "regenerationHandler"
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
        lifecycle.gameEventBus().register(foodHandler);
        lifecycle.gameEventBus().register(regenerationHandler);
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> lifecycle.gameEventBus().register(
                        clientGuiHandler.get()
                )
        );
    }
}
