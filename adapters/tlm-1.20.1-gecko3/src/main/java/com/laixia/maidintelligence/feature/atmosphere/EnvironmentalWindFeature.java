package com.laixia.maidintelligence.feature.atmosphere;

import com.laixia.maidintelligence.core.feature.FeatureContext;
import com.laixia.maidintelligence.core.feature.FeatureModule;
import com.laixia.maidintelligence.feature.atmosphere.client.EnvironmentalWindClientSetup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Optional atmosphere enhancement that contributes a procedural wind pose to
 * secondary motion without entering its physical force integration.
 */
public final class EnvironmentalWindFeature implements FeatureModule {
    public static final EnvironmentalWindFeature INSTANCE =
            new EnvironmentalWindFeature();

    private boolean initialized;

    private EnvironmentalWindFeature() {
    }

    @Override
    public void initialize(FeatureContext context) {
        if (initialized) {
            return;
        }
        initialized = true;
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> EnvironmentalWindClientSetup::initialize
        );
    }
}
