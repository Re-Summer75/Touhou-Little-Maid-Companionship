package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Caches discovery by live animated-model identity. A model switch produces a
 * new {@link AnimatedGeoModel}, so no decision or bone reference can leak from
 * the old skeleton.
 */
final class PhysicsBonePlanCache {
    private static final Map<AnimatedGeoModel, Entry> PLANS = new WeakHashMap<>();

    private PhysicsBonePlanCache() {
    }

    static synchronized PhysicsBoneSelectionPlan getOrCompute(
            String modelId,
            AnimatedGeoModel model
    ) {
        Entry cached = PLANS.get(model);
        if (cached != null && cached.modelId().equals(modelId)) {
            return cached.plan();
        }
        PhysicsMetadata metadata = PhysicsMetadataLoader.find(modelId);
        PhysicsBoneSelectionPlan plan =
                PhysicsBoneDiscoverer.discover(modelId, model, metadata);
        PLANS.put(model, new Entry(modelId, plan));
        return plan;
    }

    static synchronized void clear() {
        PLANS.clear();
    }

    private record Entry(String modelId, PhysicsBoneSelectionPlan plan) {
    }
}
