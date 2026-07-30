package com.laixia.maidintelligence.feature.shading.client;

/**
 * Invalidates model templates owned by the active TLM renderer adapter.
 */
@FunctionalInterface
public interface ShadingCacheInvalidator {
    void clear();
}
