package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.PreparedCollisionProxy;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Reads baked box geometry back out through the runtime path the solver uses.
 */
final class MeshCollisionSupport {
    private MeshCollisionSupport() {
    }

    static Vector3f restHalfExtents(CollisionProxy proxy) {
        CollisionScratch scratch = new CollisionScratch();
        PreparedCollisionProxy prepared = new PreparedCollisionProxy();
        proxy.copyStaticShape(new Quaternionf(), prepared, scratch);
        Matrix4f identity = new Matrix4f();
        prepared.prepare(
                new Vector3f(),
                identity,
                identity.normal(new Matrix3f()),
                1.0F
        );
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        prepared.copyDebugData(new Vector3f(0.0F, 1.0F, 0.0F), data, scratch);
        return new Vector3f(data.boxHalfExtents);
    }
}
