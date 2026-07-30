package com.laixia.maidintelligence.feature.physics.engine.collision.model;


import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.PreparedCollisionProxy;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immutable collision shape baked into one driven bone's layout.
 */
public interface CollisionProxy {
    CollisionProxyKind kind();

    default CollisionProxySource source() {
        return CollisionProxySource.AUTOMATIC;
    }

    int referenceNodeIndex();

    Vector3f copyReferenceOrigin(Vector3f output);

    float hitRadius();

    float leverArm();

    void copyStaticShape(
            Quaternionf referenceRestOrientation,
            PreparedCollisionProxy output,
            CollisionScratch scratch
    );

    boolean project(
            Vector3f direction,
            Quaternionf referenceOrientation,
            CollisionScratch scratch
    );

    float clearance(
            Vector3f direction,
            Quaternionf referenceOrientation,
            CollisionScratch scratch
    );
}
