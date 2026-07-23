package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.discovery.DiscoveryPlanner;

/**
 * Stable package-local entry point for building a model's physics plan.
 *
 * <p>The discovery implementation lives in the {@code discovery} subpackage;
 * this facade keeps existing cache and verification callers independent from
 * its internal module layout.
 */
final class PhysicsBoneDiscoverer {
    private PhysicsBoneDiscoverer() {
    }

    static PhysicsBoneSelectionPlan discover(
            String modelId,
            AnimatedGeoModel model,
            PhysicsMetadata metadata
    ) {
        return DiscoveryPlanner.discover(modelId, model, metadata);
    }
}
