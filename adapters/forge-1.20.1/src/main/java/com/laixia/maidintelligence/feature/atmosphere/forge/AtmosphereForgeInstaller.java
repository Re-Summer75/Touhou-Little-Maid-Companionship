package com.laixia.maidintelligence.feature.atmosphere.forge;

import com.laixia.maidintelligence.feature.atmosphere.client.EnvironmentalWindClientSetup;
import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class AtmosphereForgeInstaller
        implements ForgeFeatureInstaller {
    private boolean installed;

    @Override
    public void install(ForgeLifecycle lifecycle) {
        if (installed) {
            return;
        }
        installed = true;
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> EnvironmentalWindClientSetup::initialize
        );
    }
}
