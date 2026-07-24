package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;

/**
 * Test-only bridge that keeps benchmark fixtures out of the production API.
 */
public final class CollisionBenchmarkAccess {
    private CollisionBenchmarkAccess() {
    }

    public static PhysicsSolverLayout buildLayout(
            String modelId,
            AnimatedGeoModel model,
            String metadataJson
    ) {
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString(metadataJson).getAsJsonObject(),
                modelId + " benchmark fixture"
        );
        return PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(modelId, model, metadata)
        );
    }
}
