package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Vector3f;

/**
 * Narrow collision contract consumed by spring integration.
 */
public interface CollisionProjection {
    void beginProjectionSeries();

    boolean project(
            Vector3f direction,
            CollisionScratch scratch,
            int maximumPasses
    );

    /**
     * Lets collision runtime retain a responder after a recurring-contact
     * signal. The runtime alone owns suppression and contact hysteresis.
     */
    boolean resolveRecurringContact(
            boolean recurringContact,
            Vector3f projectedDirection,
            CollisionScratch scratch
    );
}
