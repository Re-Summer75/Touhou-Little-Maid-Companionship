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
     * Whether a direction is legal against every live collider, without
     * changing the direction or contact ownership.
     */
    boolean isClear(Vector3f direction, CollisionScratch scratch);

    /**
     * Tolerant target probe used only by severe interlock recovery.
     */
    default boolean isClear(
            Vector3f direction,
            float penetrationTolerance,
            CollisionScratch scratch
    ) {
        return isClear(direction, scratch);
    }

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
