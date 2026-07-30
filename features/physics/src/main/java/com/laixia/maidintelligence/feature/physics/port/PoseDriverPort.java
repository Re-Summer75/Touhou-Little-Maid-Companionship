package com.laixia.maidintelligence.feature.physics.port;

import org.joml.Vector3f;

/**
 * Supplies model-space external pose drivers, such as environmental wind,
 * without exposing an entity or world type to the physics feature.
 */
public interface PoseDriverPort {
    PoseDriverPort NONE = new PoseDriverPort() {
        @Override
        public void sampleInto(
                double animationTime,
                float dt,
                boolean paused,
                Vector3f output
        ) {
            output.zero();
        }

        @Override
        public void reset() {
        }
    };

    void sampleInto(
            double animationTime,
            float dt,
            boolean paused,
            Vector3f output
    );

    void reset();
}
