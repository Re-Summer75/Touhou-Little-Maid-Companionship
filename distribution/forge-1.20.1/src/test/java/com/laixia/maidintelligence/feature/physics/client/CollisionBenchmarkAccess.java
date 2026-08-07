package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;

/**
 * Test-only bridge that keeps benchmark fixtures out of the production API.
 */
public final class CollisionBenchmarkAccess {
    private CollisionBenchmarkAccess() {
    }

    public static PhysicsSolverLayout buildLayout(
            String modelId,
            BoneModelSnapshot model,
            String metadataJson
    ) {
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString(metadataJson).getAsJsonObject(),
                modelId + " benchmark fixture"
        );
        return PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(modelId, model, metadata)
        );
    }
}
