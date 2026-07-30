package com.laixia.maidintelligence.feature.shading.client;

/**
 * Invalidates Gecko mesh templates owned by this TLM adapter.
 */
public final class TlmShadingCacheInvalidator
        implements ShadingCacheInvalidator {
    @Override
    public void clear() {
        GeckoMeshNormalTemplates.clear();
    }
}
