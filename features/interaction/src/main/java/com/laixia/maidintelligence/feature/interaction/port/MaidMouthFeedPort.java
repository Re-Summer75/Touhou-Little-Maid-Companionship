package com.laixia.maidintelligence.feature.interaction.port;

import com.laixia.maidintelligence.shared.geometry.Vec3d;

/**
 * Server-packet entrypoint for validated mouth feeding.
 */
@FunctionalInterface
public interface MaidMouthFeedPort<P> {
    void handle(
            P player,
            int maidEntityId,
            float faceU,
            float faceV,
            Vec3d worldCenter,
            Vec3d worldNormal
    );
}
