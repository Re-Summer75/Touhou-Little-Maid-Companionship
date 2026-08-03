package com.laixia.maidintelligence.feature.behavior.port;

/**
 * Adapter-side publisher for object-centric affordance advertisements.
 */
@FunctionalInterface
public interface AffordanceProvider<C> {
    void observe(C context, long gameTime, AffordanceIndexPort index);
}
