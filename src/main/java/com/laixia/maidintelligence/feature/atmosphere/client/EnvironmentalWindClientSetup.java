package com.laixia.maidintelligence.feature.atmosphere.client;

import com.laixia.maidintelligence.feature.atmosphere.client.wind.EnvironmentalWindSampler;
import com.laixia.maidintelligence.feature.physics.client.pose.WorldPoseDriverSources;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Installs environmental wind through the procedural pose-driver extension.
 */
@OnlyIn(Dist.CLIENT)
public final class EnvironmentalWindClientSetup {
    private static final String SOURCE_ID =
            "tlm_companionship:environmental_wind";

    private EnvironmentalWindClientSetup() {
    }

    public static void initialize() {
        WorldPoseDriverSources.register(
                SOURCE_ID,
                EnvironmentalWindSampler::new
        );
    }
}
