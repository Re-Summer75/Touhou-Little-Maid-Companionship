package com.laixia.maidintelligence.feature.interaction.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * TLM-owned client state exposed as narrow Forge lifecycle callbacks.
 */
@OnlyIn(Dist.CLIENT)
public final class TlmInteractionClientRuntime {
    private TlmInteractionClientRuntime() {
    }

    public static void reloadResources() {
        DynamicMaidFaceTracker.clear();
        FaceTrackingGeometryCache.clear();
        MaidEatingParticleEffect.clearPendingEffects();
    }

    public static void endClientTick() {
        MaidEatingParticleEffect.tickPendingEffects();
    }
}
